# AI Hub Studio v0.3.1 — relatório de vídeo

Data: 26-10-2026. Interface principal, esquema da base local, chaves, chat, imagens, projetos e ferramentas avançadas preservados. Objetivo desta atualização: uma alternativa direta a LTX-Video Distilled, sem PC, ComfyUI, GPU local, cartão ou pagamentos.

## Investigação e pedidos reais

[Investigação inicial, execução 40360625607](https://github.com/efog82/app-videos-musicas/actions/runs/40360625607): metadata do runtime, APIs Gradio públicas, código publicado para limites e pedidos reais de geração. Não foram acedidos endpoints privados.

| Space/modelo | Resultado observado | Decisão |
|---|---|---|
| zerogpu-aoti/wan2-2-fp8da-aoti-faster — Wan 2.2 I2V A14B | API pública `/generate_video`. Pedido de 5 s/6 passos recusado: 105 s de GPU exigidos, 30 s restantes. Pedido de 2 s/4 passos gerou MP4 real. | Integração nativa nova, limitada às capacidades publicadas e à quota da conta |
| zerogpu-aoti/wan2-2-fp8da-aoti — Wan 2.2 texto→vídeo | Runtime em erro antes do envio | Não integrado |
| Wan-AI/Wan2.1 | Runtime em erro | Não acrescentado |
| kaidjuric/cogvideox-5b-text-to-video | API pública; pedido real recusado porque reservava 270 s de GPU, acima do máximo permitido | Não integrado; não se recomenda uma subscrição como requisito |
| zai-org/CogVideoX-5B-Space | API acessível; primeira preparação bloqueada por parâmetro não mapeado, sem inferência. Segunda investigação confirmou ZeroGPU, com reserva de 300 s no código publicado. | Não declarado funcional; não foi repetido o pedido em infraestruturas com limitação conhecida |
| Lightricks/LTX-2-3 — LTX 2.3 Distilled 1.1, 22B | `/generate_video` acessível. Pedido real de 5 s/832×480 falhou: nenhuma GPU disponível após 60 s | Não integrado como operacional; token de conta gratuita e disponibilidade podem permitir futuros ensaios |
| Fhwang/wan2-video-generation — Wan TI2V 5B | Runtime e API acessíveis; apenas descoberta, não houve geração | Não integrado sem prova de inferência; reserva publicada de 180 s, sujeita à elegibilidade da conta |
| Wan-AI/Wan2.2-S2V | API pública `/predict`, exige áudio e imagem; não é o percurso simples texto/imagem→vídeo desta atualização | Apenas investigado, sem geração nem integração |
| multimodalart/wan2-2-fast e wan2-2-fast-i2v | Metadata pública devolveu 401 | Não acedidos nem integrados |

Não se conclui que todos estes modelos sejam impossíveis de usar gratuitamente em qualquer conta: os resultados acima descrevem as condições efetivamente observadas. Uma conta/token gratuito tem quota própria e não garante GPU. Não foram disponibilizadas credenciais nesta sessão.

## Prova externa Wan

[Execução 40361490759](https://github.com/efog82/app-videos-musicas/actions/runs/40361490759): pedido público anónimo, imagem de entrada desenhada como fixture de teste, quatro passos, dois segundos solicitados. Não foram usados vídeos de exemplo, cache de demonstração, imagem animada por zoom nem resultados simulados.

O serviço devolveu o ficheiro remoto, que foi descarregado no artefacto `12667089574`. O passo de verificação do runner falhou por `ffprobe` não instalado; isso foi registado como falha de validação, não ocultado. O ficheiro original foi posteriormente validado com FFprobe neste ambiente, sem nova geração:

- Contentor MP4; codec H.264.
- Resolução: 832×480.
- 33 fotogramas, 16 fps, duração real 2,0625 s.
- 105 535 bytes.
- SHA-256 e metadados completos em [evidence-v031/wan-validation.json](evidence-v031/wan-validation.json) e [wan-public-video-ffprobe.json](evidence-v031/wan-public-video-ffprobe.json).

O workflow de investigação passou a instalar FFprobe e, por defeito, faz apenas descoberta, sem consumir quota de geração em cada commit. Gerações adicionais exigem seleção explícita do modo de teste. Falhas individuais ficam no JSON; workflow verde de investigação não significa que as gerações tenham passado.

## Integração Android

- Novo fornecedor: **Hugging Face · Wan 2.2 I2V 14B**, Space comunitário `zerogpu-aoti`.
- Imagem obrigatória; o modelo não é apresentado no modo sem referência. LTX anterior mantém texto→vídeo.
- Ação Animar imagem preservada; seleção de referência existente também disponível no formulário Vídeos.
- Duração publicada: 0,5–5 s solicitados. Valor inicial conservador: 2 s; somente esta duração tem prova externa nesta sessão. Não se promete acesso gratuito a 5 s.
- Dimensões inferidas da imagem pelo fornecedor, com ajuste/corte para 480–832 px; quadrado 640×640. Não são apresentados controlos de resolução que o endpoint não aceita.
- API publicada validada, upload legítimo, fila Gradio com posição quando publicada, cancelamento quando suportado, download HTTPS, verificação do contentor MP4 e descodificação Android.
- Resultado privado antes de exportar, leitor Media3, MediaStore, URI de partilha e associação persistente ao projeto.
- Duração e resolução reais guardadas e mostradas no cartão. Pedidos registam modelo, duração, seed e dimensões solicitadas para diagnóstico; repetição conserva fornecedor e duração quando válidos.
- HTML recebido em mensagens de erro é removido; quota, falta de GPU e reserva acima do máximo têm explicações distintas em português. Sem repetição automática para contornar recusas, alternativa paga ou compra de créditos.

## Qualidade e limites

Wan usa quantização FP8 e Lightning LoRA. É uma alternativa de modelo, não uma garantia de melhoria em todas as cenas. Não foram realizados estudos de preferência visual, ensaios com personagens recorrentes ou prova de consistência entre clips. A imagem desenhada valida o percurso imagem→vídeo e não representa uma avaliação de qualidade fotográfica. A orientação vertical tem ensaio Android separado do teste externo horizontal.

O processamento remoto exige rede e envio da referência/pedido ao Space escolhido. Limites são do fornecedor. Acesso gratuito não equivale a disponibilidade permanente, geração ilimitada ou garantia de uso comercial; consulte as licenças do modelo e LoRA.

## Fontes

- [Wan I2V: código e API públicos](https://huggingface.co/spaces/zerogpu-aoti/wan2-2-fp8da-aoti-faster)
- [Wan texto→vídeo: runtime](https://huggingface.co/spaces/zerogpu-aoti/wan2-2-fp8da-aoti)
- [LTX 2.3 oficial](https://huggingface.co/spaces/Lightricks/LTX-2-3)
- [CogVideoX oficial](https://huggingface.co/spaces/zai-org/CogVideoX-5B-Space)
- [CogVideoX comunitário](https://huggingface.co/spaces/kaidjuric/cogvideox-5b-text-to-video)
- [Condições ZeroGPU](https://huggingface.co/docs/hub/spaces-zerogpu)

## Validação Android final e entrega

Código executável: `0054142e8e986b15c2d2203b14bd599551bdd606`.
[Execução final 40362560726](https://github.com/efog82/app-videos-musicas/actions/runs/40362560726): quatro jobs aprovados. Emulador Android 11/API 30, **cinco testes instrumentados, zero falhas, zero erros, zero ignorados**:

| Teste real Android | Resultado |
|---|---|
| Chat público | Descoberta, pedido e resposta real persistida; 33,880 s |
| Multimédia anterior | FLUX e Pollinations, imagem no chat, LTX imagem→vídeo, primeiro fotograma no leitor, exportação e persistência; 15,156 s |
| Projetos | Contexto e dados preservados ao recriar Activity; 1,007 s |
| Android → PC TLS | Socket real, pin e autenticação; 2,563 s |
| **Novo Wan 2.2 I2V** | Escolha no formulário Animar imagem, pedido real, receção e reprodução de vídeo vertical, exportação e URI de partilha legível, projeto preservado ao recriar Activity; **15,658 s** |

Resultado Wan Android: **MP4, 480×832, 2 063 ms, 109 438 bytes**. Pré-visualização confirmada antes de exportar pela presença do leitor e evento real `onRenderedFirstFrame`. Ficheiro validado como MP4/descodificável. Não se usa uma captura de superfície de vídeo como prova de píxeis nem se declara ensaio em telemóvel físico.

Evidências: [wan-android-real.json](evidence-v031/wan-android-real.json) e [XML completo](evidence-v031/android-instrumented-tests.xml). A partilha é testada através da Intent e leitura do URI; não foram enviados conteúdos a outras pessoas.

Servidor Linux: 24 testes, 20 aprovados e 4 ignorados por falta de chaves Gemini/Groq/Mistral/OpenRouter. Windows: 24 testes, 19 aprovados e 5 ignorados (as mesmas chaves e FFmpeg ausente no runner Windows). A exportação MP4 real com FFmpeg passou no Linux. Testes lógicos do servidor com respostas controladas não contam como gerações externas reais.

**Seis testes JVM passaram**, incluindo modos de entrada/durações inválidas e regressões das mensagens reais de quota/GPU. São testes da lógica com fixtures identificadas, não gerações externas. Compilação, lint e assinatura v2 do APK aprovados.

APK `AI-Hub-Studio-v0.3.1-debug.apk`, 21 099 365 bytes.
SHA-256: `402162c0b107c6bb03f4b7f0793ec37cfa604536ef47a9ae6e342681483fc826`.
Certificado SHA-256: `9f2c433083b8383e49c1da36b28adaa758c60122f1d410f2025efce77875d23b`, igual à v0.3 entregue. A atualização mantém a base local sem migração destrutiva. É uma assinatura de desenvolvimento, não uma distribuição assinada para produção.

[APK no GitHub Actions](https://github.com/efog82/app-videos-musicas/actions/runs/40362560726/artifacts/12669841200).

**Concluído e comprovado:** novo Wan imagem→vídeo direto e preservação dos fluxos existentes cobertos pelos testes. **Não concluído:** Wan texto→vídeo, CogVideoX ou LTX 2.3 operacionais, clips Wan de cinco segundos nesta conta, avaliação comparativa de qualidade e consistência de personagens. Essas limitações não foram escondidas por simulações.
