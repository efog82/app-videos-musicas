# AI Hub Studio — estúdio multimédia v0.3.1

Cliente Android Kotlin/Compose + servidor PC Windows/Linux. A Sonora existente é preservada noutra pasta.

**Nova interface Chat/Imagens/Vídeos/Projetos, com geração direta e pré-visualização dentro da app antes de guardar na galeria.** O relatório separa os testes Android dos testes externos e das integrações experimentais. Não é uma declaração de que toda a plataforma original está concluída.

- [Configurar Wan 2.2 na v0.3.1](docs/CONFIGURACAO-v0.3.1.md)
- [Investigação, testes reais e limites da v0.3.1](docs/RELATORIO-v0.3.1.md)
- [Configurar a v0.3: chat, imagens e vídeos sem PC](docs/CONFIGURACAO-v0.3.md)
- [Relatório e limites da v0.3](docs/RELATORIO-v0.3.md)
- [Configuração do servidor e ferramentas anteriores](docs/CONFIGURACAO-v0.2.md)
- [Auditoria: causas, correções e dependências](docs/AUDITORIA-v0.2.md)
- [Integrações e limitações da plataforma](docs/INTEGRACOES.md)
- [Workflows ComfyUI](docs/WORKFLOWS.md)
- [Relatório da correção v0.2](docs/RELATORIO-v0.2.md)

## Download

[APK desta versão](https://github.com/efog82/app-videos-musicas/actions/runs/40362560726/artifacts/12669841200) · [Pacote Windows](https://github.com/efog82/app-videos-musicas/actions/runs/40362560726/artifacts/12668593007) · [Execução/testes](https://github.com/efog82/app-videos-musicas/actions/runs/40362560726)

No GitHub: **Actions → AI Hub Studio — APK e testes → execução da v0.3.1 → Artifacts**.

- **AI-Hub-Studio-APK**: ZIP com `AI-Hub-Studio-v0.3.1-debug.apk` e SHA256.
- **AI-Hub-Studio-PC-Windows**: ZIP com `Instalar-e-Iniciar.cmd`, servidor, scripts e documentação.
- Relatórios separados para servidor, Android, emulador e rede real.

É uma assinatura de desenvolvimento, não de produção. A v0.1.0 usava uma chave efémera do runner: para instalar esta atualização pode ser necessário exportar os dados que pretenda guardar e desinstalar a anterior. O novo CI guarda a chave debug em cache para melhorar continuidade; cache não substitui assinatura de produção e pode ser eliminado.

## Novo vídeo v0.3.1

Abra uma imagem → **Animar imagem** → **Hugging Face · Wan 2.2 I2V 14B** → comece com **2 s**. Geração anónima horizontal e vertical comprovada, sem PC nem chave. Duração superior depende da quota; o endpoint aceita até 5 s, mas essa duração não foi comprovada. O LTX anterior continua disponível para texto e imagem. CogVideoX e LTX 2.3 foram investigados e não são apresentados como operacionais.

## Caminho mais curto

Abra a engrenagem de definições, escolha uma IA, autorize envio e prima **Descobrir e testar IA**. Pollinations Classic permite pedidos públicos sem chave quando há quota; Gemini/Groq/OpenRouter/Mistral exigem chave própria de conta elegível. Não é necessário PC para essas ligações diretas. A app não distribui chaves nem ativa pagamentos.

Para ferramentas locais, descompacte o pacote PC, execute `Instalar-e-Iniciar.cmd`, faça o emparelhamento TLS/token e descubra os modelos em Modelos. Windows deve estar numa rede privada. Ollama, pesos, ComfyUI e nós têm instalações/licenças próprias; o script oferece opções explícitas. **127.0.0.1 no Android só é apropriado para um serviço no telemóvel ou para USB com adb reverse já ativo.**

O automático só considera modelos com resposta bem-sucedida recente. Uma lista de modelos, um HTTP 200 de health ou uma compilação aprovada não provam que a inferência funciona.

## Compilação e testes

Android: JDK 17, SDK 35, Gradle 8.10.2:

```sh
cd android
gradle :core:test :app:assembleDebug :app:lintDebug
```

O CI também instala o APK em emulador API 30, testa descoberta/pergunta real, persistência e TLS, e executa o fluxo de geração multimédia com serviços públicos. Os resultados e falhas estão no relatório; as provas reais não são substituídas por mocks. A cena inicial do teste multimédia é uma fixture identificada, separada do teste real de chat.

Servidor:

```sh
python -m pip install -r server/requirements.txt
python -m unittest discover -s server/tests -v
```

Testes com chaves reais usam `AIHUB_GEMINI_KEY`, `AIHUB_GROQ_KEY`, `AIHUB_OPENROUTER_KEY`, `AIHUB_MISTRAL_KEY` e exigem `AIHUB_TEST_FREE_PLAN=1`. Sem isso são **ignorados**, não aprovados. Podem selecionar o modelo com `AIHUB_GEMINI_MODEL`, etc. No GitHub use Secrets para chaves e a variável de confirmação da conta gratuita. Nunca publique valores.

`AIHUB_NETWORK_TESTS=1` ativa o teste real de socket HTTPS/token no Linux e Windows do CI. A descoberta mDNS e a firewall no computador físico ainda exigem ensaio Android–PC nessa rede; runners não reproduzem a rede doméstica.

Não há Gradle Wrapper binário incluído; Android Studio ou o CI instalam a versão Gradle indicada.
