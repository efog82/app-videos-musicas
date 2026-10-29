# AI Hub Studio v0.3.1 — vídeo

A interface e os dados da v0.3 são preservados. Instale a atualização sobre a versão anterior; não desinstale se quiser manter os dados privados.

## Animar uma imagem com Wan 2.2

1. Abra uma imagem na área Imagens e toque em **Animar imagem**.
2. No seletor Fornecedor, escolha **Hugging Face · Wan 2.2 I2V 14B**.
3. Descreva o movimento. Comece com **2 segundos** e autorize o envio ao Space escolhido.
4. Aguarde a fila. O vídeo aparece no leitor da app; **guardar na galeria é opcional**.

Também pode iniciar em Vídeos, escolher uma imagem existente em **Imagem de referência** e depois selecionar Wan. Sem referência, o seletor só mostra modelos que aceitam texto para vídeo. O LTX já existente mantém texto para vídeo e imagem para vídeo.

Wan 2.2 deste Space aceita apenas imagem para vídeo. O modelo é I2V A14B com Lightning LoRA, quantização FP8 e 4 passos na app. Não é o TI2V 5B. Não se garante maior qualidade para todos os pedidos nem consistência de personagens.

## Duração, formato e resolução

A API publica 0,5–5 segundos solicitados. O ensaio anónimo comprovou 2 s solicitados, com 33 fotogramas a 16 fps e duração real 2,0625 s. **Cinco segundos não foram comprovados nesta conta**: o pedido maior foi recusado por quota. A disponibilidade muda conforme a conta e a fila.

O Space calcula a resolução a partir da referência: ajusta dimensões entre 480 e 832 px, em múltiplos de 16; imagens quadradas são convertidas para 640×640. Pode recortar proporções extremas. Para vertical, escolha uma imagem vertical antes de animar; o endpoint não disponibiliza largura/altura independentes. O pedido testado na API usou 832×480. O teste Android confirmou saída vertical 480×832 e 2,063 s, com reprodução, exportação e persistência.

## Quotas e token gratuito

É possível acesso anónimo quando há quota. A app não cria contas, não compra créditos, não ativa PRO nem usa APIs pagas. Um token legítimo de uma conta gratuita Hugging Face pode permitir a quota associada à conta, sem garantir GPU ou duração específica.

Em **Fornecedores e definições**, preencha **Token Hugging Face (opcional)** e toque em **Guardar credenciais**. A chave fica protegida pelo Keystore. Não a coloque no código, em comentários, relatórios nem ficheiros públicos do GitHub.

A fila mostra a posição quando o Space a publica. Erros de quota, autenticação, tempo máximo de GPU e falta de GPU têm mensagens distintas. Não há repetição automática de gerações recusadas. Use a opção de novo pedido apenas depois de verificar o erro; escolher duração menor só ajuda se existir quota remanescente suficiente.

## Modelos investigados que não aparecem como operacionais

CogVideoX comunitário pediu uma reserva de GPU maior do que a permitida. O oficial também usa ZeroGPU; a configuração foi verificada, sem nova tentativa após a limitação conhecida. LTX 2.3 publicou API, mas o pedido real não obteve GPU. Wan 2.2 texto para vídeo estava com erro de runtime no candidato testado. Não foram substituídos por animações simuladas nem por links apresentados como funcionalidades.

Fontes: [Wan I2V](https://huggingface.co/spaces/zerogpu-aoti/wan2-2-fp8da-aoti-faster), [LTX 2.3](https://huggingface.co/spaces/Lightricks/LTX-2-3), [CogVideoX oficial](https://huggingface.co/spaces/zai-org/CogVideoX-5B-Space), [quotas ZeroGPU](https://huggingface.co/docs/hub/spaces-zerogpu).
