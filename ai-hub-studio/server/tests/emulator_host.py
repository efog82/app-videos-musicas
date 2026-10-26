"""CI companion host, reachable as 10.0.2.2 from the actual Android emulator."""
import json
from pathlib import Path
import uvicorn
from server.network import prepare
from server.app import create_app

if __name__=='__main__':
    root=Path.cwd()/'.ci-pc'
    pairing,cert,key=prepare(root,'10.0.2.2')
    (Path.cwd()/'android'/'ci-pairing.json').write_text(json.dumps(pairing))
    uvicorn.run(create_app(root,token=pairing['token']),host='127.0.0.1',port=8787,ssl_certfile=str(cert),ssl_keyfile=str(key),log_level='error',access_log=False)
