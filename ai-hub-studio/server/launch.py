"""Local-only by default. For LAN require a TLS certificate and private bind address."""
import argparse
import ipaddress
import os
import secrets
from pathlib import Path
import uvicorn
from .network import prepare, Announcement, lan_addresses
from .app import create_app


def main():
    parser = argparse.ArgumentParser(description='Servidor PC AI Hub Studio')
    parser.add_argument('--host',default='127.0.0.1')
    parser.add_argument('--port',default=8787,type=int)
    parser.add_argument('--lan',action='store_true',help='Preparar TLS, emparelhamento e descoberta LAN automaticamente')
    parser.add_argument('--cert'); parser.add_argument('--key')
    parser.add_argument('--data',default=str(Path.home()/'.aihub-studio'))
    args = parser.parse_args()
    pairing=None
    if args.lan:
        if args.host=='127.0.0.1':
            addresses=lan_addresses()
            if not addresses: parser.error('Sem IPv4 privado ativo; ligue o PC ao Wi-Fi ou indique --host')
            args.host=addresses[0]
        pairing,cert,key=prepare(args.data,args.host,args.port)
        args.cert,args.key=str(cert),str(key)
    ip = ipaddress.ip_address(args.host)
    if ip.is_unspecified or ip.is_global or ip.is_multicast or ip.is_link_local:
        parser.error('Use apenas 127.0.0.1 ou o IP privado específico do PC')
    if not ip.is_loopback and not (args.cert and args.key):
        parser.error('A ligação LAN exige TLS: indique --cert e --key. Não exponha este servidor à Internet.')
    data = Path(args.data); data.mkdir(parents=True,exist_ok=True,mode=0o700)
    token_file = data/'pairing-token.txt'
    if not token_file.exists():
        with os.fdopen(os.open(token_file,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as f:
            f.write(secrets.token_urlsafe(32))
    print('Token de emparelhamento guardado em:',token_file)
    print('Adicione o token às Definições Android. Nunca o publique no GitHub.')
    app = create_app(args.data,token_file.read_text().strip())
    if not app.state.store.list('provider'):
        app.state.store.put('provider',{'name':'Ollama no PC','type':'ollama','url':'http://127.0.0.1:11434','local':True,'enabled':True})
        app.state.store.put('provider',{'name':'ComfyUI no PC','type':'comfy','url':'http://127.0.0.1:8188','local':True,'enabled':True})
    announcement=None
    if pairing:
        print('URL Android:',pairing['url'])
        print('Impressão digital TLS (compare no Android):',pairing['fingerprint'])
        print('Abra o ficheiro emparelhamento.json na pasta de dados para copiar URL, token e impressão digital.')
        try:
            announcement=Announcement(args.host,args.port,pairing['fingerprint'])
            print('Descoberta mDNS ativa: _aihub._tcp.local.')
        except Exception as exc:
            print('mDNS indisponível; pode introduzir o IP manualmente:',type(exc).__name__)
    try:
        uvicorn.run(app,host=args.host,port=args.port,ssl_certfile=args.cert,ssl_keyfile=args.key,access_log=False)
    finally:
        if announcement: announcement.close()


if __name__ == '__main__':
    main()
