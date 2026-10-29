# Auditoria funcional e correções v0.2.0

## Causas encontradas na v0.1.0

1. O Android propunha `127.0.0.1:8787` por defeito. É o telemóvel, não o PC; só funcionava com `adb reverse` já configurado. A interface não guiava essa preparação.
2. O servidor Python arrancava apenas em loopback, sem descoberta LAN, certificado automático ou regras de firewall. A LAN requeria configuração TLS manual e CA instalada.
3. Os fornecedores de nuvem só estavam ligados através da API do servidor PC. Não existia fluxo Android autónomo com chaves. O ficheiro DirectAi iniciado posteriormente nem sequer estava ligado ao APK entregue.
4. O catálogo de modelos era confundido com autorização de inferência. Não havia teste de resposta antes de considerar um modelo no modo automático.
5. Erros de rede/autenticação/quotas eram pouco acionáveis. Os controlos multimédia dependiam do mesmo servidor não configurado, por isso também falhavam.
6. Agentes era apenas um planeador determinístico, identificado como tal, sem execução por um modelo.

## Correções

- Android inicia em Configuração, sem endereço PC inventado. As APIs da nuvem são chamadas diretamente, com chaves próprias no Keystore.
- API Classic Pollinations como opção sem chave; disponibilidade e quotas não garantidas. Não há pagamentos x402 nem carteiras implementados.
- Descoberta e teste de resposta para o fornecedor escolhido. Automático restringe-se a respostas bem-sucedidas recentes e remove falhas. A disponibilidade futura nunca pode ser garantida por uma observação passada.
- Gemini nativo, OpenAI-compatible (Groq, OpenRouter, Mistral, HF, Cerebras, SambaNova), Ollama e LM Studio. OpenRouter filtra todos os preços publicados a zero. Nos restantes, a confirmação de conta gratuita sem faturação é necessária; a API não revela universalmente o estado de faturação. Desative faturação no próprio fornecedor.
- Bridge PC HTTPS com certificado e SAN para IP privado, token, pin SHA256 por servidor, anúncio `_aihub._tcp.local.` e introdução manual se multicast não funciona. O anúncio mDNS não constitui autenticação: compare a impressão digital com o PC antes de guardar o token.
- Windows: instalação via winget/Python venv, FFmpeg, preparação de TLS, permissões da pasta privada, instruções e regras de firewall limitadas à sub-rede/perfil privado. Instalação de Ollama e download de modelo são opções explícitas. ComfyUI usa instalador oficial e pesos/nós adequados ao hardware.
- Diagnósticos: DNS, ligação recusada, timeout/firewall, certificado, token/chave, modelo/endpoint, quota e serviço indisponível. `/v1/diagnostics` testa as APIs locais separadamente.
- Ferramentas existentes preservadas: ComfyUI imagem/vídeo/áudio, biblioteca, edição FFmpeg, transcrição, SRT, TTS, projetos e plano de montagem. A ligação PC é partilhada com TLS/token por todas estas ferramentas. Imagem pública adicional pode ser guardada no Android e enviada para o editor PC.
- Agente textual novo: planeamento por LLM e até três etapas de redação, código ou storyboard. Não executa código, pesquisa web ou montagens que não tenham ocorrido. O planeador PC anterior permanece disponível.

## Dependências reais

| Modo | Necessário | Desnecessário |
|---|---|---|
| Público sem chave | Internet, consentimento, serviço público disponível e quota por IP | PC, chave, carteira ou cartão |
| Gemini/Groq/Mistral/etc. | Internet, conta elegível, chave própria com permissões e plano gratuito confirmado | Servidor AI Hub PC |
| OpenRouter gratuito | Internet, chave, modelo cujo catálogo tenha todos os preços a zero, quota | PC; compras de créditos não são impostas |
| Ollama direto USB | Ollama+modelo no PC, ADB, reverse da porta 11434, USB autorizado | Servidor AI Hub |
| PC Wi-Fi | Mesma LAN sem isolamento de clientes, servidor aberto, IP privado/8787, TLS/pin/token, firewall privada | Exposição Internet, port-forward, HTTP sem proteção |
| Imagem/vídeo/áudio ComfyUI | Bridge PC + ComfyUI/8188, workflow API correto, nós, pesos/licenças e hardware | Um workflow não instala automaticamente pesos |
| Editor/normalização | Bridge PC + FFmpeg/ffprobe no PATH; libass para SRT queimado | Modelos generativos para uma montagem normal |
| TTS/SRT/projetos locais | Motor/voz Android para TTS; armazenamento local | PC, chave para SRT/projetos |

## O que a infraestrutura não resolve sozinha

Não cria contas/chaves em nome do utilizador; não instala modelos de vídeo pesados num Radeon integrado presumindo compatibilidade; não oferece vídeo generativo gratuito ilimitado; não contorna limites; não transforma ferramentas de navegador em APIs não autorizadas. O editor continua sem multipista completa/keyframes/partículas, conforme documentação anterior. A conectividade LAN real em Windows e telemóvel físicos requer validação nesse equipamento.
