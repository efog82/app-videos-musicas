# Relatório de validação v0.3 — 26-10-2026

## O que esta atualização implementa

Nova navegação Chat/Imagens/Vídeos/Projetos, Material 3 com fonte sem serifa e temas claro/escuro; mensagens Markdown, histórico pesquisável, copiar/partilhar/regenerar e cancelamento local. Anexos dependem da capacidade e autenticação do fornecedor.

Imagens e vídeos são descarregados uma vez para armazenamento privado, validados com descodificação Android e associados à conversa/projeto. Há imagem dentro do chat, ampliação e zoom, leitor Media3 e exportação opcional para MediaStore. A pré-visualização não exige guardar na galeria. Ações contextuais permitem resposta/cena → imagem → animar imagem. As ferramentas anteriores e o servidor PC permanecem disponíveis nas definições avançadas.

## Prova externa com serviços reais

[Execução 40325446213](https://github.com/efog82/app-videos-musicas/actions/runs/40325446213), script `scripts/probe_free_media.py`, anónimo e sem GPU local:

| Fornecedor | Pedido | Resultado |
|---|---|---|
| Pollinations Classic | Texto → imagem | JPEG real, 48 875 bytes |
| Space oficial FLUX.1-schnell | `/infer` | WebP real |
| Space oficial LTX Distilled | `/text_to_video` | MP4 real |
| Space oficial LTX Distilled | `/image_to_video` com imagem gerada | MP4 real, H.264, 448×256, 33 fotogramas, 1,1 s |

Metadados: [EVIDENCIA-API-v0.3.json](EVIDENCIA-API-v0.3.json). O probe usou o mesmo nome de destino para os dois vídeos: o artefacto conserva o vídeo imagem→vídeo, enquanto o registo contém os dois resultados e caminhos remotos distintos. Estes testes de API não substituem a validação Android.

## Problemas encontrados e corrigidos

- Sintaxe Compose e colisão de setter Kotlin impediram compilações iniciais: corrigidas.
- Carregamento de miniaturas sinalizado pelo lint: usa agora coroutine IO e estado explícito.
- Resultado associado à conversa fora da área visível: deslocamento atualizado quando chegam os conteúdos; teste confirma visualização com deslocamento normal da conversa.
- Pollinations devolveu HTTP 503 por limite público numa execução Android: não contado como geração bem-sucedida. Repetição limitada do mesmo GET com espera mínima de 46 s e respeito de Retry-After; sem troca para API paga.

- O adaptador Gradio simplificado devolveu erro 404 dentro do SSE no Android. Foi substituído pelo protocolo de fila do cliente oficial (`queue/join`, `queue/data`, função identificada pelo ID publicado), incluindo posição da fila e resultado final. Na execução 40337017860, a imagem FLUX foi gerada e descarregada pelo Android; o teste parou depois na identificação do cartão na árvore de acessibilidade. O identificador foi colocado no cartão interativo e o teste passou a exigir também confirmação do carregamento da imagem por Coil. A execução final 40338404664 passou os quatro testes Android.

## Limites e dependências

Cloudflare Workers AI: adaptador implementado e experimental, sem teste de geração por falta de token e conta Free. O Space LTX original é experimental; apenas o Distilled tem prova externa. OVHcloud não foi integrado por falta de confirmação de condições elegíveis sem cartão. Edição fiel por referência e personagens consistentes não são prometidas pelos adaptadores de texto para imagem.

Gemini, Groq, OpenRouter, Mistral e outros fornecedores autenticados exigem chaves e quota próprias. Testes com chaves ausentes são ignorados, não aprovados. Acesso público pode exigir login no futuro, ter filas, limites ou suspensão. Não há garantia de geração ilimitada nem de disponibilidade permanente.

As ferramentas legadas de edição, áudio, agentes e motores locais foram preservadas; não foram todas revalidadas com serviços externos nesta atualização. Motores PC continuam a exigir instalação e modelos compatíveis. A app não converte todas as funções originais em funcionalidades completas na nuvem.

Partilha é validada através da Intent e do acesso real ao URI; não se enviam conteúdos a pessoas durante os testes. O storyboard/cena da prova multimédia é uma fixture explicitamente identificada; a inferência de chat tem um teste real separado.

## Validação Android final

Código executável: `2ca03c6005abb61463f0bfccdf24d3eb6e25cc78`. [Execução 40338404664](https://github.com/efog82/app-videos-musicas/actions/runs/40338404664): os quatro jobs passaram. APK instalado em emulador Android 11/API 30; **4 testes instrumentados, 0 falhas, 0 erros, 0 ignorados**.

| Teste instrumentado | Resultado |
|---|---|
| Descoberta e pergunta ao fornecedor público | Resposta real e persistência; 34,755 s |
| Multimédia real | FLUX via seletor, imagem no chat carregada por Coil, zoom em ecrã completo, exportação MediaStore e URI de partilha; animação LTX por referência, evento real de primeiro fotograma do Media3, exportação MP4; Pollinations real; associação de três resultados ao projeto e persistência após recriar Activity; 22,030 s |
| Projeto local | Contexto e dados persistem após recriação; 1,643 s |
| Android → servidor PC | Socket HTTPS real, pin TLS e autenticação; 2,614 s |

A captura automática do leitor não preservou os píxeis da superfície de vídeo no emulador; não é usada como prova visual. A reprodução é verificada pelo evento `onRenderedFirstFrame`, pela presença do leitor e pelo ficheiro descodificável/exportado. Não foi realizado ensaio num telemóvel físico nesta sessão.

Compilação `assembleDebug`, testes `core:test`, lint e verificação da assinatura passaram. Servidor Linux: 24 testes, 20 aprovados e 4 ignorados por falta de chaves. Windows: 24 testes, 19 aprovados e 5 ignorados (as mesmas quatro chaves e FFmpeg ausente no runner Windows); exportação FFmpeg real passou no Linux. Testes do servidor com respostas controladas validam a lógica de falhas/segurança e não são contados como gerações externas reais.

APK: `AI-Hub-Studio-v0.3.0-debug.apk`, 21 082 981 bytes.
SHA-256: `bd903077c2aa64077de1b023881360133886dae3e05a4b6dbba1733126481ad3`.
Assinatura de desenvolvimento verificada; certificado coincide com o APK v0.2 entregue (`9f2c433083b8383e49c1da36b28adaa758c60122f1d410f2025efce77875d23b`), permitindo atualização sem desinstalar. Continua a ser uma distribuição debug, não uma assinatura de produção.
