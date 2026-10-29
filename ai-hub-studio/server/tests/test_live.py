"""Real external integrations. Never mocked. Explicitly skipped without credentials/consent."""
import asyncio
import os
import tempfile
import unittest
from server.storage import Store
from server.providers import Router,PRESETS


class LiveKeyedTests(unittest.TestCase):
    def exercise(self,name,key_var):
        key=os.environ.get(key_var,'')
        if not key: self.skipTest('Credencial não configurada: '+key_var)
        if os.environ.get('AIHUB_TEST_FREE_PLAN')!='1':self.skipTest('Confirme uma conta sem faturação com AIHUB_TEST_FREE_PLAN=1')
        preset=next(p for p in PRESETS if p['name']==name)
        async def work():
            with tempfile.TemporaryDirectory() as tmp:
                store=Store(tmp)
                try:
                    p=store.put('provider',dict(preset,enabled=True,free_plan=name!='OpenRouter'))
                    store.secret(p['id'],key)
                    router=Router(store)
                    catalog=await router.discover(p)
                    models=catalog['models']
                    preferred=os.environ.get(key_var.replace('_KEY','_MODEL'))
                    selected=next((m for m in models if (m['id']==preferred if preferred else (m['free'] if name=='OpenRouter' else True))),None)
                    self.assertIsNotNone(selected,'Sem modelo elegível; defina o MODEL explicitamente')
                    response=await router.chat({'messages':[{'role':'user','content':'Quanto é 2+2? Responde apenas 4.'}],
                        'candidates':[{'provider':p['id'],'model':selected['id']}],'remote_consent':True,'allow_metered':False})
                    self.assertIn('4',response['text'])
                    print('REAL_PROVIDER_PASS:',name,selected['id'],'resposta:',response['text'][:100])
                finally:store.db.close()
        asyncio.run(work())

    def test_real_gemini(self):self.exercise('Google Gemini','AIHUB_GEMINI_KEY')
    def test_real_groq(self):self.exercise('Groq','AIHUB_GROQ_KEY')
    def test_real_openrouter(self):self.exercise('OpenRouter','AIHUB_OPENROUTER_KEY')
    def test_real_mistral(self):self.exercise('Mistral','AIHUB_MISTRAL_KEY')
