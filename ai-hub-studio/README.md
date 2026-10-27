# AI Hub Studio — estúdio multimédia v0.3.3

Cliente Android Kotlin/Compose + servidor PC Windows/Linux. A Sonora existente é preservada noutra pasta.

**A v0.3.3 preserva a interface Chat/Imagens/Vídeos/Projetos e o Chat. Acrescenta Z-Image Turbo, diagnóstico Cloudflare e estados/quotas multimédia com reposição quando conhecida.** O relatório separa os testes Android dos testes externos e das integrações experimentais. Não é uma declaração de que toda a plataforma original está concluída.

- [Diagnóstico Cloudflare, modelos, quotas, testes e instalação v0.3.3](docs/RELATORIO-v0.3.3.md)
- [Novidades e utilização da v0.3.2](docs/ALTERACOES-v0.3.2.md)
- [Testes e entrega da v0.3.2](docs/RELATORIO-v0.3.2.md)
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

[Descarregar APK v0.3.3](https://github.com/efog82/app-videos-musicas/releases/download/aihub-v0.3.3/AI-Hub-Studio-v0.3.3-debug.apk) · [Versão, assinatura e SHA-256](https://github.com/efog82/app-videos-musicas/releases/tag/aihub-v0.3.3)

É um APK de desenvolvimento (versionCode 6), com a mesma assinatura dos APKs v0.2/v0.3/v0.3.1/v0.3.2 entregues. O CI verifica o certificado antes de publicar. Instale por cima sem desinstalar para preservar dados; uma compilação local com outra chave não atualiza a instalação anterior. O APK v0.1 tinha uma assinatura efémera diferente.

A publicação só ocorre após testes JVM, lint, emuladores Android (incluindo gerações reais), servidor e scripts Windows passarem. Os artefactos da execução incluem também o pacote Windows preservado e os relatórios. Cloudflare sem credenciais continua **sem qualidade visual comprovada**; consultar o relatório antes de interpretar um HTTP 200 como sucesso.


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
