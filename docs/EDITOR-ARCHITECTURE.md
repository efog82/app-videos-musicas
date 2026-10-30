# Evolução Sonora: análise e integração

## Estado inspecionado

Baseline publicado: db69928, APK experimental 0.1 em downloads/Sonora.apk. Apenas o APK e instruções estavam versionados; o código-fonte existente foi preservado nesta branch antes de alterações. Não apagar nem substituir esse APK.

Web: Flask 3.1.2, subprocessos FFmpeg/ffprobe, fila com uma conversão, áudio e imagem para H.264/AAC 720p, estado de trabalhos em memória. Android: MainActivity monolítica com formulário; Converter descodifica PCM 16-bit, cria AAC, codifica vídeo e remistura; Painter desenha Canvas, transfere bitmap para textura GLES2 e escreve na surface do codificador. Limite Android de cinco minutos. Compilação ECJ/Android SDK 35, Android mínimo 26. Sem bibliotecas Android externas.

Não existem no baseline timeline, importação de vídeo, projetos ou qualquer legendagem. Esses recursos serão adições, sem migração destrutiva. Os testes web não demonstram funcionamento Android.

## Arquitetura proposta antes de alterar o motor

- Project: documento JSON versionado, clips não destrutivos com URI persistente, tipo, faixa, posição, entrada/saída, volume, mute e fades; opções de efeitos, legendas e exportação.
- ProjectStore: gravação atómica, autosave separado de projetos nomeados, importação/exportação JSON. Nunca altera a mídia original. Validar versões futuras antes de substituir o projeto aberto.
- EditorActivity/MainActivity: navegação Projeto/Efeitos/Áudio/Legendas/Exportar, pré-visualização, transporte e timeline persistentes; controlos de dimensão tátil acessível.
- Preview: reprodução/seeking com base temporal comum; efeitos e legendas devem partilhar parâmetros e função de renderização com exportação. Não apresentar aproximações como render final.
- Audio engine: descodificação local, PCM normalizado, mistura de faixas com ganhos/fades, extração não destrutiva de áudio original; análise FFT para graves/médios/agudos e deteção de transientes. Extração não significa separação de instrumentos.
- Render: manter barras anteriores; efeitos parametrizados, Canvas para overlays e GLES2 para apresentação/shaders; usar apenas codecs reportados pelo aparelho.
- Subtitle engine: parser/escritor SRT, edição e letra conhecida com marcação de tempos. Transcrição local só quando disponível e explicitamente acionada, pt-PT, sem envio automático online. Não inventar tempos precisos de palavras.
- Export engine: MediaCodec/MediaMuxer H.264/AAC MP4, capacidades verificadas em execução (tamanho, FPS, bitrate, surface); progresso, cancelamento, erros, recursos libertados e escrita final atómica. Não confundir suporte anunciado pelo codec com desempenho garantido.

## Integração em fases

1. Interface, projeto, importação e timeline; preservar exportação música/imagem; APK experimental próprio.
2. Áudio original ativo por defeito, mute/extração/faixa independente/mistura/volumes/fades, testes de áudio; novo checkpoint.
3. Análise espectral e efeitos ajustáveis reativos, preview/export coerentes; checkpoint e medição.
4. SRT, edição, estilos e letra sincronizada; transcrição condicionada à existência de motor local pt-PT; sem modelo/serviço disponível, relatar indisponibilidade e manter edição manual.
5. Perfis sociais, resolução/FPS/bitrate com validação de hardware, progresso/cancelamento/recuperação; testes e APK experimental.

## Dependências, opções técnicas e riscos

Manter Java/APIs Android/ECJ para evitar migração de build e SDKs remotos. Media3 Transformer pode substituir o motor de composição posteriormente, mas exige AndroidX/Gradle e testes de compatibilidade; não é necessário para a primeira interface. OpenGL ES2 já existe: shaders executam por pixel e são adequados a túneis, caleidoscópios e fractais; Canvas/bitmap por frame limita 4K/60 e exige otimização antes de promessa de desempenho. Audio FFT e partículas devem ter orçamento limitado. Usar memória limitada ao descodificar imagens/vídeo. MediaMetadataRetriever por frame é alternativa conservadora de vídeo offline, mas lenta; SurfaceTexture/MediaCodec é a evolução para baixa latência.

URIs precisam de takePersistableUriPermission, com aviso se a origem não suportar persistência. Projetos guardam referências e não copiam nem apagam ficheiros de origem. Reabrir pode exigir relocalização se o utilizador mover/apagar mídia. Uma exportação não deve alterar o projeto aberto. Encerramento pelo sistema exige autosave e recuperação, não apenas estado em Activity. Permissões, disponibilidade de reconhecimento local, codecs e desempenho dependem do telefone. Sem serviço online nem credenciais novas por omissão.

## Publicação e critérios

Trabalhar apenas em develop/editor-avancado. Manter main e downloads/Sonora.apk. Checkpoints por fase e APKs identificados experimentalmente. Não criar versão definitiva, tag de release nem merge automático. Compilação/assinatura são separadas de validação em emulador ou aparelho. Relatar testes executados, ausentes e limitações.
