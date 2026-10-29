"""Provider discovery, explicit cost policy, quota telemetry and bounded failover."""
import asyncio
import ipaddress
import time
from urllib.parse import urlsplit
import httpx

PRESETS = [
    {'name':'Ollama', 'type':'ollama', 'url':'http://127.0.0.1:11434', 'local':True},
    {'name':'LM Studio / llama.cpp', 'type':'openai', 'url':'http://127.0.0.1:1234/v1', 'local':True},
    {'name':'OpenRouter', 'type':'openai', 'url':'https://openrouter.ai/api/v1', 'local':False},
    {'name':'Google Gemini', 'type':'gemini', 'url':'https://generativelanguage.googleapis.com/v1beta', 'local':False},
    {'name':'Groq', 'type':'openai', 'url':'https://api.groq.com/openai/v1', 'local':False},
    {'name':'Mistral', 'type':'openai', 'url':'https://api.mistral.ai/v1', 'local':False},
    {'name':'Hugging Face', 'type':'openai', 'url':'https://router.huggingface.co/v1', 'local':False},
    {'name':'Cerebras', 'type':'openai', 'url':'https://api.cerebras.ai/v1', 'local':False},
    {'name':'SambaNova', 'type':'openai', 'url':'https://api.sambanova.ai/v1', 'local':False},
    {'name':'ComfyUI', 'type':'comfy', 'url':'http://127.0.0.1:8188', 'local':True},
]


def local_url(url):
    h = urlsplit(url).hostname or ''
    if h == 'localhost':
        return True
    try:
        ip = ipaddress.ip_address(h)
        return ip.is_loopback or (ip.is_private and not ip.is_unspecified and not ip.is_link_local)
    except ValueError:
        return False


def validate_url(url, local):
    parts = urlsplit(url)
    if parts.scheme not in ('https', 'http') or not parts.hostname or parts.username or parts.password or parts.query or parts.fragment:
        raise ValueError('Endpoint inválido: utilize um URL sem credenciais ou parâmetros')
    if local != local_url(url):
        raise ValueError('O tipo local/remoto não corresponde ao endereço; use um IP privado para servidores LAN')
    if not local and parts.scheme != 'https':
        raise ValueError('Os serviços externos exigem HTTPS')
    # Block cloud metadata and unusual local services by policy.
    if parts.port is not None and parts.port < 1024 and local:
        raise ValueError('Use uma porta local superior a 1023')
    return url.rstrip('/')


class ProviderError(Exception):
    pass


class Router:
    def __init__(self, store, transport=None):
        self.store = store
        self.transport = transport
        self.cooldowns = {}

    def client(self):
        return httpx.AsyncClient(timeout=httpx.Timeout(120, connect=10),
                                 follow_redirects=False, transport=self.transport)

    async def call(self, p, method, route, **kwargs):
        headers = dict(kwargs.pop('headers', {}))
        key = self.store.secret(p['id'])
        if key:
            headers['x-goog-api-key' if p['type'] == 'gemini' else 'Authorization'] = key if p['type'] == 'gemini' else 'Bearer ' + key
        try:
            async with self.client() as client:
                response = await client.request(method, p['url'] + route, headers=headers, **kwargs)
        except httpx.HTTPError as exc:
            raise ProviderError('Não foi possível contactar ' + p['name']) from exc
        # Only record published limits. Unknown periods remain unknown.
        limits = {k:v for k,v in response.headers.items() if k.startswith(('x-ratelimit-', 'ratelimit-')) or k == 'retry-after'}
        if limits:
            self.store.put('quota', {'provider':p['id'], 'headers':limits, 'observed':time.time(),
                                    'period':'desconhecido; consultar cabeçalhos e fornecedor'}, id='quota:' + p['id'])
        if response.status_code == 429:
            try:
                delay = min(86400, max(1, float(response.headers.get('retry-after', '60'))))
            except ValueError:
                delay = 60
            self.cooldowns[p['id']] = time.time() + delay
        if response.is_error or response.is_redirect:
            # Never expose request URLs, upstream error bodies or keys.
            reason={401:'chave inválida ou em falta',403:'permissão/região/modelo recusado',404:'endpoint incorreto ou modelo não instalado',402:'quota gratuita indisponível; sem pagamento automático',429:'quota esgotada; respeite Retry-After',503:'serviço temporariamente indisponível'}.get(response.status_code,'serviço indisponível ou pedido recusado')
            raise ProviderError(f"{p['name']}: HTTP {response.status_code} — {reason}")
        return response

    async def discover(self, p):
        if p['type'] == 'comfy':
            data = (await self.call(p, 'GET', '/object_info')).json()
            return {'nodes':list(data), 'note':'Capacidades dependem dos nós, pesos e workflow instalado.'}
        route = '/api/tags' if p['type'] == 'ollama' else '/models'
        data = (await self.call(p, 'GET', route)).json()
        rows = data.get('models', []) if p['type'] in ('ollama','gemini') else data.get('data', [])
        models = []
        for row in rows:
            id = row.get('id') or row.get('name', '').removeprefix('models/')
            if not id:
                continue
            caps = ['chat', 'code']
            if p['type'] == 'gemini' and 'generateContent' not in row.get('supportedGenerationMethods', []):
                continue
            architecture = row.get('architecture', {})
            inputs = architecture.get('input_modalities', [])
            if 'image' in inputs:
                caps.append('vision')
            pricing = row.get('pricing', {})
            # OpenRouter has more price fields than prompt/completion (images, requests, etc.).
            free = False
            if 'openrouter.ai' == urlsplit(p['url']).hostname and pricing:
                try:
                    free = 'prompt' in pricing and 'completion' in pricing and all(float(v) == 0 for v in pricing.values())
                except (ValueError, TypeError):
                    pass
            models.append({'id':id, 'provider':p['id'], 'name':row.get('name', id),
                           'capabilities':caps, 'free':bool(p['local'] or free), 'pricing':pricing,
                           'context':row.get('context_length'), 'capability_source':'metadata' if inputs else 'protocol; visão não confirmada'})
        self.store.put('catalog', {'models':models, 'observed':time.time()}, id='catalog:' + p['id'])
        return {'models':models}

    def allowed(self, p, model, remote_consent, allow_metered):
        if not p.get('enabled', True):
            return False
        if not p['local'] and not remote_consent:
            return False
        if p['local']:
            return True
        if p.get('free_plan'):
            return True
        if allow_metered:
            return True
        # Price validation is fresh and exact, never infer free from a model's name.
        try:
            c = self.store.get('catalog:' + p['id'])
            return time.time() - c['observed'] < 3600 and any(m['id'] == model and m['free'] for m in c['models'])
        except KeyError:
            return False

    async def one(self, p, model, messages):
        if p['type'] == 'ollama':
            data = (await self.call(p, 'POST', '/api/chat', json={'model':model, 'messages':messages, 'stream':False})).json()
            return data['message']['content']
        if p['type'] == 'gemini':
            contents = [{'role':'model' if m['role'] == 'assistant' else 'user', 'parts':[{'text':m['content']}]} for m in messages if m['role'] != 'system']
            body = {'contents':contents}
            system = '\n'.join(m['content'] for m in messages if m['role'] == 'system')
            if system:
                body['systemInstruction'] = {'parts':[{'text':system}]}
            data = (await self.call(p, 'POST', '/models/' + model + ':generateContent', json=body)).json()
            return ''.join(x.get('text','') for x in data['candidates'][0]['content']['parts'])
        data = (await self.call(p, 'POST', '/chat/completions', json={'model':model, 'messages':messages, 'stream':False})).json()
        return data['choices'][0]['message']['content']

    async def chat(self, req):
        candidates = req.get('candidates', [])
        if not candidates:
            for c in self.store.list('modelcheck'):
                if c.get('ok') and time.time()-c.get('observed',0)<1800:
                    candidates.append({'provider':c['provider'],'model':c['model']})
        errors = []
        for candidate in candidates:
            try:
                p = self.store.get(candidate['provider'], 'provider')
                if not self.allowed(p, candidate['model'], req.get('remote_consent',False), req.get('allow_metered',False)):
                    errors.append(p['name'] + ': bloqueado pela política de privacidade/custos')
                    continue
                if self.cooldowns.get(p['id'],0) > time.time():
                    errors.append(p['name'] + ': período de espera por quota')
                    continue
                text = await self.one(p, candidate['model'], req['messages'])
                self.store.put('modelcheck',{'provider':p['id'],'model':candidate['model'],'ok':True,'observed':time.time()},id='check:'+p['id']+':'+candidate['model'])
                return {'text':text, 'model':candidate['model'], 'provider':p['name'], 'failover':errors}
            except (ProviderError, KeyError, ValueError, TypeError, IndexError) as exc:
                self.store.put('modelcheck',{'provider':candidate.get('provider',''),'model':candidate.get('model',''),'ok':False,'observed':time.time()},id='check:'+candidate.get('provider','')+':'+candidate.get('model',''))
                errors.append(str(exc) if isinstance(exc, ProviderError) else 'Resposta inválida ou configuração em falta')
        raise ProviderError('; '.join(errors) or 'Descubra modelos e configure um servidor local ou fornecedor autorizado')
