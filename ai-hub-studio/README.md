# AI Hub Studio — estúdio multimédia v0.3.4

Cliente Android Kotlin/Compose + servidor PC Windows/Linux. A Sonora existente é preservada noutra pasta.

**A v0.3.4 renova a interface, permite eliminar relatórios de pedidos terminados e corrige as resoluções pequenas do SDXL Cloudflare.** A preparação visual opcional usa a IA de chat configurada e apresenta uma proposta para revisão. Mantém Chat, Imagens, Vídeos, Projetos, dados, credenciais e fornecedores existentes.

- [Diagnóstico, alterações, testes e instalação v0.3.4](docs/RELATORIO-v0.3.4.md)
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

[Descarregar APK v0.3.4](https://github.com/efog82/app-videos-musicas/releases/download/aihub-v0.3.4/AI-Hub-Studio-v0.3.4-debug.apk) · [Publicação, assinatura e SHA-256](https://github.com/efog82/app-videos-musicas/releases/tag/aihub-v0.3.4)

Android `pt.aihub.studio`, versionCode 7. É um APK de desenvolvimento, com a mesma assinatura dos APKs entregues desde a v0.2. Instale por cima da versão anterior, sem desinstalar, para preservar dados e credenciais. O CI confirma o certificado antes de publicar; uma compilação local com outra chave não instala por cima.

Na área Imagens ou Vídeos, abra **Pedidos e diagnósticos** para consultar/apagar relatórios. As criações são independentes desses registos. Na Cloudflare, o SDXL usa tamanhos adequados próximos de 1 MP; HTTP 200 ou hashes iguais não comprovam qualidade visual. [Leia o diagnóstico e as limitações](docs/RELATORIO-v0.3.4.md).

Para compilar: JDK 17, Android SDK 35 e Gradle 8.10.2; em `ai-hub-studio/android`, executar `gradle :core:test :app:assembleDebug :app:lintDebug`. O servidor PC e as ferramentas anteriores continuam opcionais para os fornecedores online.


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
