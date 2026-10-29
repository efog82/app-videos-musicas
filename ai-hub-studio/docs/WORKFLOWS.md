# Manifestos ComfyUI

Formato JSON aceite pelo importador Android (ou POST `/v1/workflows`):

```json
{
  "name": "O meu workflow aprovado",
  "modality": "video",
  "requirements": "Indique pesos, nós, licença, VRAM e modo (texto-vídeo, imagem-vídeo, etc.)",
  "workflow": {
    "6": { "class_type": "CLIPTextEncode", "inputs": { "text": "", "clip": ["4", 1] } }
  },
  "bindings": { "prompt": "6.text" }
}
```

**O exemplo mostra apenas o formato, não é um grafo executável.** Substitua `workflow` pelo grafo completo exportado em API format do seu ComfyUI. Não se inclui um grafo supostamente universal: nomes de nós, loaders e pesos variam e não há modelos instalados no ambiente de desenvolvimento para validar uma receita específica.

`modality`: `image`, `video` ou `audio`. Cada binding é `id_do_nó.nome_do_input`. Inputs devem existir no grafo. A app envia `prompt`, `seed`, `width`, `height`, `duration` (segundos), `fps`, `image`, `end_image`, `video` e/ou `audio`. Só são aplicados os que aparecem em `bindings`. **Não ligue `duration` a um input que exige frames**: calcule o número de frames no workflow com um nó de expressão ou exponha um input em segundos. Não há tradução automática nem ajuste de múltiplos exigidos pelo modelo.

As referências a ficheiros são IDs da biblioteca. O servidor faz upload para a pasta input ComfyUI e substitui o valor pelo nome resultante. Loaders de áudio/vídeo nem sempre aceitam `/upload/image`; valide a compatibilidade da instalação ou use os ficheiros já carregados no grafo e omita os bindings correspondentes.

Para produzir resultados, use nós que guardem outputs na API history como `images`, `videos`, `gifs` ou `audio`, com `filename`, `subfolder` e `type`. VHS Video Combine costuma devolver `gifs` mesmo para MP4. A exportação MP4 generativa depende do nó de saída/codificação. O servidor copia os outputs para a biblioteca privada.

No arranque de cada tarefa verifica-se `/object_info`; nós em falta bloqueiam a execução. Erros de pesos ou validação são apresentados, nunca substituídos por imagens/zoom falsos. O polling da history não inventa percentagens: permanece indeterminado até obter resultados. Timeout 2 horas não interrompe automaticamente a GPU; verifique a fila ComfyUI antes de repetir. Cancelar tarefas já em execução não está implementado, para não interromper trabalhos de outros clientes.

Documentação dos projetos a confirmar na instalação:

- https://docs.comfy.org/development/comfyui-server/comms_routes
- https://github.com/Wan-Video/Wan2.1
- https://github.com/Lightricks/LTX-Video
- https://github.com/guoyww/AnimateDiff
- https://github.com/Kosinkadink/ComfyUI-AnimateDiff-Evolved

Num Ryzen 7 com 16 GB e Radeon integrada, comece por texto quantizado pequeno. Não assuma suporte ROCm, VRAM dedicada ou desempenho viável em vídeo. Use um ComfyUI num PC com GPU suportada através de ligação privada autorizada; serviços remotos de vídeo com autenticação/pagamento específico ainda não têm adaptadores dedicados nesta versão.
