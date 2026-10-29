# AI Hub Studio v0.3.3 — diagnóstico e validação multimédia

Versão Android: 0.3.3, versionCode 6, pacote `pt.aihub.studio`. Base: v0.3.2, commit `c56a05d908300b2b72d4094d0bd7e215e568a55a`. Trabalho limitado aos módulos multimédia, testes e entrega. Não foi criada uma aplicação nova.

## A. Cloudflare: diagnóstico técnico

Endpoint: `POST https://api.cloudflare.com/client/v4/accounts/<Account ID>/ai/run/<modelo>`, com Bearer token e Content-Type application/json. O Account ID e o token não são incluídos nos diagnósticos.

Modelos existentes preservados:
- `@cf/black-forest-labs/flux-1-schnell`.
- `@cf/stabilityai/stable-diffusion-xl-base-1.0`.

A v0.3.2 enviava prompt, seed e steps=4 ao FLUX. A documentação declara prompt e steps, máximo 8 e predefinição 4. A própria página inclui exemplos com seed, mas não o declara no esquema de parâmetros. Esta inconsistência **não comprova** que seed causava as imagens abstratas. O novo pedido FLUX envia apenas prompt e steps; permite 4 ou 8, com 8 por defeito. Mais passos podem melhorar resultados, mas também consomem mais recursos e não garantem qualidade.

O SDXL mantém parâmetros próprios: prompt, seed, width/height (256–2048), num_steps=20, guidance=7.5. Não recebe o campo steps do FLUX. Nenhum endpoint de imagem de referência novo foi anunciado para a Cloudflare sem geração real comprovada.

A resposta FLUX JSON é extraída de result.image, descodificada em Base64 e validada como imagem. A resposta binária SDXL é validada pelo tipo e pelo descodificador Android. success=false, ausência de imagem, HTML e Base64 inválido são rejeitados. Os bytes da imagem recebida são guardados sem recompressão. SHA-256 compara imagem descodificada e ficheiro privado; o hash do envelope HTTP é registado separadamente.

O diagnóstico privado inclui modelo pedido, modelo presente no URL, parâmetros enviados, HTTP, Content-Type, dimensões, MIME e hashes. A API não confirma necessariamente um identificador de modelo executado: o relatório assinala modelReportedByApi=null; não inventa essa confirmação. Pode partilhar o último diagnóstico nas definições; inclui o prompt, sem credenciais.

**Causa visual: por comprovar.** Não existem credenciais Cloudflare Workers Free neste ambiente/CI. Não foram executadas as seis gerações maçã/galáxia/paisagem × 4/8 passos. A correção de esquema e os testes de integridade não provam que o fornecedor deixará de gerar imagens abstratas. O script reproduzível está em scripts/audit_media_033.py e não anuncia sucesso só por HTTP 200.

## B. Correções e melhorias

- Esquemas separados por modelo Cloudflare; FLUX deixa de enviar seed não declarado.
- Controlo de 4/8 passos FLUX; sem controlos fictícios de resolução/proporção neste endpoint.
- Validação de imagem e hashes, com diagnóstico privado partilhável.
- Estados persistentes por fornecedor/modelo: disponível, quota reduzida/esgotada, em espera, indisponível, credenciais recusadas, modelo indisponível e desconhecido.
- Retry-After suporta segundos e datas HTTP. RateLimit, RateLimit-Policy e cabeçalhos X-RateLimit reconhecidos sem adivinhar janelas.
- Cloudflare 3036 (quota diária) é distinguido de 3040 (falta de capacidade), 5035 (modelo exige plano pago), 401/403 e 404.
- Erros ZeroGPU por SSE são registados, mesmo quando o transporte HTTP foi 200.
- Quota ZeroGPU partilhada entre Spaces e quota diária Cloudflare partilhada entre os seus modelos são respeitadas.
- O contador usa relógio local, sem chamadas periódicas à API. Ao expirar o prazo, o estado passa a desconhecido; não promete desbloqueio.
- Contagem local de gerações concluídas explicitamente separada do consumo total da conta.
- Tokens Hugging Face só são utilizados para inferência após confirmar utilizador Free pela API oficial whoami-v2, uma vez por token/sessão do cliente. PRO/organizações são recusados para evitar créditos automáticos; o modo anónimo continua disponível.
- Z-Image Turbo integrado com 9 passos e parâmetros da API publicada; sem substituir fornecedores anteriores.

## C–E. Modelos investigados e integração

| Família / serviço | Evidência e resultado | Decisão |
| --- | --- | --- |
| Z-Image Turbo, mrfakename/Z-Image-Turbo | API pública /generate_image e geração real PNG 512×512. Imagem inspecionada: maçã vermelha sobre madeira, coerente com o pedido. Teste Android próprio. | **Nova integração** hf-zimage. |
| Qwen/Qwen-Image | /infer acessível; geração real recusada pelo ZeroGPU com mensagem de quota e prazo. | Não integrado como funcional. |
| Qwen Image Lightning, Space comunitário consultado | O endereço consultado devolveu 401. Isto não comprova inexistência de todos os outros Spaces. | Não integrado; nenhum contorno de autenticação. |
| FLUX.1 Schnell oficial Hugging Face | Integração anterior preservada. 4 passos; só parâmetros publicados são enviados. | Mantido. |
| Cloudflare FLUX / SDXL | Documentação e esquema verificados; sem credenciais para geração real nesta revisão. | Mantidos, sem afirmação de qualidade visual resolvida. |
| Stable Diffusion / SDXL e outros Workers AI | Famílias presentes no catálogo; existir modelo aberto ou preço por unidade não comprova acesso gratuito operacional da conta. | Não acrescentados sem teste real. |
| Pollinations Classic | Integração anónima existente, com limites e disponibilidade variáveis. | Mantida; sem alternativa paga automática. |
| Wan 2.2 I2V | Integração existente preservada: imagem obrigatória, 4 passos, 16 fps, API permite 0,5–5 s; prova histórica de 2 s. | Mantida, sem promessa de clips longos ou consistência de personagens. |
| LTX Distilled | Texto/vídeo e imagem/vídeo já implementados; limites publicados preservados. | Mantido. |
| Lightricks/LTX-2-3 | Space RUNNING, /generate_video publicado; isso não demonstra GPU disponível. Gerações anteriores tinham falhado por capacidade/quota. | Não acrescentado como funcional. |
| CogVideoX-5B oficial | Space RUNNING, /generate publicado. Inferência pesada já tinha encontrado limites de GPU na investigação anterior. | Não acrescentado; nenhuma nova geração válida comprovada. |
| HunyuanVideo | Código/modelos abertos não garantem API gratuita. Space comunitário multimodalart/Hunyuan-Video-1-5 RUNNING, /generate acessível. Código publicado exige imagem, fixa 854×480/16:9, exporta 24 fps e reserva 120 s de GPU. Não foi comprovado novo vídeo válido; estas capacidades não se aplicam automaticamente a outros modelos Hunyuan. | Não integrado como funcional. |
| HF Inference Providers | A documentação consultada anuncia **nenhum crédito mensal para Free**; exige compra de créditos ou PRO. É diferente de Spaces públicos ZeroGPU. | Não utilizado como fornecedor gratuito. |

Nenhum novo fornecedor de vídeo é declarado operacional nesta revisão. Preservação de identidade, cenas cinematográficas ou fidelidade de referência não são garantidas por estes endpoints.

Z-Image: texto → imagem, sem referência. 9 passos (8 forwards), guidance=0 no servidor. Dimensões publicadas 512–2048 em múltiplos de 64; a aplicação limita a 1024 por lado para controlar consumo. 1:1, 4:5, 9:16 e 16:9 são aproximados aos múltiplos suportados, sem esticar uma imagem recebida. A resolução é solicitada, não uma garantia do fornecedor.

## F. Quotas e reposição

| Fornecedor | Regra documentada / dados utilizados | Limitação |
| --- | --- | --- |
| Cloudflare Workers Free | 10 000 neurónios/dia; reposição documentada às 00:00 UTC. Em erro diário 3036, a próxima meia-noite é mostrada como **previsão**, salvo prazo oficial específico. | Neurónios não são número de imagens. Outros pedidos da conta consomem quota. O prazo não garante desbloqueio imediato. |
| HTTP rate limits | Retry-After, RateLimit t/r, RateLimit-Policy w e X-RateLimit em formatos conhecidos. | Um 429 genérico não é convertido em quota diária. Uma janela só é nomeada quando informada. |
| HF Spaces ZeroGPU | A documentação consultada indica quota diária de GPU por identidade: anónimo 2 min, Free 5 min, reposição 24 h após primeiro uso global. Mensagens oficiais Try again in HH:MM:SS são usadas quando devolvidas. | A app não conhece o primeiro uso global noutras aplicações e não inventa uma data. Segundos de GPU reportados não são gerações restantes. |
| Pollinations | Respeita espera pública e Retry-After; mantém a limitação anterior da integração Classic. | Sem saldo global conhecido ou data de reposição garantida. |
| Mensal / outros | Só apresenta dados oficiais reconhecidos. | Sem dados: **Reposição desconhecida**. Não presume dia 1 nem 30 dias. |

Horários: Europe/Lisbon, com hora de verão/inverno automática. O fim de um prazo provoca estado desconhecido até existir nova evidência; não há sondagem constante da API nem repetição de gerações para consultar saldo.

## G. Testes e evidência

A investigação pública inicial está no workflow [40412690962](https://github.com/efog82/app-videos-musicas/actions/runs/40412690962). O PNG Z-Image inicial tem 303 345 bytes, 512×512, SHA-256 5ab8d4f859aa6338812bec230872a164b06cec323b0a68ae3b1e7a273cd45d18. O Qwen respondeu com erro real de quota; isso não é um sucesso de geração.

As verificações finais, o commit e o APK são identificados na secção de entrega abaixo. Os testes determinísticos de Cloudflare usam imagens de teste desenhadas localmente: verificam os mesmos bytes/pixels, Base64, JSON e erros. **Não são gerações reais Cloudflare.** Os testes do relógio e quotas também usam respostas de teste claramente identificadas. Só os testes marcados REAL executam inferência pública.

Limitações: sem teste num telemóvel físico nem comparação visual de gerações Cloudflare desta conta. A disponibilidade de Spaces pode mudar após o teste. Guardar/partilhar verifica o leitor e os bytes do URI; não envia mensagens a terceiros.

## H. Ficheiros e instalação

Código alterado:
- android/app/src/main/java/pt/aihub/studio/OnlineMedia.kt, WebTransport.kt, CloudflareImages.kt.
- MediaStatusStore.kt, MediaStatusUi.kt, MediaProviders.kt.
- StudioModel.kt, MediaUi.kt e apenas a área multimédia de ProviderSettings.kt.
- android/core/src/main/kotlin/pt/aihub/core/MediaQuota.kt, CloudflareImageRequest.kt e respetivos testes.
- android/app/src/androidTest/java/pt/aihub/studio/MediaAuditTest.kt, ZImageLiveTest.kt.
- android/app/build.gradle.kts, scripts CI, workflows e documentação.

Chat, DirectAi, MainActivity, tipografia, Android Keystore, esquema SQLite, projetos, editor e servidor não foram reformulados. Nenhum fornecedor anterior foi eliminado. Os únicos novos registos SQLite são observações/contagens de multimédia; não existe limpeza/migração destrutiva.

Compilar: JDK 17, Android SDK 35 e Gradle 8.10.2; em ai-hub-studio/android executar `gradle :core:test :app:assembleDebug :app:lintDebug`. Para atualizar uma instalação anterior é indispensável a mesma chave de assinatura; o APK CI verifica o certificado de desenvolvimento anterior. A compilação local com uma chave diferente não instala por cima.

Instalar o APK v0.3.3 por cima da app existente, sem desinstalar. Em Imagens, escolher Hugging Face · Z-Image Turbo e autorizar envio. O modo anónimo não precisa de PC nem chave; o servidor pode limitar a quota. Cloudflare mantém Account ID, token e confirmação de Workers Free. Não ativar Paid nem créditos.

Investigação reproduzível: `pip install httpx gradio_client pillow`; `python scripts/audit_media_033.py` consulta apenas metadados por defeito. `AIHUB_AUDIT_GENERATE=true` permite os pedidos reais limitados. O diagnóstico Cloudflare necessita de AIHUB_CF_ACCOUNT, AIHUB_CF_TOKEN e AIHUB_CF_FREE=true, sem imprimir credenciais; é interrompido na primeira falha, sem insistir na quota.

## Fontes primárias consultadas

- [Cloudflare FLUX: parâmetros e saída](https://developers.cloudflare.com/workers-ai/models/flux-1-schnell/).
- [Cloudflare SDXL: parâmetros específicos](https://developers.cloudflare.com/workers-ai/models/stable-diffusion-xl-base-1.0/).
- [Cloudflare: quotas, plano Free e reposição](https://developers.cloudflare.com/workers-ai/platform/pricing/).
- [Cloudflare: códigos de erro](https://developers.cloudflare.com/workers-ai/platform/errors/).
- [Hugging Face: preços dos Inference Providers](https://huggingface.co/docs/inference-providers/pricing).
- [Hugging Face: quotas e reposição ZeroGPU](https://huggingface.co/docs/hub/spaces-zerogpu).
- [Z-Image Turbo: código público do Space](https://huggingface.co/spaces/mrfakename/Z-Image-Turbo/blob/main/app.py).
- [Qwen Image: código público](https://huggingface.co/spaces/Qwen/Qwen-Image/blob/main/app.py).
- [HunyuanVideo: código público do Space investigado](https://huggingface.co/spaces/multimodalart/Hunyuan-Video-1-5/blob/main/app.py).

As condições dos fornecedores podem mudar; a documentação não substitui uma geração real da conta do utilizador.

## Entrega final e resultados — 27-10-2026

- Commit executável/testado: `c5ab923b19930b3583a7f740482d3468deb3f346`.
- [Workflow final: sete jobs concluídos com sucesso](https://github.com/efog82/app-videos-musicas/actions/runs/40477222494).
- **14 testes JVM**, zero falhas/erros; assembleDebug e lintDebug aprovados.
- **12 testes Android**: 10 no Android 11 (chat/multimédia, TLS PC, projetos, biblioteca e dois testes determinísticos novos), 1 Photo Picker/ícone no Android 15 e 1 geração Z-Image real no Android 11. Nenhum destes é anunciado como geração real Cloudflare.
- A bateria Android existente voltou a exercitar chat Pollinations, imagem Pollinations/FLUX, vídeo LTX e Wan, pré-visualização, exportação, URI de partilha e persistência. O teste Z-Image executou uma geração direta pela API, recebeu um PNG 512×512, abriu pré-visualização antes de exportar e verificou os mesmos bytes na galeria e no URI de partilha, além do histórico e estado persistente.
- Linux: 24 testes, 20 aprovados e 4 ignorados por falta de credenciais. Windows: 24 testes, 19 aprovados e 5 ignorados por credenciais/FFmpeg não instalado nesse runner. Ignorados não são contados como integrações reais comprovadas.
- A conta Free/PRO Hugging Face é validada pelo código, mas **não foi possível exercitar essa verificação com tokens reais**, porque esta revisão continuou sem credenciais. As gerações Hugging Face dos testes foram anónimas.
- Os testes Android compilaram e instalaram a mesma revisão do código em emuladores; a assinatura do APK de entrega foi verificada separadamente. Não houve teste num dispositivo físico.
- APK: `AI-Hub-Studio-v0.3.3-debug.apk`, 21,155,339 bytes.
- SHA-256 do APK, divulgado pelo GitHub na publicação: `29b0fe1397b486c15c566386dae5692abce6090ae083a69cabd2e818328ff133`.
- Certificado SHA-256: `9f2c433083b8383e49c1da36b28adaa758c60122f1d410f2025efce77875d23b`, confirmado por apksigner no workflow; igual aos APKs entregues anteriormente.
- Package/version confirmados pelo aapt: `pt.aihub.studio`, versionCode 6, versionName 0.3.3.
- [Descarregar APK diretamente](https://github.com/efog82/app-videos-musicas/releases/download/aihub-v0.3.3/AI-Hub-Studio-v0.3.3-debug.apk).
- [Publicação com assinatura, checksum e arquivo do código](https://github.com/efog82/app-videos-musicas/releases/tag/aihub-v0.3.3).

Artefactos finais:
- [AI-Hub-Studio-testes-emulador](https://github.com/efog82/app-videos-musicas/actions/runs/40477222494/artifacts/12721915423)
- [AI-Hub-Studio-relatorios-Android](https://github.com/efog82/app-videos-musicas/actions/runs/40477222494/artifacts/12721691543)
- [AI-Hub-Studio-APK](https://github.com/efog82/app-videos-musicas/actions/runs/40477222494/artifacts/12721578187)
- [AI-Hub-Studio-PC-Windows](https://github.com/efog82/app-videos-musicas/actions/runs/40477222494/artifacts/12721098128)
- [AI-Hub-ZImage-Android-real](https://github.com/efog82/app-videos-musicas/actions/runs/40477222494/artifacts/12720833746)
- [AI-Hub-Studio-relatorio-servidor](https://github.com/efog82/app-videos-musicas/actions/runs/40477222494/artifacts/12720593205)
- [AI-Hub-Studio-PhotoPicker-Android15](https://github.com/efog82/app-videos-musicas/actions/runs/40477222494/artifacts/12720499628)

Descoberta adicional sem geração: [workflow 40476788130](https://github.com/efog82/app-videos-musicas/actions/runs/40476788130). HunyuanVideo, LTX 2.3 e CogVideoX publicaram APIs; nenhum foi acrescentado como novo vídeo funcional sem ficheiro válido comprovado.

A comparação de código desde a v0.3.2 encontrou 21 ficheiros acrescentados/alterados, nenhum removido e nenhuma alteração fora de ai-hub-studio e dos seus workflows. MainActivity (incluindo apresentação do Chat), DirectAi, LocalData/Keystore, LegacyActivity, servidor, ícones e Sonora foram preservados. A documentação acrescentada depois destes testes não altera o APK publicado.
