"""Private-LAN TLS identity, pairing and mDNS. Never publishes the pairing token."""
import datetime
import hashlib
import ipaddress
import json
import os
import secrets
import socket
from pathlib import Path
import psutil
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID


def lan_addresses():
    result=[]
    try:
        interfaces=psutil.net_if_addrs(); stats=psutil.net_if_stats()
    except OSError:
        return []
    for name,addresses in interfaces.items():
        if not stats.get(name) or not stats[name].isup:
            continue
        for address in addresses:
            if address.family==socket.AF_INET:
                ip=ipaddress.ip_address(address.address)
                if ip.is_private and not (ip.is_loopback or ip.is_link_local or ip.is_unspecified):
                    result.append(address.address)
    return sorted(set(result))


def prepare(root, host, port=8787):
    root=Path(root); root.mkdir(parents=True,exist_ok=True,mode=0o700)
    cert_path=root/'server-cert.pem'; key_path=root/'server-key.pem'
    hosts=sorted(set(lan_addresses()+[host,'127.0.0.1']))
    renew=not cert_path.exists() or not key_path.exists()
    if not renew:
        cert=x509.load_pem_x509_certificate(cert_path.read_bytes())
        valid_ips=cert.extensions.get_extension_for_class(x509.SubjectAlternativeName).value.get_values_for_type(x509.IPAddress)
        renew=ipaddress.ip_address(host) not in valid_ips or cert.not_valid_after_utc < datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=7)
    if renew:
        key=rsa.generate_private_key(public_exponent=65537,key_size=2048)
        subject=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'AI Hub Studio PC')])
        now=datetime.datetime.now(datetime.timezone.utc)
        cert=(x509.CertificateBuilder().subject_name(subject).issuer_name(subject).public_key(key.public_key())
              .serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(minutes=5))
              .not_valid_after(now+datetime.timedelta(days=365))
              .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address(h)) for h in hosts]),critical=False)
              .sign(key,hashes.SHA256()))
        key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
        os.chmod(key_path,0o600)
        cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    token_path=root/'pairing-token.txt'
    if not token_path.exists():
        with os.fdopen(os.open(token_path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as f:
            f.write(secrets.token_urlsafe(32))
    fingerprint=cert.fingerprint(hashes.SHA256()).hex()
    pairing={'url':f'https://{host}:{port}','fingerprint':fingerprint,'token':token_path.read_text().strip()}
    file=root/'emparelhamento.json'; file.write_text(json.dumps(pairing,indent=2),encoding='utf-8');os.chmod(file,0o600)
    return pairing,cert_path,key_path


class Announcement:
    def __init__(self,host,port,fingerprint):
        from zeroconf import Zeroconf,ServiceInfo,IPVersion
        self.zc=Zeroconf(interfaces=[host],ip_version=IPVersion.V4Only)
        self.info=ServiceInfo('_aihub._tcp.local.',f'AI Hub {socket.gethostname()}._aihub._tcp.local.',
                              addresses=[socket.inet_aton(host)],port=port,
                              properties={'tls':'1','fp':fingerprint,'version':'0.2.0'})
        self.zc.register_service(self.info,allow_name_change=True)
    def close(self):
        self.zc.unregister_service(self.info);self.zc.close()
