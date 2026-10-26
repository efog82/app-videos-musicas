"""Public, anonymous API research; generations are real and never paid fallbacks."""
import json,time,subprocess,shutil
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor,as_completed
import httpx
from gradio_client import Client,handle_file
from PIL import Image,ImageDraw
out=Path('video-research-031');out.mkdir(exist_ok=True)
image=Image.new('RGB',(832,480),'#86bed4');d=ImageDraw.Draw(image);d.polygon([(0,300),(220,80),(450,300)],fill='#4b6981');d.rectangle((0,300,832,480),fill='#32889c');d.polygon([(340,360),(440,360),(400,390)],fill='white');image.save(out/'reference-fixture.png')
prompt='Cinematic wide shot of a paper boat drifting slowly across a turquoise mountain lake, gentle water ripples, soft golden sunlight, natural movement, camera slowly tracking sideways, no text'
repos=['Lightricks/LTX-2-3','Wan-AI/Wan2.2-S2V','Wan-AI/Wan2.1','zai-org/CogVideoX-5B-Space','kaidjuric/cogvideox-5b-text-to-video','zerogpu-aoti/wan2-2-fp8da-aoti','zerogpu-aoti/wan2-2-fp8da-aoti-faster','multimodalart/wan2-2-fast','multimodalart/wan2-2-fast-i2v','Fhwang/wan2-video-generation']
results=[]
def inspect(repo):
 r={'repo':repo,'test':'REAL_API_DISCOVERY','started':time.time()}
 try:
  with httpx.Client(timeout=25,follow_redirects=True) as h:
   response=h.get('https://huggingface.co/api/spaces/'+repo);response.raise_for_status();meta=response.json();r.update(stage=meta.get('runtime',{}).get('stage'),host=meta.get('host'),private=meta.get('private'))
   if r['private']:raise ValueError('Private Space: not accessed')
   if r['stage'] not in ['RUNNING','SLEEPING']:raise ValueError('Unavailable runtime '+str(r['stage']))
   for name in ['app.py','README.md']:
    source=h.get('https://huggingface.co/spaces/'+repo+'/resolve/main/'+name)
    if source.status_code==200:(out/(repo.replace('/','_')+'-'+name.replace('.','_')+'.txt')).write_text(source.text)
  c=Client(repo,verbose=False,httpx_kwargs={'timeout':45})
  api=c.view_api(return_format='dict');(out/(repo.replace('/','_')+'-api.json')).write_text(json.dumps(api,default=str,indent=2));(out/(repo.replace('/','_')+'-config.json')).write_text(json.dumps(c.config,default=str,indent=2));r['endpoints']=list(api.get('named_endpoints',{}));r['ok']=True
 except Exception as e:r.update(ok=False,error=str(e)[:1200])
 return r
with ThreadPoolExecutor(max_workers=5) as pool:
 for f in as_completed([pool.submit(inspect,repo) for repo in repos]):
  r=f.result();results.append(r);(out/'discovery.json').write_text(json.dumps(results,indent=2));print(json.dumps(r),flush=True)

def generate(repo,reference=False):
 r={'repo':repo,'test':'REAL_GENERATION','reference':reference,'started':time.time()}
 try:
  c=Client(repo,verbose=False,httpx_kwargs={'timeout':120});api=c.view_api(return_format='dict');endpoints=api.get('named_endpoints',{})
  names=['/image_to_video','/generate_video','/generate','/infer'] if reference else ['/text_to_video','/generate_video','/generate','/infer']
  name=next((k for k in names if k in endpoints),None)
  if not name:raise ValueError('No compatible public endpoint: '+','.join(endpoints))
  r['endpoint']=name
  values={'prompt':prompt,'negative_prompt':'blurry, distorted, low quality, watermark','seed':310031,'randomize_seed':False,'width':832,'height':480,'duration':5,'duration_seconds':5,'num_frames':81,'frame_rate':16,'fps':16,'aspect_ratio':'16:9','image':None,'input_image':None,'image_path':None,'input_image_path':None,'audio':None,'audio_path':None,'input_audio':None}
  if reference:
   for key in ['image','input_image','image_path','input_image_path','start_image']:values[key]=handle_file(str(out/'reference-fixture.png'))
  args={}
  for p in endpoints[name]['parameters']:
   key=p['parameter_name']
   if key in values:args[key]=values[key]
   elif p.get('parameter_has_default'):args[key]=p.get('parameter_default')
   else:raise ValueError('Unsupported required parameter '+key)
  r['arguments']={k:v for k,v in args.items() if k not in ['prompt','negative_prompt']};print(json.dumps({'submitting':repo,'endpoint':name,'reference':reference,'args':r['arguments']},default=str),flush=True)
  job=c.submit(api_name=name,**args);deadline=time.time()+420
  while not job.done():
   if time.time()>deadline:job.cancel();raise TimeoutError('Public generation exceeded 7 min; cancellation requested')
   time.sleep(3)
  result=job.result();r['result']=str(result)[:1000]
  def files(v):
   if isinstance(v,str) and Path(v).is_file():return [Path(v)]
   if isinstance(v,(tuple,list)):return [p for x in v for p in files(x)]
   if isinstance(v,dict):return [p for x in v.values() for p in files(x)]
   return []
  videos=[p for p in files(result) if p.suffix in ['.mp4','.webm']]
  if not videos:raise ValueError('No downloaded video '+str(result)[:500])
  r['videos']=[]
  for i,p in enumerate(videos):
   dest=out/(repo.replace('/','_')+('-i2v' if reference else '-t2v')+f'-{i}'+p.suffix);shutil.copyfile(p,dest)
   probe=subprocess.run(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(dest)],capture_output=True,text=True,check=True);info=json.loads(probe.stdout);r['videos'].append({'file':str(dest),'bytes':dest.stat().st_size,'probe':info})
   assert any(s['codec_type']=='video' and s['width']>0 and s['height']>0 for s in info['streams']);assert float(info['format']['duration'])>0
  r['ok']=True
 except Exception as e:r.update(ok=False,error=str(e)[:2000])
 r['elapsed']=round(time.time()-r['started'],2);return r
# One generation per selected Space, with ordinary public quota and no auth workaround.
selected=[(r['repo'],r['repo'].endswith('aoti-faster')) for r in results if r.get('ok') and r['repo'] in ['Lightricks/LTX-2-3','kaidjuric/cogvideox-5b-text-to-video','zai-org/CogVideoX-5B-Space','zerogpu-aoti/wan2-2-fp8da-aoti','zerogpu-aoti/wan2-2-fp8da-aoti-faster','multimodalart/wan2-2-fast','multimodalart/wan2-2-fast-i2v']]
generations=[]
with ThreadPoolExecutor(max_workers=3) as pool:
 for f in as_completed([pool.submit(generate,repo,ref) for repo,ref in selected]):
  r=f.result();generations.append(r);(out/'generations.json').write_text(json.dumps(generations,indent=2,default=str));print(json.dumps(r,default=str),flush=True)
