"""Real public-provider probes. No private keys, billing, GPU or fabricated outputs."""
import json,time,traceback
from pathlib import Path
import httpx
from gradio_client import Client,handle_file

out=Path('media-research');out.mkdir(exist_ok=True)
report=[]
prompt='A cinematic paper boat on a calm turquoise lake, mountains in the distance, natural daylight, no text'

def record(provider,kind,work):
    result={'provider':provider,'kind':kind,'started':time.time(),'test':'REAL_NETWORK'}
    try: result.update(work());result['ok']=True
    except Exception as e: result.update(ok=False,error=str(e)[:2000])
    report.append(result);(out/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False),flush=True)

def pollinations():
    with httpx.Client(timeout=180,follow_redirects=True) as c:
        r=c.get('https://image.pollinations.ai/prompt/'+httpx.URL('',params={'p':prompt}).query.decode()[2:],params={'width':512,'height':512,'seed':31001,'safe':'true'})
        r.raise_for_status()
        if not r.headers.get('content-type','').startswith('image/'):raise ValueError('Non-image response: '+r.text[:500])
        path=out/'pollinations.jpg';path.write_bytes(r.content)
        from PIL import Image
        with Image.open(path) as im: im.verify()
        return {'file':str(path),'bytes':len(r.content)}

def space(repo,video=False,reference=None):
    c=Client(repo,verbose=False)
    info=c.view_api(return_format='dict');(out/(repo.replace('/','_')+'-api.json')).write_text(json.dumps(info,default=str,indent=2))
    endpoints=info.get('named_endpoints',{})
    name=next((k for k in (('/image_to_video',) if reference else ('/text_to_video','/generate','/infer')) if k in endpoints),None)
    if not name:raise ValueError('No supported inference API; endpoints='+','.join(endpoints))
    params=endpoints[name].get('parameters',[])
    values={'prompt':prompt,'negative_prompt':'blurry, low quality','seed':31001,'seed_ui':31001,'randomize_seed':False,'width':512,'height':512,'num_inference_steps':4,'input_image_filepath':None,'input_video_filepath':None,'height_ui':256,'width_ui':448,'mode':'text-to-video','duration_ui':1.1,'ui_frames_to_use':9,'ui_guidance_scale':3.0,'improve_texture_flag':False}
    if reference:values['input_image_filepath']=handle_file(str(reference));values['mode']='image-to-video'
    args={}
    for p in params:
        key=p['parameter_name']
        if key in values:args[key]=values[key]
        elif p.get('parameter_has_default'):args[key]=p.get('parameter_default')
        else:raise ValueError('Unknown required parameter '+key)
    job=c.submit(api_name=name,**args)
    deadline=time.time()+360
    while not job.done():
        if time.time()>deadline:job.cancel();raise TimeoutError('Public queue exceeded 6 minutes; request cancellation sent')
        time.sleep(3)
    result=job.result()
    def find_files(obj):
        if isinstance(obj,str) and Path(obj).is_file():return [Path(obj)]
        if isinstance(obj,(list,tuple)):return [p for x in obj for p in find_files(x)]
        if isinstance(obj,dict):return [p for x in obj.values() for p in find_files(x)]
        return []
    files=find_files(result)
    if not files:raise ValueError('No downloaded media: '+str(result)[:500])
    import shutil
    copies=[]
    for i,p in enumerate(files):
        dest=out/(repo.replace('/','_')+f'-{i}'+p.suffix);shutil.copyfile(p,dest);copies.append(str(dest))
    return {'api':name,'files':copies,'result':str(result)[:600]}

record('Pollinations Classic','image',pollinations)
record('black-forest-labs/FLUX.1-schnell','image',lambda:space('black-forest-labs/FLUX.1-schnell'))
record('Lightricks/ltx-video-distilled','video',lambda:space('Lightricks/ltx-video-distilled',True))

if report[-1].get('ok') and (out/'pollinations.jpg').exists():
    record('Lightricks/ltx-video-distilled image-to-video','video',lambda:space('Lightricks/ltx-video-distilled',True,out/'pollinations.jpg'))
