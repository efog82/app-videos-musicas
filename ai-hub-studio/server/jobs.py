"""Persistent bounded single-worker queue. Interrupted tasks are never billed/retried silently."""
import concurrent.futures
import threading
import time
import uuid


class Jobs:
    def __init__(self, store):
        self.store = store
        self.pool = concurrent.futures.ThreadPoolExecutor(max_workers=1, thread_name_prefix='aihub')
        self.futures = {}
        self.lock = threading.RLock()
        for task in store.list('job'):
            if task['status'] in ('queued','running'):
                self.update(task['id'],status='interrupted', error='Servidor reiniciado; repita manualmente se desejar')

    def update(self, id, **fields):
        with self.lock:
            body = self.store.get(id,'job')
            body.update(fields)
            for k in ('id','kind','project'):
                body.pop(k,None)
            return self.store.put('job',body,id=id)

    def submit(self, kind, fn):
        with self.lock:
            active = [x for x in self.store.list('job') if x['status'] in ('queued','running')]
            if len(active) >= 20:
                raise ValueError('Fila cheia (20 tarefas); aguarde a conclusão')
            id = str(uuid.uuid4())
            self.store.put('job',{'task':kind, 'status':'queued','progress':0,'created':time.time()},id=id)
            def work():
                self.update(id,status='running')
                try:
                    result = fn(lambda p:self.update(id,progress=p))
                    self.update(id,status='done',progress=1,result=result)
                except Exception as exc:
                    # Keep exceptions concise; provider adapters strip upstream secrets.
                    self.update(id,status='failed',error=str(exc)[:1500])
            self.futures[id] = self.pool.submit(work)
            return self.store.get(id)

    def cancel(self,id):
        with self.lock:
            f = self.futures.get(id)
            if f and f.cancel():
                return self.update(id,status='cancelled')
            raise ValueError('Só é possível cancelar tarefas ainda em espera')
