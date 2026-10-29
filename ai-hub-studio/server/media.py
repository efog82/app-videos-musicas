"""Real FFmpeg editing and audio analysis; never presented as generative video."""
import array
import json
import math
import re
import shutil
import subprocess
import uuid
from pathlib import Path


def run(args, timeout=600):
    try:
        result = subprocess.run(args, capture_output=True, timeout=timeout, check=False)
    except FileNotFoundError as exc:
        raise ValueError('Instale FFmpeg e ffprobe no PC e adicione-os ao PATH') from exc
    if result.returncode:
        # Diagnostics are local, limited and omit input command lines.
        raise ValueError('Processamento multimédia falhou: ' + result.stderr.decode(errors='replace')[-1200:])
    return result.stdout


def probe(path):
    return json.loads(run(['ffprobe','-v','error','-show_format','-show_streams','-of','json',str(path)], 30))


def analyse(path):
    info = probe(path)
    raw = run(['ffmpeg','-v','error','-i',str(path),'-vn','-t','1800','-ac','1','-ar','8000','-f','s16le','pipe:1'])
    samples = array.array('h', raw)
    hop = 800
    energy = [math.sqrt(sum(float(x)*x for x in samples[i:i+hop])/max(1,len(samples[i:i+hop]))) / 32768
              for i in range(0,len(samples),hop)]
    # Signal peak times are estimates, not speech or lyric recognition.
    peaks = [round(i/10,2) for i in range(1,len(energy)-1)
             if energy[i] > energy[i-1]*1.2 and energy[i] > energy[i+1] and energy[i] > .03]
    gaps = [b-a for a,b in zip(peaks,peaks[1:]) if .25 < b-a < 1.5]
    bpm = round(60 / sorted(gaps)[len(gaps)//2]) if gaps else None
    return {'duration':float(info['format'].get('duration',0)), 'rms':energy,
            'peaks':peaks, 'bpm_estimate':bpm, 'method':'Picos de energia; estimativa, não análise semântica',
            'analysis_limit_seconds':1800}


def parse_srt(text):
    blocks = re.split(r'\n\s*\n', text.replace('\r','').strip())
    result = []
    def ms(t):
        h,m,s,frac = re.split('[:,.]', t)
        return int(h)*3600000 + int(m)*60000 + int(s)*1000 + int(frac)
    for b in blocks:
        lines = b.splitlines()
        at = next((i for i,l in enumerate(lines) if '-->' in l), None)
        if at is None:
            raise ValueError('Bloco SRT sem intervalo')
        match = re.fullmatch(r'(\d{2,}:\d{2}:\d{2}[,.]\d{3})\s*-->\s*(\d{2,}:\d{2}:\d{2}[,.]\d{3})',lines[at])
        if not match:
            raise ValueError('Intervalo SRT inválido')
        start,end = map(ms,match.groups())
        if end <= start:
            raise ValueError('A legenda deve terminar depois de começar')
        result.append({'start_ms':start,'end_ms':end,'text':'\n'.join(lines[at+1:])})
    return result


def write_srt(items):
    def ts(ms):
        ms = int(ms)
        if ms < 0:
            raise ValueError('Tempo negativo')
        s,f = divmod(ms,1000); m,s = divmod(s,60); h,m = divmod(m,60)
        return f'{h:02}:{m:02}:{s:02},{f:03}'
    return '\n\n'.join(f"{i+1}\n{ts(x['start_ms'])} --> {ts(x['end_ms'])}\n{x['text']}" for i,x in enumerate(items)) + '\n'


SIZES = {'1:1':(720,720),'4:5':(720,900),'9:16':(720,1280),'16:9':(1280,720)}


def render(store, req, progress=lambda x: None):
    clips = req.get('clips',[])
    if not clips or len(clips) > 200:
        raise ValueError('A timeline deve conter entre 1 e 200 clips')
    if req.get('aspect','16:9') not in SIZES:
        raise ValueError('Proporção inválida')
    w,h = SIZES[req.get('aspect','16:9')]
    tmp = store.root / ('render-' + str(uuid.uuid4()))
    tmp.mkdir()
    total = 0
    try:
        segments = []
        for i,c in enumerate(clips):
            path = store.path(c['file'])
            start = float(c.get('start',0)); duration = float(c.get('duration',5))
            if not math.isfinite(start) or not math.isfinite(duration) or start < 0 or duration <= 0 or duration > 600:
                raise ValueError('Intervalo inválido')
            total += duration
            if total > 3600:
                raise ValueError('Duração máxima de exportação: uma hora')
            out = tmp / f'{i:04}.mp4'
            info = probe(path)
            image = any(s.get('codec_name') in ('png','mjpeg','webp','bmp') for s in info['streams']) and not info['format'].get('duration')
            args = ['ffmpeg','-v','error','-y']
            if image:
                args += ['-loop','1']
            else:
                args += ['-ss',str(start)]
            args += ['-i',str(path),'-t',str(duration),'-an']
            vf = f'scale={w}:{h}:force_original_aspect_ratio=decrease,pad={w}:{h}:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=25'
            effect = c.get('effect','none')
            if effect == 'fade':
                vf += f',fade=t=in:st=0:d=0.3,fade=t=out:st={max(0,duration-.3)}:d=0.3'
            elif effect == 'gray':
                vf += ',hue=s=0'
            elif effect != 'none':
                raise ValueError('Efeito não implementado')
            args += ['-vf',vf,'-c:v','libx264','-preset','veryfast','-pix_fmt','yuv420p',str(out)]
            run(args)
            segments.append(out)
            progress((i+1)/(len(clips)+1))
        concat = tmp / 'clips.txt'
        concat.write_text('\n'.join("file '" + p.name + "'" for p in segments),encoding='utf-8')
        joined = tmp / 'joined.mp4'
        run(['ffmpeg','-v','error','-y','-f','concat','-safe','1','-i',str(concat),'-c','copy',str(joined)])
        dest = store.files / (str(uuid.uuid4()) + '.mp4')
        args = ['ffmpeg','-v','error','-y','-i',str(joined)]
        if req.get('audio'):
            args += ['-i',str(store.path(req['audio'])),'-map','0:v:0','-map','1:a:0',
                     '-af','loudnorm=I=-16:TP=-1.5:LRA=11,apad','-c:a','aac']
        if req.get('srt'):
            text = store.path(req['srt']).read_text(encoding='utf-8-sig')
            parse_srt(text)
            # Fixed safe filename avoids FFmpeg filter path injection (including Windows drives).
            subtitle = store.files / ('sub-' + uuid.uuid4().hex + '.srt')
            subtitle.write_text(text,encoding='utf-8')
            try:
                # Render from the private files dir, only a generated basename enters the filter.
                cmd = args + ['-vf',f"subtitles=filename={subtitle.name}",'-c:v','libx264',
                              '-pix_fmt','yuv420p','-t',str(total),'-movflags','+faststart',str(dest)]
                result = subprocess.run(cmd,cwd=store.files,capture_output=True,timeout=600)
                if result.returncode:
                    raise ValueError('FFmpeg não conseguiu aplicar legendas (requer filtro libass)')
            finally:
                subtitle.unlink(missing_ok=True)
        else:
            run(args + ['-c:v','copy','-t',str(total),'-movflags','+faststart',str(dest)])
        progress(1)
        return {'file':store.add_file(dest, 'AI Hub Studio.mp4',req.get('project','')), 'method':'Edição FFmpeg; não é vídeo generativo', 'duration':total}
    finally:
        shutil.rmtree(tmp,ignore_errors=True)
