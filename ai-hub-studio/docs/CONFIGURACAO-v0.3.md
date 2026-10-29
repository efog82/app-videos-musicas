# Configuração v0.3

A navegação principal tem Chat, Imagens, Vídeos e Projetos. A engrenagem abre fornecedores, consentimento e tema claro/escuro. As ferramentas anteriores continuam em **Abrir ferramentas avançadas**.

## Conversar

1. Abra a engrenagem e autorize o envio dos pedidos aos fornecedores selecionados.
2. Escolha Pollinations Classic para acesso público sem chave, ou configure a sua chave gratuita Gemini, Groq, OpenRouter ou outro fornecedor listado.
3. Confirme que a sua conta tem acesso gratuito e use **Descobrir e testar IA**. Uma chave não garante quota.
4. Feche as definições, selecione o modelo que respondeu ao teste e envie a pergunta no Chat.

A app não ativa faturação nem fornece chaves partilhadas. OpenRouter fica limitado a modelos gratuitos. Outros planos exigem a confirmação do utilizador; confirme também os limites na conta do fornecedor. As chaves são guardadas no Android com proteção do Keystore, sem publicação no GitHub.

## Imagens sem computador

Em Imagens, escolha Pollinations Classic ou o Space oficial FLUX.1-schnell da Black Forest Labs, escreva a descrição e gere. Também pode usar **Gerar imagem** numa resposta do Chat. A imagem fica associada à conversa e ao projeto selecionado.

O resultado aparece dentro da aplicação. Toque para ampliar e fazer zoom. **Guardar na galeria é opcional**: a app conserva uma cópia privada para pré-visualização e histórico. Partilhar usa uma autorização temporária de leitura do ficheiro.

Pollinations Classic tem intervalo público de cerca de 45 segundos, resolução limitada, marca de água e disponibilidade variável. FLUX público depende da fila e da quota do Space; pode exigir autenticação ou ficar suspenso. Descrições e referências são enviadas ao serviço escolhido. Evite conteúdo confidencial em serviços públicos.

Cloudflare é opcional e experimental: requer token de Workers AI, Account ID e confirmação de plano Free. O plano gratuito anuncia 10 000 neurónios/dia; não ative Workers Paid. A app não muda de plano. A integração não foi testada com credenciais nesta entrega.

**Variar descrição** produz uma nova geração a partir do pedido; não é edição fiel dos píxeis nem garantia de personagem consistente. Apenas fornecedores que aceitem referência recebem uma imagem.

## Vídeos sem PC nem GPU local

Escolha o Space público LTX-Video Distilled. Para imagem para vídeo, abra uma imagem gerada e use **Animar imagem**; para texto para vídeo, use Vídeos. Escolha duração e dimensões suportadas e confirme o envio. O serviço pode ter fila, quota ou suspensão. A duração final segue os fotogramas aceites pelo modelo.

O vídeo concluído aparece no leitor integrado, antes de qualquer exportação para a galeria. Não é uma animação de zoom. Cancelar interrompe a espera local e tenta cancelar a tarefa quando a API o permite; a GPU remota pode continuar a trabalhar.

Uma falha não é convertida em vídeo simulado. Use a mensagem da tarefa e selecione outro fornecedor disponível. O Space LTX original está marcado como experimental; não tem a mesma validação do Distilled.

## Histórico e projetos

Conversas, pedidos, ficheiros privados e associações a projetos persistem na base local. Use a pesquisa do histórico para encontrar conversas e Projetos para agrupar conteúdos. Dados anteriores são preservados pelo esquema existente; não desinstale a app para atualizar se quiser conservá-los. Tarefas interrompidas pelo encerramento do processo exigem repetição manual para evitar duplicar pedidos externos.

## Ligações locais e ferramentas anteriores

Não são necessárias para Chat, Imagens e Vídeos públicos. Para Ollama, ComfyUI, FFmpeg, áudio e ferramentas locais existentes, consulte [a configuração v0.2](CONFIGURACAO-v0.2.md). Continuam a exigir os respetivos motores, modelos e emparelhamento seguro; a nova interface não transforma dependências locais em serviços gratuitos na nuvem.

## Fontes e limites

- Pollinations: https://github.com/pollinations/pollinations/blob/master/APIDOCS.md
- FLUX: https://huggingface.co/spaces/black-forest-labs/FLUX.1-schnell
- LTX: https://huggingface.co/spaces/Lightricks/ltx-video-distilled
- Quotas Spaces: https://huggingface.co/docs/hub/spaces-zerogpu
- Cloudflare: https://developers.cloudflare.com/workers-ai/platform/pricing/

OVHcloud foi avaliado, mas não integrado: não foi possível confirmar uma configuração elegível sem cartão para esta entrega. Gratuito não significa ilimitado, disponível permanentemente ou adequado a todas as utilizações comerciais; consulte as condições e licenças atuais.
