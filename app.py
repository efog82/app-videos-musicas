import json
import os
from pathlib import Path
import shutil
import subprocess
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor

from flask import Flask, jsonify, render_template, request, send_file

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 100 * 1024 * 1024
ROOT = Path(__file__).resolve().parent
DATA = ROOT / '.runtime'
DATA.mkdir(exist_ok=True)
executor = ThreadPoolExecutor(max_workers=1)
jobs = {}
lock = threading.Lock()


def probe(path):
    result = subprocess.run(['ffprobe', '-v', 'error', '-show_format', '-show_streams', '-of', 'json', str(path)], capture_output=True, text=True, timeout=20, check=True)
    return json.loads(result.stdout)


def render(job_id, vertical):
    folder = DATA / job_id
    width, height = (720, 1280) if vertical else (1280, 720)
    wave_height = 220
    try:
        with lock:
            jobs[job_id]['status'] = 'processing'
        inputs = ['-f', 'lavfi', '-i', f'color=c=0x11152b:s={width}x{height}:r=24']
        if (folder / 'image').exists():
            inputs = ['-loop', '1', '-i', str(folder / 'image')]
        filters = (
            f'[0:v]scale={width}:{height}:force_original_aspect_ratio=increase,crop={width}:{height},setsar=1,format=yuv420p[bg];'
            f'[1:a]aformat=channel_layouts=mono,showwaves=s={width}x{wave_height}:mode=cline:colors=0x9d8dff:rate=24,format=rgba[wave];'
            f'[bg][wave]overlay=0:(H-h)*0.72:shortest=1,drawtext=textfile={folder / "title.txt"}:expansion=none:fontcolor=white:fontsize=36:x=(w-tw)/2:y=h*0.3:box=1:boxcolor=black@0.4:boxborderw=16[v]'
        )
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-filter_complex_threads', '1', *inputs, '-i', str(folder / 'audio'), '-filter_complex', filters, '-map', '[v]', '-map', '1:a:0', '-c:v', 'libx264', '-threads', '2', '-preset', 'veryfast', '-crf', '24', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', str(folder / 'video.mp4')], capture_output=True, timeout=1800, check=True)
        with lock:
            jobs[job_id]['status'] = 'done'
    except Exception:
        app.logger.exception('Falha ao gerar vídeo %s', job_id)
        with lock:
            jobs[job_id].update(status='error', error='Não foi possível gerar o vídeo. Experimente outro ficheiro.')
    finally:
        for name in ['audio', 'image', 'title.txt']:
            (folder / name).unlink(missing_ok=True)


@app.get('/')
def index():
    return render_template('index.html')


@app.post('/api/videos')
def create_video():
    if not shutil.which('ffmpeg') or not shutil.which('ffprobe'):
        return jsonify(error='O servidor precisa do FFmpeg instalado.'), 503
    audio = request.files.get('audio')
    if not audio or not audio.filename:
        return jsonify(error='Escolha uma música.'), 400
    with lock:
        if sum(j['status'] in ('queued', 'processing') for j in jobs.values()) >= 3:
            return jsonify(error='O servidor está ocupado. Tente novamente dentro de alguns minutos.'), 429
        job_id = uuid.uuid4().hex
        jobs[job_id] = {'status': 'queued'}
    folder = DATA / job_id
    folder.mkdir()
    try:
        audio.save(folder / 'audio')
        info = probe(folder / 'audio')
        duration = float(info['format']['duration'])
        if not any(s['codec_type'] == 'audio' for s in info['streams']) or not 0 < duration <= 600:
            raise ValueError()
        image = request.files.get('image')
        if image and image.filename:
            if Path(image.filename).suffix.lower() not in ('.jpg', '.jpeg', '.png', '.webp'):
                raise ValueError()
            image.save(folder / 'image')
            image_info = probe(folder / 'image')
            stream = next(s for s in image_info['streams'] if s['codec_type'] == 'video')
            if stream['width'] * stream['height'] > 25000000:
                raise ValueError()
        title = request.form.get('title', '').replace('\n', ' ').strip()[:48] or 'A minha música'
        (folder / 'title.txt').write_text(title, encoding='utf-8')
    except Exception:
        shutil.rmtree(folder, ignore_errors=True)
        with lock:
            jobs.pop(job_id, None)
        return jsonify(error='Use uma música válida até 10 minutos e uma imagem JPG, PNG ou WebP válida.'), 400
    executor.submit(render, job_id, request.form.get('format') == 'vertical')
    return jsonify(id=job_id), 202


@app.get('/api/videos/<job_id>')
def status(job_id):
    with lock:
        job = jobs.get(job_id)
        if not job:
            return jsonify(error='Vídeo não encontrado.'), 404
        return jsonify(**job)


@app.get('/videos/<job_id>')
def download(job_id):
    with lock:
        job = jobs.get(job_id)
        if not job or job['status'] != 'done':
            return jsonify(error='O vídeo ainda não está disponível.'), 404
    return send_file(DATA / job_id / 'video.mp4', mimetype='video/mp4', as_attachment=request.args.get('download') == '1', download_name='minha-musica.mp4', conditional=True)


@app.errorhandler(413)
def too_large(error):
    return jsonify(error='Os ficheiros devem ter menos de 100 MB no total.'), 413


if __name__ == '__main__':
    app.run(host=os.environ.get('HOST', '127.0.0.1'), port=int(os.environ.get('PORT', '8000')), debug=False)
