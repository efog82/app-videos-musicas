"""SQLite durable state and private server-side credential vault."""
import json
import os
import sqlite3
import threading
import time
import uuid
from pathlib import Path
from cryptography.fernet import Fernet


class Store:
    def __init__(self, root):
        self.root = Path(root).resolve()
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.files = self.root / 'files'
        self.files.mkdir(exist_ok=True, mode=0o700)
        key = self.root / 'vault.key'
        if not key.exists():
            fd = os.open(key, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'wb') as out:
                out.write(Fernet.generate_key())
        self.cipher = Fernet(key.read_bytes())
        self.lock = threading.RLock()
        self.db = sqlite3.connect(self.root / 'hub.db', check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript('''
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS records(id TEXT PRIMARY KEY, kind TEXT NOT NULL,
          project TEXT NOT NULL DEFAULT '', body TEXT NOT NULL, updated REAL NOT NULL);
        CREATE INDEX IF NOT EXISTS idx_kind ON records(kind,updated);
        CREATE TABLE IF NOT EXISTS secrets(id TEXT PRIMARY KEY, value BLOB NOT NULL);
        ''')
        self.db.commit()

    def put(self, kind, body, id=None, project=''):
        id = id or str(uuid.uuid4())
        with self.lock, self.db:
            self.db.execute('INSERT OR REPLACE INTO records VALUES(?,?,?,?,?)',
                            (id, kind, project, json.dumps(body, ensure_ascii=False), time.time()))
        return dict(id=id, kind=kind, project=project, **body)

    def get(self, id, kind=None):
        with self.lock:
            row = self.db.execute('SELECT * FROM records WHERE id=?', (id,)).fetchone()
        if not row or (kind and row['kind'] != kind):
            raise KeyError(id)
        return dict(json.loads(row['body']), id=row['id'], kind=row['kind'], project=row['project'])

    def list(self, kind, query='', project=None):
        with self.lock:
            rows = self.db.execute('SELECT * FROM records WHERE kind=? ORDER BY updated DESC', (kind,)).fetchall()
        return [dict(json.loads(r['body']), id=r['id'], kind=r['kind'], project=r['project'])
                for r in rows if query.casefold() in r['body'].casefold()
                and (project is None or r['project'] == project)]

    def delete(self, id):
        with self.lock, self.db:
            self.db.execute('DELETE FROM records WHERE id=?', (id,))
            self.db.execute('DELETE FROM secrets WHERE id=?', (id,))

    def secret(self, id, value=None):
        with self.lock, self.db:
            if value is not None:
                if value:
                    self.db.execute('INSERT OR REPLACE INTO secrets VALUES(?,?)',
                                    (id, self.cipher.encrypt(value.encode())))
                else:
                    self.db.execute('DELETE FROM secrets WHERE id=?', (id,))
            row = self.db.execute('SELECT value FROM secrets WHERE id=?', (id,)).fetchone()
        return self.cipher.decrypt(row[0]).decode() if row else ''

    def path(self, id):
        record = self.get(id, 'file')
        path = (self.files / record['disk']).resolve()
        if path.parent != self.files or not path.is_file():
            raise KeyError(id)
        return path

    def add_file(self, path, name=None, project=''):
        path = Path(path).resolve()
        if path.parent != self.files:
            raise ValueError('Ficheiro fora da biblioteca')
        return self.put('file', {'disk': path.name, 'name': name or path.name,
                                'size': path.stat().st_size}, project=project)
