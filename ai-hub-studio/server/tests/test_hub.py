import asyncio
import json
import shutil
import tempfile
import threading
import unittest
from pathlib import Path
import httpx

from server.app import create_app
from server.providers import validate_url
from server.storage import Store
from server.jobs import Jobs
from server.comfy import bind_workflow
from server import media

class TestClient:
    """HTTPX ASGI transport; no sockets or background portal required."""
    def __init__(self, app, headers):
        self.app, self.headers = app, headers
    def request(self, method, path, **kwargs):
        async def send():
            # Keep a short timer so cross-thread completions also work in restricted runtimes.
            loop = asyncio.get_running_loop()
            def tick(): loop.call_later(.01, tick)
            tick()
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=self.app),base_url='http://test',headers=self.headers) as client:
                return await client.request(method,path,**kwargs)
        return asyncio.run(send())
    def get(self, path, **kwargs): return self.request('GET',path,**kwargs)
    def post(self, path, **kwargs): return self.request('POST',path,**kwargs)
    def delete(self, path, **kwargs): return self.request('DELETE',path,**kwargs)


TOKEN = 'test-pairing-token-with-at-least-32-characters'


class HubTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.calls = []
        self.fail_first = False
        def upstream(request):
            self.calls.append(request)
            path = request.url.path
            if path == '/v1beta/models':
                return httpx.Response(200,json={'models':[{'name':'models/gemini-test','supportedGenerationMethods':['generateContent']}]})
            if path.endswith(':generateContent'):
                return httpx.Response(200,json={'candidates':[{'content':{'parts':[{'text':'Olá Gemini'}]}}]})
            if path == '/object_info':
                return httpx.Response(200,json={'Text':{},'SaveImage':{}})
            if path == '/prompt':
                return httpx.Response(200,json={'prompt_id':'synthetic-test-prompt'})
            if path == '/history/synthetic-test-prompt':
                return httpx.Response(200,json={'synthetic-test-prompt':{'status':{'status_str':'success'},'outputs':{'2':{'images':[{'filename':'fixture.png','subfolder':'','type':'output'}]}}}})
            if path == '/view':
                return httpx.Response(200,content=b'synthetic-output-for-contract-test')
            if path.endswith('/models'):
                return httpx.Response(200,json={'data':[
                    {'id':'free','pricing':{'prompt':'0','completion':'0'}},
                    {'id':'paid','pricing':{'prompt':'0.1','completion':'0.2'}},
                    {'id':'image-cost','pricing':{'prompt':'0','completion':'0','image':'1'}}]})
            if path == '/api/tags':
                return httpx.Response(200,json={'models':[{'name':'qwen3:1.7b'}]})
            if self.fail_first and request.url.host == '127.0.0.1':
                return httpx.Response(429,headers={'retry-after':'60','x-ratelimit-remaining-requests':'0'})
            if path == '/api/chat':
                return httpx.Response(200,json={'message':{'content':'Olá do Ollama'}})
            if path.endswith('/chat/completions'):
                return httpx.Response(200,json={'choices':[{'message':{'content':'Olá do modelo remoto'}}]})
            return httpx.Response(404)
        self.app = create_app(self.tmp.name,TOKEN,httpx.MockTransport(upstream))
        self.client = TestClient(self.app,headers={'Authorization':'Bearer '+TOKEN})
        self.store = self.app.state.store

    def tearDown(self):
        self.app.state.jobs.pool.shutdown(wait=True)
        self.store.db.close()
        self.tmp.cleanup()

    def provider(self, **values):
        body = dict(name='Ollama',type='ollama',url='http://127.0.0.1:11434',local=True)
        body.update(values)
        r = self.client.post('/v1/providers',json=body)
        self.assertEqual(r.status_code,200,r.text)
        return r.json()['id']

    def test_auth_and_rate_limit(self):
        self.assertEqual(self.client.get('/health').status_code,200)
        self.assertEqual(self.client.get('/v1/files',headers={'Authorization':'wrong'}).status_code,401)
        for _ in range(29):
            self.client.get('/v1/files',headers={'Authorization':'wrong'})
        self.assertEqual(self.client.get('/v1/files',headers={'Authorization':'wrong'}).status_code,429)

    def test_keys_encrypted_never_returned(self):
        id = self.provider(key='secret-value-not-to-leak')
        self.assertEqual(self.store.secret(id),'secret-value-not-to-leak')
        self.assertNotIn('secret-value-not-to-leak',self.client.get('/v1/providers').text)
        self.assertNotIn(b'secret-value-not-to-leak',(Path(self.tmp.name)/'hub.db').read_bytes())
        self.client.delete('/v1/providers/'+id)
        self.assertEqual(self.store.secret(id),'')

    def test_local_discovery_chat_search_delete(self):
        p = self.provider()
        r = self.client.post(f'/v1/providers/{p}/discover')
        self.assertEqual(r.status_code,200,r.text)
        self.assertEqual(r.json()['models'][0]['id'],'qwen3:1.7b')
        body = {'messages':[{'role':'user','content':'Olá Portugal'}], 'candidates':[{'provider':p,'model':'qwen3:1.7b'}]}
        r = self.client.post('/v1/chat',json=body)
        self.assertEqual(r.status_code,200,r.text)
        self.assertEqual(r.json()['text'],'Olá do Ollama')
        rows = self.client.get('/v1/conversations?q=Portugal').json()
        self.assertEqual(len(rows),1)
        self.client.delete('/v1/conversations/'+rows[0]['id'])
        self.assertEqual(self.client.get('/v1/conversations').json(),[])

    def test_remote_consent_and_cost_guard(self):
        p = self.provider(name='OpenRouter',type='openai',url='https://openrouter.ai/api/v1',local=False,key='key')
        self.client.post(f'/v1/providers/{p}/discover')
        b = {'messages':[{'role':'user','content':'Olá'}], 'candidates':[{'provider':p,'model':'free'}]}
        self.assertEqual(self.client.post('/v1/chat',json=b).status_code,502)
        b['remote_consent']=True
        self.assertEqual(self.client.post('/v1/chat',json=b).status_code,200)
        for model in ('paid','image-cost','free:unverified'):
            b['candidates'][0]['model']=model
            self.assertEqual(self.client.post('/v1/chat',json=b).status_code,502)
        b['allow_metered']=True
        self.assertEqual(self.client.post('/v1/chat',json=b).status_code,200)

    def test_failover_and_quota(self):
        a=self.provider(); b=self.provider(name='OpenRouter',type='openai',url='https://openrouter.ai/api/v1',local=False)
        self.client.post(f'/v1/providers/{b}/discover'); self.fail_first=True
        r=self.client.post('/v1/chat',json={'messages':[{'role':'user','content':'Olá'}], 'remote_consent':True,
            'candidates':[{'provider':a,'model':'qwen3:1.7b'},{'provider':b,'model':'free'}]})
        self.assertEqual(r.status_code,200,r.text)
        self.assertEqual(r.json()['provider'],'OpenRouter')
        self.assertTrue(r.json()['failover'])
        quotas=self.client.get('/v1/quotas').json()
        self.assertEqual(quotas[0]['headers']['x-ratelimit-remaining-requests'],'0')

    def test_project_context_and_missing_project(self):
        p=self.provider()
        project=self.client.post('/v1/projects',json={'name':'Cinema','context':'A personagem é a Maria'}).json()['id']
        b={'messages':[{'role':'user','content':'Olá'}],'candidates':[{'provider':p,'model':'qwen'}], 'project':project}
        self.assertEqual(self.client.post('/v1/chat',json=b).status_code,200)
        sent=json.loads(self.calls[-1].content)
        self.assertEqual(sent['messages'][0]['content'],'A personagem é a Maria')
        b['project']='missing'
        self.assertEqual(self.client.post('/v1/chat',json=b).status_code,404)

    def test_upload_extract_download_and_delete(self):
        r=self.client.post('/v1/files',files={'file':('../../private.txt',b'Conteudo local','text/plain')})
        self.assertEqual(r.status_code,200,r.text)
        id=r.json()['id']
        self.assertEqual(self.client.get(f'/v1/files/{id}/text').json()['text'],'Conteudo local')
        self.assertEqual(self.client.get(f'/v1/files/{id}/download').content,b'Conteudo local')
        self.assertEqual(self.client.get('/v1/files/not-a-file/download').status_code,404)
        self.client.delete('/v1/files/'+id)
        self.assertEqual(self.client.get(f'/v1/files/{id}/download').status_code,404)
        self.assertEqual(self.client.post('/v1/files',files={'file':('a.exe',b'a')}).status_code,400)

    def test_workflow_binding_and_validation(self):
        graph={'1':{'class_type':'Text','inputs':{'text':'original'}}}
        result=bind_workflow(graph,{'prompt':'1.text'},{'prompt':'novo'})
        self.assertEqual(result['1']['inputs']['text'],'novo')
        self.assertEqual(graph['1']['inputs']['text'],'original')
        with self.assertRaises(ValueError):
            bind_workflow(graph,{'prompt':'404.text'},{'prompt':'x'})
        r=self.client.post('/v1/workflows',json={'name':'Teste','modality':'video','workflow':graph,'bindings':{'prompt':'1.text'}})
        self.assertEqual(r.status_code,200,r.text)

    def test_srt_roundtrip_shift_validation(self):
        text='1\n00:00:01,000 --> 00:00:02,500\nOlá\nmundo\n'
        r=self.client.post('/v1/srt',json={'text':text,'offset_ms':1000})
        self.assertEqual(r.status_code,200,r.text)
        self.assertEqual(media.parse_srt(r.json()['text'])[0]['start_ms'],2000)
        self.assertEqual(self.client.post('/v1/srt',json={'text':text,'offset_ms':-3000}).status_code,400)
        self.assertEqual(self.client.post('/v1/srt',json={'text':'invalido'}).status_code,400)

    def test_jobs_and_restart(self):
        jobs=self.app.state.jobs
        j=jobs.submit('unit',lambda progress:{'ok':True})
        jobs.futures[j['id']].result(timeout=5)
        self.assertEqual(self.store.get(j['id'])['status'],'done')
        j=jobs.submit('failed',lambda progress:(_ for _ in ()).throw(ValueError('erro esperado')))
        jobs.futures[j['id']].result(timeout=5)
        self.assertEqual(self.store.get(j['id'])['status'],'failed')
        self.store.put('job',{'status':'running'},id='interrupted')
        restarted=Jobs(self.store)
        self.assertEqual(self.store.get('interrupted')['status'],'interrupted')
        restarted.pool.shutdown()

    def test_automatic_excludes_untested_or_failed_models(self):
        p=self.provider()
        self.client.post(f'/v1/providers/{p}/discover')
        b={'messages':[{'role':'user','content':'Olá'}]}
        self.assertEqual(self.client.post('/v1/chat',json=b).status_code,502)
        r=self.client.post(f'/v1/providers/{p}/test',json={})
        self.assertEqual(r.status_code,200,r.text)
        self.assertTrue(r.json()['ok'])
        self.assertEqual(self.client.post('/v1/chat',json=b).status_code,200)
        self.assertTrue(self.client.get('/v1/models').json()[0]['auto_eligible'])
        self.fail_first=True
        self.assertEqual(self.client.post('/v1/chat',json=b).status_code,502)
        self.assertFalse(self.client.get('/v1/models').json()[0]['auto_eligible'])

    def test_diagnostics_auth_and_installed_services(self):
        self.provider()
        r=self.client.get('/v1/diagnostics')
        self.assertEqual(r.status_code,200,r.text)
        self.assertTrue(r.json()['authenticated'])
        self.assertTrue(r.json()['providers'][0]['reachable'])
        self.assertEqual(r.json()['providers'][0]['models'],1)

    def test_tls_pairing_identity_is_stable_and_private(self):
        from server.network import prepare
        from cryptography import x509
        from cryptography.hazmat.primitives import hashes
        first,cert,key=prepare(Path(self.tmp.name)/'tls','192.168.1.123')
        second,_,_=prepare(Path(self.tmp.name)/'tls','192.168.1.123')
        self.assertEqual(first,second)
        self.assertGreaterEqual(len(first['token']),32)
        c=x509.load_pem_x509_certificate(cert.read_bytes())
        self.assertEqual(c.fingerprint(hashes.SHA256()).hex(),first['fingerprint'])
        self.assertIn('192.168.1.123',str(c.extensions.get_extension_for_class(x509.SubjectAlternativeName).value))

    def test_gemini_discovery_and_native_request(self):
        p=self.provider(name='Gemini',type='gemini',url='https://generativelanguage.googleapis.com/v1beta',local=False,key='gemini-private')
        r=self.client.post(f'/v1/providers/{p}/discover')
        self.assertEqual(r.status_code,200,r.text)
        self.assertEqual(r.json()['models'][0]['id'],'gemini-test')
        r=self.client.post('/v1/chat',json={'messages':[{'role':'system','content':'Responda em português'},{'role':'user','content':'Olá'}],
             'candidates':[{'provider':p,'model':'gemini-test'}],'remote_consent':True,'allow_metered':True})
        self.assertEqual(r.status_code,200,r.text)
        self.assertEqual(r.json()['text'],'Olá Gemini')
        req=self.calls[-1]
        self.assertEqual(req.headers['x-goog-api-key'],'gemini-private')
        self.assertNotIn('gemini-private',str(req.url))
        self.assertEqual(json.loads(req.content)['systemInstruction']['parts'][0]['text'],'Responda em português')

    def test_comfy_generation_queue_and_output_download(self):
        p=self.provider(name='ComfyUI',type='comfy',url='http://127.0.0.1:8188')
        w=self.client.post('/v1/workflows',json={'name':'Contrato de teste, não modelo real','modality':'image',
            'workflow':{'1':{'class_type':'Text','inputs':{'text':'original'}},'2':{'class_type':'SaveImage','inputs':{}}},
            'bindings':{'prompt':'1.text'}}).json()['id']
        r=self.client.post('/v1/generate',json={'provider':p,'template':w,'values':{'prompt':'Portugal'}})
        self.assertEqual(r.status_code,200,r.text)
        id=r.json()['id'];self.app.state.jobs.futures[id].result(timeout=5)
        job=self.store.get(id)
        self.assertEqual(job['status'],'done',job)
        file=job['result']['files'][0]
        self.assertEqual(self.client.get('/v1/files/'+file['id']+'/download').content,b'synthetic-output-for-contract-test')
        submitted=next(json.loads(c.content) for c in self.calls if c.url.path=='/prompt')
        self.assertEqual(submitted['prompt']['1']['inputs']['text'],'Portugal')

    def test_cancel_queued_job_and_bound_queue(self):
        release=threading.Event()
        jobs=self.app.state.jobs
        running=jobs.submit('blocked',lambda progress:release.wait(5))
        try:
            pending=jobs.submit('cancel',lambda progress:{})
            self.assertEqual(jobs.cancel(pending['id'])['status'],'cancelled')
            for _ in range(19):
                jobs.submit('queued',lambda progress:{})
            with self.assertRaises(ValueError):
                jobs.submit('overflow',lambda progress:{})
        finally:
            release.set()
        jobs.futures[running['id']].result(timeout=5)

    def test_url_policy(self):
        for url,local in [('http://example.com',False),('http://169.254.169.254',True),('http://0.0.0.0:8787',True),('https://user:pass@example.com',False),('http://127.0.0.1:22',True)]:
            with self.assertRaises(ValueError,msg=url):
                validate_url(url,local)
        self.assertEqual(validate_url('http://192.168.1.20:8188/',True),'http://192.168.1.20:8188')

    def test_plan_explicitly_identified(self):
        r=self.client.post('/v1/agent/plan',json={'prompt':'Vídeo musical','duration':12})
        self.assertEqual(r.status_code,200,r.text)
        self.assertEqual([x['duration'] for x in r.json()['shots']],[5,5,2])
        self.assertIn('determinístico',r.json()['method'])

    @unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'),'FFmpeg não instalado')
    def test_real_audio_and_mp4_render(self):
        audio=self.store.files/'tone.wav'
        media.run(['ffmpeg','-v','error','-y','-f','lavfi','-i','sine=frequency=440:duration=1',str(audio)])
        a=self.store.add_file(audio)
        info=media.analyse(audio)
        self.assertAlmostEqual(info['duration'],1,places=1)
        self.assertTrue(info['rms'])
        video=self.store.files/'source.mp4'
        media.run(['ffmpeg','-v','error','-y','-f','lavfi','-i','color=c=blue:s=160x90:d=1','-c:v','libx264','-pix_fmt','yuv420p',str(video)])
        v=self.store.add_file(video)
        srt=self.client.post('/v1/srt',json={'text':'1\n00:00:00,000 --> 00:00:00,900\nPortugal\n'}).json()['file']
        r=self.client.post('/v1/render',json={'clips':[{'file':v['id'],'start':0,'duration':1,'effect':'fade'}], 'audio':a['id'],'srt':srt['id'],'aspect':'9:16'})
        self.assertEqual(r.status_code,200,r.text)
        id=r.json()['id']; self.app.state.jobs.futures[id].result(timeout=40)
        job=self.store.get(id)
        self.assertEqual(job['status'],'done',job)
        exported=self.store.path(job['result']['file']['id'])
        info=media.probe(exported)
        stream=next(s for s in info['streams'] if s['codec_type']=='video')
        self.assertEqual((stream['width'],stream['height']),(720,1280))
        self.assertTrue(any(s['codec_type']=='audio' for s in info['streams']))
        self.assertAlmostEqual(float(info['format']['duration']),1,delta=.2)


if __name__=='__main__':
    unittest.main(verbosity=2)
