"""AI Hub Studio PC API. Launch with python -m server.launch from project root."""
import asyncio
import hashlib
import hmac
import json
import os
import platform
import secrets
import shutil
import time
import uuid
from pathlib import Path
from typing import Any

import psutil
from fastapi import Depends, FastAPI, File, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from .storage import Store
from .providers import PRESETS, Router, ProviderError, validate_url
from .jobs import Jobs
from . import media, comfy

MAX_UPLOAD = 512 * 1024 * 1024


class ProviderIn(BaseModel):
    name: str = Field(min_length=1,max_length=100)
    type: str
    url: str
    local: bool = False
    key: str | None = None
    enabled: bool = True


class Message(BaseModel):
    role: str
    content: str = Field(max_length=200000)


class ChatIn(BaseModel):
    messages: list[Message] = Field(min_length=1,max_length=200)
    candidates: list[dict[str,str]] = Field(default_factory=list,max_length=30)
    remote_consent: bool = False
    allow_metered: bool = False
    project: str = ''
    conversation: str | None = None


class WorkflowIn(BaseModel):
    name: str = Field(min_length=1,max_length=120)
    modality: str
    workflow: dict[str,Any]
    bindings: dict[str,str] = Field(default_factory=dict)
    requirements: str = ''


class GenerateIn(BaseModel):
    provider: str
    template: str
    values: dict[str,Any]


def create_app(root=None, token=None, transport=None):
    root = root or os.environ.get('AIHUB_DATA',str(Path.home()/'.aihub-studio'))
    token = token or os.environ.get('AIHUB_TOKEN','')
    if len(token) < 32:
        raise ValueError('Defina AIHUB_TOKEN com pelo menos 32 caracteres ou use python -m server.launch')
    store = Store(root)
    router = Router(store, transport)
    jobs = Jobs(store)
    app = FastAPI(title='AI Hub Studio PC',version='0.1.0',docs_url=None,redoc_url=None,openapi_url=None)
    app.state.store, app.state.router, app.state.jobs = store,router,jobs
    attempts = {}

    async def auth(request: Request):
        # Rate-limit failed authentication; never accept tokens through URLs.
        peer = request.client.host if request.client else 'unknown'
        now = time.monotonic()
        if len(attempts) > 10000:
            attempts.clear()
        count,since = attempts.get(peer,(0,now))
        if now-since > 60:
            count,since = 0,now
        if count >= 30:
            raise HTTPException(429,'Demasiadas tentativas; aguarde um minuto')
        supplied = request.headers.get('authorization','').removeprefix('Bearer ')
        if not hmac.compare_digest(supplied.encode(), token.encode()):
            attempts[peer] = (count+1,since)
            raise HTTPException(401,'Emparelhamento necessário')

    @app.exception_handler(KeyError)
    async def missing(request, exc):
        from fastapi.responses import JSONResponse
        return JSONResponse({'detail':'Registo ou ficheiro não encontrado'},status_code=404)

    @app.exception_handler(ValueError)
    async def invalid(request, exc):
        from fastapi.responses import JSONResponse
        return JSONResponse({'detail':str(exc)},status_code=400)

    @app.exception_handler(ProviderError)
    async def upstream(request, exc):
        from fastapi.responses import JSONResponse
        return JSONResponse({'detail':str(exc)},status_code=502)

    @app.get('/health')
    def health():
        return {'service':'AI Hub Studio','version':'0.1.0','pairing_required':True}

    protected = [Depends(auth)]

    @app.get('/v1/hardware',dependencies=protected)
    def hardware():
        ram = psutil.virtual_memory().total / 1024**3
        gpu = 'Não detetada automaticamente'
        if shutil.which('nvidia-smi'):
            try:
                gpu = media.run(['nvidia-smi','--query-gpu=name,memory.total','--format=csv,noheader'],10).decode().strip()
            except ValueError:
                pass
        return {'os':platform.system(),'cpu':platform.processor() or platform.machine(),
                'ram_gb':round(ram,1),'gpu':gpu, 'ffmpeg':bool(shutil.which('ffmpeg')),
                'recommendation':'Comece com modelos de texto quantizados de 1–3B; confirme memória livre. Vídeo Wan/LTX/AnimateDiff requer avaliação de VRAM e backend. Radeon integrada com 16 GB partilhados não é recomendada para vídeo pesado.'}

    @app.get('/v1/presets',dependencies=protected)
    def presets():
        return PRESETS

    @app.get('/v1/providers',dependencies=protected)
    def providers():
        return [dict(p,has_key=bool(store.secret(p['id']))) for p in store.list('provider')]

    def save_provider(p, id=None):
        if p.type not in ('openai','ollama','gemini','comfy'):
            raise ValueError('Protocolo não suportado')
        url = validate_url(p.url,p.local)
        item = store.put('provider',dict(name=p.name,type=p.type,url=url,local=p.local,enabled=p.enabled),id=id)
        if p.key is not None:
            store.secret(item['id'],p.key)
        return item

    @app.post('/v1/providers',dependencies=protected)
    def add_provider(p:ProviderIn):
        return save_provider(p)

    @app.put('/v1/providers/{id}',dependencies=protected)
    def update_provider(id:str,p:ProviderIn):
        store.get(id,'provider')
        return save_provider(p,id)

    @app.delete('/v1/providers/{id}',dependencies=protected)
    def delete_provider(id:str):
        store.get(id,'provider'); store.delete(id); store.delete('catalog:'+id); store.delete('quota:'+id)
        return {'deleted':True}

    @app.post('/v1/providers/{id}/discover',dependencies=protected)
    async def discover(id:str):
        return await router.discover(store.get(id,'provider'))

    @app.get('/v1/models',dependencies=protected)
    def models():
        return [m for c in store.list('catalog') for m in c['models']]

    @app.get('/v1/quotas',dependencies=protected)
    def quotas():
        return store.list('quota')

    @app.post('/v1/chat',dependencies=protected)
    async def chat(req:ChatIn):
        body = req.model_dump()
        for m in body['messages']:
            if m['role'] not in ('user','assistant','system'):
                raise ValueError('Papel de mensagem inválido')
        if req.project:
            project = store.get(req.project,'project')
            if project.get('context'):
                body['messages'] = [{'role':'system','content':project['context']}] + body['messages']
        if req.conversation:
            store.get(req.conversation,'conversation')
        result = await router.chat(body)
        saved = store.put('conversation',{'messages':body['messages']+[{'role':'assistant','content':result['text']}],
                    'model':result['model'],'provider':result['provider']},id=req.conversation,project=req.project)
        return dict(result,conversation=saved['id'])

    @app.post('/v1/compare',dependencies=protected)
    async def compare(req:ChatIn):
        if not req.candidates or len(req.candidates)>6:
            raise ValueError('Selecione entre um e seis modelos')
        async def one(c):
            try:
                return await chat(req.model_copy(update={'candidates':[c],'conversation':None}))
            except ProviderError as exc:
                return dict(c,error=str(exc))
        return await asyncio.gather(*(one(c) for c in req.candidates))

    @app.get('/v1/conversations',dependencies=protected)
    def conversations(q:str='',project:str|None=None):
        return store.list('conversation',q,project)

    @app.delete('/v1/conversations/{id}',dependencies=protected)
    def delete_conversation(id:str):
        store.get(id,'conversation'); store.delete(id)
        return {'deleted':True}

    @app.get('/v1/projects',dependencies=protected)
    def projects():
        return store.list('project')

    @app.post('/v1/projects',dependencies=protected)
    def project(body:dict):
        name = str(body.get('name','')).strip()
        if not name or len(name)>120:
            raise ValueError('Nome de projeto obrigatório (até 120 caracteres)')
        return store.put('project',{'name':name,'context':str(body.get('context',''))[:100000]})

    @app.delete('/v1/projects/{id}',dependencies=protected)
    def delete_project(id:str):
        store.get(id,'project')
        for c in store.list('conversation',project=id):
            store.delete(c['id'])
        for record in store.list('file',project=id):
            store.path(record['id']).unlink(); store.delete(record['id'])
        store.delete(id)
        return {'deleted':True}

    @app.post('/v1/files',dependencies=protected)
    async def upload(file:UploadFile=File(...),project:str=''):
        suffix = Path(file.filename or '').suffix.lower()
        allowed = {'.png','.jpg','.jpeg','.webp','.mp4','.webm','.mov','.wav','.mp3','.m4a','.flac','.ogg','.srt','.txt','.pdf','.json'}
        if suffix not in allowed:
            raise ValueError('Formato de ficheiro não suportado')
        if project:
            store.get(project,'project')
        path = store.files / (str(uuid.uuid4()) + suffix)
        size = 0
        try:
            with path.open('wb') as f:
                while chunk := await file.read(1024*1024):
                    size += len(chunk)
                    if size > MAX_UPLOAD:
                        raise HTTPException(413,'Limite por ficheiro: 512 MiB')
                    f.write(chunk)
            return store.add_file(path,Path(file.filename).name,project)
        except BaseException:
            path.unlink(missing_ok=True)
            raise
        finally:
            await file.close()

    @app.get('/v1/files',dependencies=protected)
    def files(project:str|None=None):
        return store.list('file',project=project)

    @app.get('/v1/files/{id}/download',dependencies=protected)
    def download(id:str):
        return FileResponse(store.path(id),filename=store.get(id,'file')['name'])

    @app.delete('/v1/files/{id}',dependencies=protected)
    def delete_file(id:str):
        store.path(id).unlink(); store.delete(id)
        return {'deleted':True}

    @app.get('/v1/files/{id}/text',dependencies=protected)
    def text(id:str):
        path = store.path(id)
        if path.suffix == '.pdf':
            from pypdf import PdfReader
            reader = PdfReader(path)
            content = '\n'.join(p.extract_text() or '' for p in reader.pages[:100])
        elif path.suffix in ('.txt','.srt','.json'):
            content = path.read_text(encoding='utf-8-sig')
        else:
            raise ValueError('Extração de texto disponível para PDF, TXT, JSON e SRT; PDFs digitalizados exigem OCR externo')
        return {'text':content[:200000], 'limit':200000}

    @app.get('/v1/workflows',dependencies=protected)
    def workflows():
        return store.list('workflow')

    @app.post('/v1/workflows',dependencies=protected)
    def workflow(body:WorkflowIn):
        if body.modality not in ('image','video','audio'):
            raise ValueError('Modalidade inválida')
        comfy.bind_workflow(body.workflow,body.bindings,{k:'validate' for k in body.bindings})
        return store.put('workflow',body.model_dump())

    @app.delete('/v1/workflows/{id}',dependencies=protected)
    def delete_workflow(id:str):
        store.get(id,'workflow'); store.delete(id)
        return {'deleted':True}

    @app.post('/v1/generate',dependencies=protected)
    def generate(body:GenerateIn):
        p = store.get(body.provider,'provider')
        if p['type'] != 'comfy':
            raise ValueError('Selecione um servidor ComfyUI')
        template = store.get(body.template,'workflow')
        return jobs.submit('generative-'+template['modality'],lambda progress:asyncio.run(comfy.generate(store,router,p,template,body.values,progress)))

    @app.post('/v1/render',dependencies=protected)
    def render(body:dict):
        return jobs.submit('render',lambda progress:media.render(store,body,progress))

    @app.post('/v1/audio/analyse/{id}',dependencies=protected)
    def analyse(id:str):
        path = store.path(id)
        return jobs.submit('audio-analysis',lambda progress:media.analyse(path))

    @app.post('/v1/audio/normalize/{id}',dependencies=protected)
    def normalize(id:str):
        path = store.path(id)
        def work(progress):
            out = store.files/(str(uuid.uuid4())+'.wav')
            media.run(['ffmpeg','-v','error','-y','-i',str(path),'-vn','-af','loudnorm=I=-16:TP=-1.5:LRA=11',str(out)])
            return {'file':store.add_file(out,'Áudio normalizado.wav')}
        return jobs.submit('normalize',work)

    @app.post('/v1/transcribe',dependencies=protected)
    async def transcribe(body:dict):
        p = store.get(body['provider'],'provider')
        model = body.get('model','whisper-1')
        if p['type'] != 'openai':
            raise ValueError('A transcrição requer um endpoint compatível OpenAI /audio/transcriptions')
        if not router.allowed(p,model,body.get('remote_consent',False),body.get('allow_metered',False)):
            raise ValueError('Envio externo/custo não autorizado')
        path = store.path(body['file'])
        with path.open('rb') as f:
            r = await router.call(p,'POST','/audio/transcriptions',files={'file':(path.name,f)},
                                  data={'model':model,'response_format':'srt','language':'pt'})
        media.parse_srt(r.text)
        dest = store.files/(str(uuid.uuid4())+'.srt'); dest.write_text(r.text,encoding='utf-8')
        return {'file':store.add_file(dest,'Legendas.srt')}

    @app.post('/v1/srt',dependencies=protected)
    def srt(body:dict):
        items = media.parse_srt(body['text'])
        offset = int(body.get('offset_ms',0))
        for item in items:
            item['start_ms'] += offset; item['end_ms'] += offset
        text = media.write_srt(items)
        dest = store.files/(str(uuid.uuid4())+'.srt'); dest.write_text(text,encoding='utf-8')
        return {'items':items,'text':text,'file':store.add_file(dest,'Legendas.srt')}

    @app.get('/v1/jobs',dependencies=protected)
    def all_jobs():
        return store.list('job')

    @app.get('/v1/jobs/{id}',dependencies=protected)
    def job(id:str):
        return store.get(id,'job')

    @app.post('/v1/jobs/{id}/cancel',dependencies=protected)
    def cancel(id:str):
        return jobs.cancel(id)

    @app.post('/v1/agent/plan',dependencies=protected)
    def plan(body:dict):
        # Explicit deterministic orchestration plan; no claim of autonomous generative reasoning.
        duration = float(body.get('duration',120))
        if not 0 < duration <= 3600:
            raise ValueError('Duração entre 1 e 3600 segundos')
        plan = {'name':body.get('prompt','Sequência multimédia'),'duration':duration,
                'method':'Plano determinístico editável; os passos executam-se individualmente',
                'steps':[{'tool':'audio-analysis','status':'ready' if body.get('audio') else 'requires-input'},
                         {'tool':'storyboard','status':'requires-chat-model'},
                         {'tool':'generative-image','status':'requires-workflow'},
                         {'tool':'generative-video','status':'requires-workflow-and-hardware'},
                         {'tool':'transcription','status':'requires-transcription-endpoint'},
                         {'tool':'render','status':'requires-approved-timeline'}],
                'shots':[{'start':i,'duration':min(5,duration-i),'prompt':''} for i in range(0,int(duration+.999),5)]}
        return store.put('plan',plan)

    @app.get('/v1/agent/plans',dependencies=protected)
    def plans():
        return store.list('plan')

    return app
