"""Real TCP/TLS server test, distinct from external-provider and ASGI contract tests."""
import os
import socket
import ssl
import tempfile
import threading
import time
import unittest
import httpx
import uvicorn
from server.app import create_app
from server.network import prepare


@unittest.skipUnless(os.environ.get('AIHUB_NETWORK_TESTS')=='1','Sockets não autorizados neste ambiente; executar em CI com AIHUB_NETWORK_TESTS=1')
class RealTlsTests(unittest.TestCase):
    def test_actual_https_server_authentication(self):
        with tempfile.TemporaryDirectory() as root:
            pairing,cert,key=prepare(root,'127.0.0.1')
            app=create_app(root,token=pairing['token'])
            sock=socket.socket();sock.bind(('127.0.0.1',0));sock.listen(128)
            port=sock.getsockname()[1]
            server=uvicorn.Server(uvicorn.Config(app,host='127.0.0.1',port=port,ssl_certfile=str(cert),ssl_keyfile=str(key),log_level='error'))
            thread=threading.Thread(target=lambda:server.run(sockets=[sock]),daemon=True);thread.start()
            try:
                end=time.monotonic()+15
                while not server.started and time.monotonic()<end:time.sleep(.05)
                self.assertTrue(server.started,'Servidor real não arrancou')
                trust=ssl.create_default_context(cafile=str(cert))
                with httpx.Client(base_url=f'https://127.0.0.1:{port}',verify=trust,trust_env=False,timeout=10) as client:
                    self.assertEqual(client.get('/health').json()['service'],'AI Hub Studio')
                    self.assertEqual(client.get('/v1/projects').status_code,401)
                    response=client.get('/v1/projects',headers={'Authorization':'Bearer '+pairing['token']})
                    self.assertEqual(response.status_code,200)
                print('REAL_TLS_PASS: HTTPS socket, certificate validation, 401 without token, 200 with token')
            finally:
                server.should_exit=True;thread.join(10);sock.close()
                app.state.jobs.pool.shutdown();app.state.store.db.close()
