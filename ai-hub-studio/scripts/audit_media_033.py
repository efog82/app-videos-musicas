"""Bounded real public API audit. No private endpoints, payment or quota retries."""
import base64, hashlib, json, os, shutil, subprocess, time
from pathlib import Path
import httpx
from gradio_client import Client
from PIL import Image
OUT=Path('media-audit-033'); OUT.mkdir(exist_ok=True)
REPOS=['mrfakename/Z-Image-Turbo','Qwen/Qwen-Image','multimodalart/Qwen-Image-Lightning','multimodalart/Hunyuan-Video-1-5','Lightricks/LTX-2-3','zai-org/CogVideoX-5B-Space','multimodalart/wan2-2-fast']
PROMPTS=[('apple','A red apple on a wooden table, realistic photograph, natural window light'),('galaxy','A realistic spiral galaxy, astrophotography, detailed spiral arms, dark star field'),('landscape','Cinematic natural mountain landscape, forest and clear lake, golden hour, realistic photography')]
report={'type':'REAL_NETWORK','startedUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'discovery':[],'generations':[],'cloudflare':[]}
def save(): (OUT/'report.json').write_text(json.dumps(report,indent=2,ensure_ascii=False))
def validate(path):
 if path.suffix.lower() in ['.mp4','.webm']:
  probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_format','-show_streams','-of','json',str(path)])); assert float(probe['format']['duration'])>0
  return {'format':'video','probe':probe}
 with Image.open(path) as im: im.load(); return {'format':im.format,'width':im.width,'height':im.height}
def files(v):
 if isinstance(v,str) and Path(v).is_file(): return [Path(v)]
 if isinstance(v,(list,tuple)): return [p for x in v for p in files(x)]
 if isinstance(v,dict): return [p for x in v.values() for p in files(x)]
 return []
clients={}
for repo in REPOS:
 r={'repo':repo}
 try:
  h=httpx.get('https://huggingface.co/api/spaces/'+repo,timeout=25,follow_redirects=True);h.raise_for_status();meta=h.json();r.update(stage=meta.get('runtime',{}).get('stage'),host=meta.get('host'))
  assert not meta.get('private'),'Private Space not accessed'
  assert r['stage']=='RUNNING','Runtime not running: '+str(r['stage'])
  c=Client(repo,verbose=False,httpx_kwargs={'timeout':45});api=c.view_api(return_format='dict');clients[repo]=(c,api)
  stem=repo.replace('/','_');(OUT/(stem+'-api.json')).write_text(json.dumps(api,indent=2,default=str));(OUT/(stem+'-config.json')).write_text(json.dumps(c.config,indent=2,default=str))
  source=httpx.get('https://huggingface.co/spaces/'+repo+'/resolve/main/app.py',timeout=25,follow_redirects=True)
  if source.status_code==200:(OUT/(stem+'-app.py.txt')).write_text(source.text)
  r.update(ok=True,endpoints=list(api.get('named_endpoints',{})))
 except Exception as e:r.update(ok=False,error=str(e)[:700])
 report['discovery'].append(r);save();print(json.dumps(r),flush=True)
# A single inference attempt per new image model; stop on a shared ZeroGPU quota failure.
for repo in (['mrfakename/Z-Image-Turbo','multimodalart/Qwen-Image-Lightning','Qwen/Qwen-Image'] if os.getenv('AIHUB_AUDIT_GENERATE')=='true' else []):
 if repo not in clients:continue
 r={'repo':repo,'type':'REAL_GENERATION'}
 try:
  c,api=clients[repo];named=api['named_endpoints'];name=next(k for k in ['/generate_image','/infer','/generate'] if k in named)
  values={'prompt':PROMPTS[0][1],'height':512,'width':512,'seed':33033,'randomize_seed':False,'num_inference_steps':9 if 'Z-Image' in repo else (4 if 'Lightning' in repo else 20),'true_guidance_scale':4.0,'guidance_scale':1.0 if 'Lightning' in repo else 4.0,'enhance_prompt':False,'prompt_enhance':False}
  args={}
  for p in named[name]['parameters']:
   key=p['parameter_name']
   if key in values:args[key]=values[key]
   elif p.get('parameter_has_default'):args[key]=p.get('parameter_default')
   else:raise ValueError('Unsupported required argument '+key)
  r.update(endpoint=name,args=args);job=c.submit(api_name=name,**args);deadline=time.time()+300
  while not job.done():
   if time.time()>deadline:job.cancel();raise TimeoutError('5-minute queue timeout; cancellation requested')
   time.sleep(2)
  result=job.result();r['outputs']=[]
  for i,path in enumerate(files(result)):
   dest=OUT/(repo.replace('/','_')+f'-{i}'+path.suffix);shutil.copyfile(path,dest);r['outputs'].append({'file':dest.name,'bytes':dest.stat().st_size,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),**validate(dest)})
  assert r['outputs'],'No valid downloaded output';r['ok']=True
 except Exception as e:r.update(ok=False,error=str(e)[:1000])
 report['generations'].append(r);save();print(json.dumps(r),flush=True)
 if 'quota' in r.get('error','').lower():break
# Cloudflare credentials are optional CI secrets, never displayed or stored in evidence.
account=os.getenv('AIHUB_CF_ACCOUNT','');token=os.getenv('AIHUB_CF_TOKEN','')
if os.getenv('AIHUB_AUDIT_GENERATE')!='true' or not account or not token or os.getenv('AIHUB_CF_FREE')!='true':report['cloudflare']=[{'status':'NOT_TESTED','reason':'Workers Free credentials/confirmation not configured in CI; no claim about visual quality'}]
else:
 for label,prompt in PROMPTS:
  for steps in [4,8]:
   r={'model':'@cf/black-forest-labs/flux-1-schnell','case':label,'body':{'prompt':prompt,'steps':steps},'type':'REAL_GENERATION'}
   try:
    response=httpx.post('https://api.cloudflare.com/client/v4/accounts/'+account+'/ai/run/'+r['model'],headers={'Authorization':'Bearer '+token},json=r['body'],timeout=180)
    r.update(http=response.status_code,contentType=response.headers.get('content-type'),headers={k:v for k,v in response.headers.items() if k.lower() in ['retry-after','ratelimit','ratelimit-policy','cf-ray']});response.raise_for_status()
    if 'json' in response.headers.get('content-type',''):
     payload=response.json();assert payload.get('success') is not False,'API success=false';raw=base64.b64decode(payload['result']['image'],validate=True)
    else:raw=response.content
    path=OUT/(label+'-'+str(steps)+'.jpg');path.write_bytes(raw);r.update(ok=True,sha256=hashlib.sha256(raw).hexdigest(),bytes=len(raw),file=path.name,**validate(path))
   except Exception as e:r.update(ok=False,error=type(e).__name__+' (credentials and URL redacted)')
   report['cloudflare'].append(r);save()
   if not r.get('ok'):break
  if not r.get('ok'):break
save()
