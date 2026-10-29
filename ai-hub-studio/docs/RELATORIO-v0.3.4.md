# AI Hub Studio v0.3.4 — qualidade SDXL, pedidos e interface

Base: v0.3.3, commit `07ff913522cc6c6e22deeaf295976aceb189acb2`. Android `pt.aihub.studio`, versionName 0.3.4, versionCode 7. As correções e respetivos limites foram explicados antes de alterar a geração Cloudflare.

## Diagnóstico: comprovado e por verificar

Os valores fornecidos pelo utilizador foram: modelo solicitado `@cf/stabilityai/stable-diffusion-xl-base-1.0`, HTTP 200, PNG 288×512, hashes iguais, qualidade não avaliada. Foram recebidos esses valores no chat; o JSON completo e a imagem não estavam disponíveis para inspeção independente nesta revisão.

| Questão | Evidência | Conclusão |
| --- | --- | --- |
| Modelo solicitado | No código, o modelo selecionado é validado e usado diretamente em `/accounts/<conta>/ai/run/<modelo>`. O diagnóstico identifica SDXL. | O endpoint corresponde ao SDXL pedido. Não existe substituição silenciosa por outro fornecedor. A API não identifica independentemente os pesos internos executados; HTTP 200 não os comprova. |
| Descodificação | SDXL devolve imagem binária; FLUX pode devolver JSON/Base64. A aplicação valida a imagem e guarda os mesmos bytes. O utilizador reporta hashes iguais. | Não há evidência de corrupção pela cópia. Os testes de descodificação verificam bytes e píxeis com ficheiros de teste. Não provam qualidade semântica de uma geração Cloudflare. |
| Resolução | O controlo antigo de 512 px em 9:16 produzia exatamente 288×512. | Problema confirmado: é aceite pela API, mas o lado de 288 px está abaixo da recomendação técnica do SDXL base. Não é apenas um problema de apresentação. |
| Parâmetros | A v0.3.3 envia num_steps=20 e guidance=7.5 ao SDXL. A documentação Cloudflare limita os passos a 20. | São parâmetros compatíveis. Não se aumentou para 50, apesar de exemplos Diffusers usarem outros valores, porque o endpoint Cloudflare não os suporta. |
| Texto longo | A ação Gerar imagem de uma resposta do chat copiava todo o conteúdo para a descrição. | Uma resposta longa podia ser enviada sem adaptação. O exemplo da galáxia dado pelo utilizador já é curto: este problema não explica esse exemplo por si só. |
| FLUX abstrato | O utilizador refere resultados abstratos nos dois modelos. Não foi recebido um diagnóstico FLUX com imagem para comparação. | A causa do FLUX permanece por confirmar. O endpoint não publica width/height; a correção de dimensões do SDXL não é apresentada como correção do FLUX. |

A documentação Diffusers recomenda 1024×1024 para SDXL e desaconselha dimensões abaixo de 512 px. A resolução é uma causa plausível e tecnicamente fundamentada da má qualidade SDXL, mas não foi demonstrado que explica, sozinha, todos os resultados abstratos. O serviço Cloudflare, o modelo base sem refiner, os seus limites de passos e a interpretação do prompt continuam a ser fatores possíveis.

## Correções de geração

- **SDXL Cloudflare:** resolução de geração ajustada a tamanhos próximos de um megapíxel; não é upscaling de uma imagem pequena. A imagem recebida não é esticada, cortada nem recomprimida pela correção.
- 1:1: **1024×1024**; 4:5 aproximado: **896×1152**; 9:16 aproximado: **768×1344**; 16:9 aproximado: **1344×768**. As proporções são identificadas como aproximadas aos tamanhos do modelo.
- O formulário mostra as dimensões exatas antes do envio. A proteção na construção do pedido também corrige os tamanhos antigos insuficientes (incluindo 288×512), abrangendo pedidos do chat e novas tentativas.
- Pedidos explícitos com dimensões já adequadas são preservados. Os limites oficiais 256–2048 são validados. Outros fornecedores e as resoluções de vídeo mantêm o comportamento anterior.
- Mantidos **20 passos e guidance 7,5** no SDXL. O FLUX mantém apenas prompt/steps (4 ou 8); não recebe parâmetros de SDXL não suportados.
- Aumentar a resolução pode consumir mais quota. Não existe ativação de planos, compras, subscrições ou alternativa paga automática.
- Corrigida a validação de Base64 com quebras de linha/espaços legítimos. Era uma falha de robustez que podia rejeitar uma resposta válida; não é apresentada como causa de imagens abstratas já descodificadas.

## Preparar uma descrição visual

O botão **Preparar descrição visual com IA** usa o fornecedor/modelo de chat já configurado e as mesmas proteções de acesso gratuito. É uma chamada real, separada da conversa, que consome a quota desse fornecedor. Exige autorização de envio e uma IA de chat configurada; a descrição manual continua disponível.

A instrução pede uma única cena, em inglês, normalmente 40–60 palavras: sujeito, composição, ambiente, iluminação, cor e estilo; em vídeo, também movimento/câmara. Pede remover comentários do chat, listas de cenas e explicações. Não cria uma tradução ou descrição fictícia localmente sob o nome de IA.

A proposta aparece para **revisão e edição**. O utilizador escolhe Usar descrição ou Manter original e pode Repor texto original. A aplicação não gera imagens automaticamente ao preparar o texto e não substitui as mensagens/histórico do chat. Textos longos têm aviso; respostas excessivamente longas da preparação são recusadas sem truncagem silenciosa. Erros surgem no próprio formulário.

Uma descrição de exemplo disponível na comparação Cloudflare é uma galáxia espiral sintética inspirada no estilo infravermelho do James Webb, com braços, núcleo, poeira e estrelas identificáveis. Isso não transforma uma imagem IA numa observação científica real nem garante fidelidade astronómica.

## Diagnóstico acessível por imagem

Os botões Guardar, Partilhar, Variar descrição, Animar imagem, Projeto e Eliminar foram preservados. Nas imagens Cloudflare há agora **Diagnóstico desta imagem**, também na pré-visualização.

O diagnóstico mostra modelo no endpoint, dimensões recebidas, MIME, HTTP, passos e prompt efetivamente enviado. O utilizador pode partilhar um ZIP com `diagnostico.json` e o ficheiro `original.*`; os bytes são comparados com o hash antes de preparar o ZIP. O ZIP inclui a descrição, não as credenciais. Só é aberto o seletor Android quando o utilizador pede partilha.

As novas imagens guardam a referência ao diagnóstico. Imagens v0.3.3 procuram o diagnóstico privado existente pelo hash. Imagens anteriores sem diagnóstico apresentam uma explicação; continuam disponíveis para guardar/partilhar normalmente. Mantém-se também o botão antigo Partilhar último diagnóstico Cloudflare nas definições.

Em Definições → Cloudflare, **Comparar qualidade Cloudflare · 3 pedidos** pede confirmação antes de gerar FLUX 4 passos, FLUX 8 passos e SDXL 1024×1024, com o mesmo prompt curto (galáxia ou maçã). Necessita de credenciais guardadas, consentimento e confirmação Workers Free. Consome até três gerações da quota gratuita; os resultados e os erros reais aparecem nas áreas existentes. Um bloqueio de quota já conhecido é respeitado, sem insistir no serviço. A comparação não é executada automaticamente ao abrir o ecrã.

## Eliminar relatórios de pedidos

- O ícone do caixote do lixo aparece em cada relatório terminado: falhado, cancelado, interrompido ou concluído.
- A confirmação explica que elimina apenas o registo do pedido. Imagens, vídeos, conversas, projetos, configurações e ficheiros da galeria pessoal são mantidos.
- Pedidos em fila/processamento têm Cancelar; só se pode eliminar o registo depois de terminar o cancelamento. A proteção existe também na base de dados, não apenas no botão.
- A eliminação é persistente. Um callback de progresso atrasado não pode voltar a mudar um pedido terminado para Em curso.
- O mesmo botão está disponível nos relatórios apresentados no chat.

## Interface renovada

Preservadas as quatro áreas Chat, Imagens, Vídeos e Projetos, as ações multimédia, Markdown, anexos e ferramentas avançadas. Novo tema claro/escuro azul/violeta, tipografia legível, cartões e ações de criação com rótulos. O cabeçalho criativo fica compacto quando existem resultados, dando espaço às pré-visualizações. As capturas em ecrã estreito revelaram um título cortado e o botão Animar imagem esticado; o cabeçalho foi encurtado e as ações da pré-visualização passaram a duas linhas com um botão Animar imagem de largura completa. As capturas finais foram revistas visualmente.

**Pedidos e diagnósticos** tem acesso na barra superior e nas galerias com histórico. Filtros Todos, Em curso, Falhados e Concluídos. Pedidos ativos continuam visíveis na galeria; relatórios antigos ficam organizados no histórico, em vez de taparem os resultados. Detalhes extensos podem ser expandidos. O formulário organiza seleção de IA, descrição e formato; mantém os controlos existentes e mostra os limites detalhados quando solicitados.

## Preservação

Sem mudança de applicationId, esquema/versão da base de dados, nomes das credenciais, Android Keystore ou formatos dos ficheiros existentes. Nenhum fornecedor/modelo foi removido. A API de chat DirectAi, o corpo Markdown, Photo Picker, editor/ferramentas avançadas, servidores e Sonora foram preservados. As mensagens do chat não são reescritas pela preparação visual.

## Verificação e limites

Os resultados finais, revisão executável, workflow, APK e checksum são registados na secção Entrega, após os testes. Testes com respostas/ficheiros locais são identificados como fixtures e não como gerações reais. Os testes existentes exercitam serviços reais quando as condições públicas o permitem.

Não existem credenciais Cloudflare neste ambiente. A qualidade visual Cloudflare com as novas dimensões **não foi comprovada nesta revisão**. A alteração corrige a resolução inadequada demonstrada e disponibiliza comparação/diagnóstico reais na conta do utilizador. HTTP 200, compilação e hashes coincidentes não substituem essa validação visual.

## Fontes primárias

- [SDXL: dimensões e recomendações Diffusers](https://huggingface.co/docs/diffusers/api/pipelines/stable_diffusion/stable_diffusion_xl).
- [SDXL: modelo oficial Stability AI](https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0).
- [Cloudflare SDXL: esquema de parâmetros e limites](https://developers.cloudflare.com/workers-ai/models/stable-diffusion-xl-base-1.0/).
- [Cloudflare FLUX Schnell: parâmetros e Base64](https://developers.cloudflare.com/workers-ai/models/flux-1-schnell/).

## Primeira execução nativa e indisponibilidade externa

Na revisão executável `44150fc6d2d334a0b739e7d512b1185bc4399336`, os 18 testes JVM, lint, APK, Z-Image Android, Photo Picker Android 15, servidor e Windows passaram. A primeira bateria Android 11 executou 15 casos: 13 passaram e 2 falharam devido a HTTP 503 do Pollinations Classic, que informou capacidade esgotada e Retry-After de 10 segundos. Não foram falhas de descodificação ou perda de ficheiros.

Todos os cinco testes novos de relatórios/dimensões/ZIP/cancelamento passaram. A bateria também confirmou FLUX, LTX e Wan antes de encontrar a indisponibilidade Pollinations. O teste de chat chegou a receber a resposta à pergunta 2+2, mas o pedido adicional de preparação visual falhou com o 503; esse pedido não é anunciado como uma preparação real concluída. Foi repetida apenas a bateria afetada, sem alterar o código nem contornar o limite; o resultado final é registado em Entrega.

Na repetição dessa bateria, chat, preparação visual, Pollinations, FLUX, LTX e Wan passaram. O único erro foi no seletor do teste novo: o texto Pollinations Classic existia no formulário e numa legenda de imagem da galeria. O seletor foi limitado ao formulário. Esta correção alterou apenas o teste, sem alterar o APK; uma nova execução confirma o conjunto após a correção. A evidência real da preparação visual está em evidence-v034/visual-description-real.json.

## Entrega final — 29-10-2026

- Revisão final: `76a20a2e4e6fd802589d3b0a2cb4d47a43dd5505`.
- Código executável sem alterações desde `44150fc6d2d334a0b739e7d512b1185bc4399336`; o último commit corrige apenas o seletor do teste. O APK publicado é byte a byte igual ao validado localmente dessa revisão.
- [Workflow final](https://github.com/efog82/app-videos-musicas/actions/runs/40857693697): **sete jobs aprovados**, incluindo publicação.
- **18 testes JVM**, sem falhas/erros, assembleDebug e lintDebug aprovados.
- **17 testes Android** aprovados, sem falhas/erros/ignorados: 15 no Android 11, 1 geração Z-Image real no Android 11 e 1 Photo Picker/ícone no Android 15.
- Chat, preparação visual por API real (com conversa preservada), Pollinations imagem, FLUX, LTX e Wan verificados nos serviços públicos. A disponibilidade futura depende das quotas e do serviço.
- Verificadas pré-visualizações antes de exportar, reprodução de vídeo com primeiro frame, galeria, URI de partilha, persistência, projetos, originais importados e as cinco novas verificações de relatórios/SDXL/ZIP/cancelamento.
- Servidor Linux: 24 casos, 20 aprovados/4 ignorados por credenciais. Windows: 24 casos, 19 aprovados/5 ignorados por credenciais e FFmpeg. Casos ignorados não são contabilizados como integrações reais comprovadas.
- Cloudflare: a resolução/payload, a descodificação, a interface e o ZIP foram testados; **não foi feita geração Cloudflare real nesta revisão** por falta de acesso à conta/credenciais. A melhoria visual de uma galáxia e os problemas FLUX continuam por confirmar numa geração real.
- Sem teste num telemóvel físico. As instalações e interações foram testadas em emuladores; o certificado do APK de entrega foi verificado separadamente.

[Descarregar APK diretamente](https://github.com/efog82/app-videos-musicas/releases/download/aihub-v0.3.4/AI-Hub-Studio-v0.3.4-debug.apk) · [Publicação, assinatura e checksum](https://github.com/efog82/app-videos-musicas/releases/tag/aihub-v0.3.4)

APK: `AI-Hub-Studio-v0.3.4-debug.apk`, 21 220 875 bytes. Pacote `pt.aihub.studio`, versionCode 7, versionName 0.3.4.

SHA-256: `9b73f3876290ae8157b140fae9b1a6768c876f29d3639f5e340bbef727378a63`.

Certificado SHA-256 confirmado por apksigner: `9f2c433083b8383e49c1da36b28adaa758c60122f1d410f2025efce77875d23b`, o mesmo dos APKs anteriores entregues desde a v0.2. A assinatura é de desenvolvimento.

Instale por cima da aplicação existente, sem desinstalar. Para a tua galáxia: Imagens → Criar imagem → Cloudflare → SDXL → escolha o formato e confirme o tamanho mostrado (768×1344 no vertical). Pode preparar/rever uma descrição visual antes de gerar. Se continuar abstrata, abra o resultado → Diagnóstico desta imagem → Partilhar original + diagnóstico; isso permitirá comparar o ficheiro real com o pedido, sem partilhar chaves.

Para compilar: JDK 17, SDK Android 35, Gradle 8.10.2; em `ai-hub-studio/android`: `gradle :core:test :app:assembleDebug :app:lintDebug`. Uma compilação local com chave diferente não atualiza o APK instalado. O workflow conserva a chave de desenvolvimento em cache e verifica o certificado antes de publicar.

Ficheiros principais alterados: MainActivity/StudioTheme/WorkspaceUi (apresentação), MediaUi/MediaStatusUi (formulário, cartões e limites), GenerationHistory/StudioModel (relatórios e preparação), SdxlQuality/CloudflareImageRequest (dimensões), CloudflareImages/CloudflareDiagnosticsUi/OnlineMedia (diagnóstico por resultado e Base64), testes e workflow. Sem remoção de fornecedores, modelos, dados ou ficheiros Sonora.
