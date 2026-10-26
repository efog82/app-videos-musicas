# AI Hub Studio

Aplicação Android nativa Kotlin/Jetpack Compose e servidor PC Python. Este projeto é independente da Sonora existente na pasta `android` da raiz do repositório.

**Estado: implementação inicial em validação. Não corresponde ainda à totalidade da plataforma universal pedida.** Não contém pesos de IA, quotas gratuitas garantidas, nem interfaces que fingem produzir IA. Leia [funcionalidades e limites](docs/INTEGRACOES.md) e [testes](docs/TESTES.md).

## Obter o APK

No GitHub, abra **Actions → AI Hub Studio — APK e testes → execução concluída → Artifacts → AI-Hub-Studio-APK**. Descompacte o ZIP e instale `AI-Hub-Studio-v0.1.0-debug.apk`. O artefacto só existe se a compilação terminar com êxito; pode exigir sessão GitHub. É uma compilação de desenvolvimento, com assinatura de teste gerada pelo runner; futuras compilações podem exigir desinstalar a anterior. Não é uma versão de produção assinada. Não instale os APK da Sonora pensando que são o AI Hub.

## PC Windows

Requer Python 3.11+ e FFmpeg/ffprobe no PATH. Ollama, ComfyUI e pesos instalam-se separadamente, segundo os seus termos.

```powershell
cd ai-hub-studio
powershell -ExecutionPolicy Bypass -File server/start-windows.ps1
```

O script cria um ambiente virtual local, instala as dependências e arranca apenas em `127.0.0.1:8787`. O token é criado em `%USERPROFILE%\.aihub-studio\pairing-token.txt`. Copie-o para Definições na app. Não o envie a terceiros nem o publique. Os ficheiros, histórico e cofre ficam em `%USERPROFILE%\.aihub-studio`.

Linux/macOS:

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r server/requirements.txt
python -m server.launch
```

### Ligação Android–PC por USB

Ative a depuração USB no Android, instale Android Platform Tools no PC e autorize o computador no telemóvel:

```sh
adb reverse tcp:8787 tcp:8787
adb reverse tcp:11434 tcp:11434
```

Em **Definições**: servidor `http://127.0.0.1:8787`, token do ficheiro; prima **Guardar e testar**. Em **Modelos**, carregue predefinições, adicione Ollama e prima **Descobrir**. No Chat escolha um modelo. Instale previamente um modelo no Ollama (por exemplo `ollama pull qwen3:1.7b`, confirmando licença e requisitos atuais).

**Chat direto:** ative o modo direto Ollama nas Definições e guarde `http://127.0.0.1:11434`; não precisa do servidor AI Hub para conversar. Descubra modelos em Modelos. USB deve ter o redirecionamento 11434 ativo. As restantes ferramentas PC continuam a exigir o servidor AI Hub.

### Rede local com TLS

Não há pesquisa automática da rede: a associação é explícita por endereço e token. Use uma CA privada de confiança, um certificado com SAN correspondente ao IP privado do PC e instale apenas essa CA no Android. O cliente verifica TLS; não existe opção para ignorar erros de certificado.

```sh
python -m server.launch --host 192.168.1.20 --cert servidor-cert.pem --key servidor-key.pem
```

Permita a porta 8787 apenas na rede privada na firewall Windows. O launcher rejeita bind global, `0.0.0.0` e LAN sem TLS. Não configure port forwarding no router. O ficheiro `vault.key` protege o cofre em repouso, mas fica na mesma conta local: proteção de disco/conta e ACL Windows continuam necessárias. As permissões POSIX de ficheiros não substituem ACL Windows.

## Imagens e vídeo real com ComfyUI

Instale ComfyUI e os nós/pesos de um workflow compatível. Teste primeiro no próprio ComfyUI. Exporte o workflow em **API format** e crie um manifesto conforme [WORKFLOWS.md](docs/WORKFLOWS.md). Importe esse JSON em **Modelos**, adicione o endpoint ComfyUI `http://127.0.0.1:8188` e descubra os nós. Nos separadores Imagens/Vídeo selecione servidor e workflow. O resultado é obtido pela API `/prompt`, `/history` e `/view`, guardado na biblioteca e exportável pelo seletor de ficheiros Android.

Os controlos (prompt, seed, resolução, duração, imagem inicial/final, clip de entrada) só alteram campos explicitamente ligados no manifesto. Não existe conversão automática de parâmetros entre arquiteturas. Personagens consistentes, movimentos de membros, estilos, remoção de fundo, upscaling, Wan/LTX/AnimateDiff e música dependem dos workflows, nós e pesos; não são capacidades universalmente garantidas. Upload de imagem usa a API ComfyUI; áudio/vídeo de entrada depende de o loader aceitar ficheiros na pasta input.

## Edição e som

Importe ficheiros na biblioteca. No Editor selecione clips, início/duração e efeitos, organize a ordem, escolha música e SRT e exporte MP4. A edição usa FFmpeg no PC, 720p/1280 nas proporções disponíveis, H.264/AAC. O áudio original dos clips é removido; a faixa musical é a única faixa de áudio. O fade aplica entrada/saída por clip, não crossfade entre clips. O SRT requer FFmpeg com libass. A exportação não é vídeo generativo.

O editor SRT local e o TTS Android funcionam sem servidor; as vozes precisam de estar instaladas e podem ter requisitos de rede próprios. Análise de energia/BPM e normalização exigem FFmpeg. Transcrição exige `/audio/transcriptions` compatível e um modelo explicitamente selecionado. Não há reconhecimento garantido de letras cantadas.

## Custos e privacidade

Os pedidos remotos estão bloqueados sem consentimento. No modo automático, apenas modelos locais ou com preços OpenRouter todos a zero, descobertos há menos de uma hora, são aceites por defeito. Os preços podem mudar entre descoberta e execução; **só um limite de faturação no fornecedor impede cobranças com garantia**. Nunca use chaves com faturação aberta se pretende risco financeiro zero. Gemini, Groq e outros exigem confirmar o plano: não se presume gratuitidade do nome ou publicidade de uma quota. A opção explícita de permitir custos não confirmados pode produzir cobranças. O router não contorna quotas, autenticação ou condições.

As chaves de fornecedores são enviadas ao PC emparelhado e cifradas em repouso; não são devolvidas pela API. O token Android é cifrado com Keystore. A base SQLite de conteúdos não é cifrada. Backups Android estão desativados. Pedidos a serviços externos apenas ocorrem mediante configuração e consentimento. Workflows ComfyUI devem ser de fontes confiáveis: podem executar nós instalados no PC. Não importe workflows não revistos.

## Compilar e testar

JDK 17, Android SDK 35, Gradle 8.10.2:

```sh
cd android
gradle :core:test :app:assembleDebug :app:lintDebug
```

Pode abrir a pasta `ai-hub-studio/android` no Android Studio e usar a instalação Gradle adequada. Não existe Gradle Wrapper binário incluído; o CI instala a versão fixa através de setup-gradle.

```sh
# Na pasta ai-hub-studio
python -m unittest discover -s server/tests -v
```

A arquitetura separa o domínio portátil `:core`, o cliente `:app` e os módulos PC de armazenamento, fornecedores, media, workflows e tarefas. Ainda não existe cliente desktop/web.
