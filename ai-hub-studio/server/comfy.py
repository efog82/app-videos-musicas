"""ComfyUI API workflows: actual generated images/video/audio, no canned animation."""
import asyncio
import json
import time
import uuid
from pathlib import Path
from urllib.parse import urlencode


def bind_workflow(workflow, bindings, values):
    graph = json.loads(json.dumps(workflow))
    if not isinstance(graph,dict) or not graph:
        raise ValueError('Importe um workflow ComfyUI em formato API, não o formato visual')
    for name,target in bindings.items():
        if name not in values or values[name] is None:
            continue
        node,field = target.split('.',1)
        if node not in graph or field not in graph[node].get('inputs',{}):
            raise ValueError('Ligação de parâmetro inválida: ' + target)
        graph[node]['inputs'][field] = values[name]
    return graph


async def generate(store, router, p, template, values, progress):
    if not p['local'] and not values.get('remote_consent'):
        raise ValueError('Autorize o envio do conteúdo ao servidor remoto')
    if not p['local']:
        raise ValueError('ComfyUI remoto indisponível nesta versão: utilize o servidor PC privado com TLS')
    uploaded = {}
    for name in ('image','end_image','video','audio'):
        if values.get(name):
            path = store.path(values[name])
            with path.open('rb') as f:
                data = (await router.call(p,'POST','/upload/image',files={'image':(path.name,f)},data={'overwrite':'false'})).json()
            uploaded[name] = (data.get('subfolder','') + '/' + data['name']).lstrip('/')
    bound = dict(values,**uploaded)
    graph = bind_workflow(template['workflow'],template.get('bindings',{}),bound)
    # Validate node availability before starting a GPU job.
    nodes = (await router.call(p,'GET','/object_info')).json()
    missing = sorted({n.get('class_type','') for n in graph.values()} - set(nodes))
    if missing:
        raise ValueError('Nós ComfyUI em falta: ' + ', '.join(missing))
    response = (await router.call(p,'POST','/prompt',json={'prompt':graph,'client_id':str(uuid.uuid4())})).json()
    if response.get('node_errors') or not response.get('prompt_id'):
        raise ValueError('Workflow rejeitado; confirme os pesos, nós e parâmetros no ComfyUI')
    prompt_id = response['prompt_id']
    deadline = time.monotonic() + 7200
    while time.monotonic() < deadline:
        history = (await router.call(p,'GET','/history/' + prompt_id)).json().get(prompt_id)
        if history:
            if history.get('status',{}).get('status_str') == 'error':
                raise ValueError('ComfyUI falhou durante a execução; consulte o registo local do ComfyUI')
            files = []
            for output in history.get('outputs',{}).values():
                for group in ('images','gifs','videos','audio'):
                    for item in output.get(group,[]):
                        if not isinstance(item,dict) or not item.get('filename'):
                            continue
                        query = urlencode({k:item[k] for k in ('filename','subfolder','type') if k in item})
                        response = await router.call(p,'GET','/view?' + query)
                        suffix = Path(item['filename']).suffix
                        if suffix.lower() not in ('.png','.jpg','.jpeg','.webp','.mp4','.webm','.gif','.wav','.mp3','.flac'):
                            suffix = '.bin'
                        path = store.files / (str(uuid.uuid4()) + suffix)
                        path.write_bytes(response.content)
                        files.append(store.add_file(path,item['filename'],values.get('project','')))
            if not files:
                raise ValueError('Workflow terminou sem ficheiros exportados; acrescente nós SaveImage/Video/Audio')
            progress(1)
            return {'files':files,'method':'Geração ComfyUI: ' + template['name'],'prompt_id':prompt_id}
        await asyncio.sleep(2)
    raise ValueError('Tempo de espera excedido; a tarefa pode continuar no ComfyUI. Não repetir sem verificar a fila.')
