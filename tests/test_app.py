import io
import shutil
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

import app as module


class ConversionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.original_data = module.DATA
        module.DATA = Path(cls.temp.name)
        cls.audio = module.DATA / 'sample.wav'
        cls.image = module.DATA / 'sample.png'
        subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1', str(cls.audio)], check=True)
        subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'color=c=blue:s=640x480', '-frames:v', '1', '-threads', '1', str(cls.image)], check=True)

    @classmethod
    def tearDownClass(cls):
        module.executor.shutdown(wait=True)
        module.DATA = cls.original_data
        cls.temp.cleanup()

    def setUp(self):
        self.client = module.app.test_client()

    def test_page_and_invalid_audio(self):
        self.assertEqual(self.client.get('/').status_code, 200)
        self.assertEqual(self.client.post('/api/videos').status_code, 400)
        response = self.client.post('/api/videos', data={'audio': (io.BytesIO(b'not audio'), 'invalid.mp3')})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(self.client.get('/videos/../../etc/passwd').status_code, 404)

    def test_real_conversions(self):
        for orientation, size in [('horizontal', (1280, 720)), ('vertical', (720, 1280))]:
            with self.subTest(orientation=orientation):
                data = {'audio': (io.BytesIO(self.audio.read_bytes()), 'music.wav'), 'title': "Canção: 100% d'amor", 'format': orientation}
                if orientation == 'vertical':
                    data['image'] = (io.BytesIO(self.image.read_bytes()), 'background.png')
                response = self.client.post('/api/videos', data=data)
                self.assertEqual(response.status_code, 202, response.json)
                job_id = response.json['id']
                deadline = time.monotonic() + 60
                while time.monotonic() < deadline:
                    job = self.client.get('/api/videos/' + job_id).json
                    if job['status'] in ('done', 'error'):
                        break
                    time.sleep(.2)
                self.assertEqual(job['status'], 'done', job)
                info = module.probe(module.DATA / job_id / 'video.mp4')
                video = next(s for s in info['streams'] if s['codec_type'] == 'video')
                self.assertEqual((video['width'], video['height']), size)
                self.assertEqual(video['codec_name'], 'h264')
                self.assertTrue(any(s['codec_type'] == 'audio' and s['codec_name'] == 'aac' for s in info['streams']))
                self.assertGreater(float(info['format']['duration']), .8)
                result = self.client.get('/videos/' + job_id + '?download=1')
                self.assertEqual(result.status_code, 200)
                self.assertIn('attachment', result.headers['Content-Disposition'])
                result.close()
                self.assertFalse((module.DATA / job_id / 'audio').exists())


if __name__ == '__main__':
    unittest.main()
