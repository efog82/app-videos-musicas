# Sonora: arquitetura analisada e plano de integração

## Base preservada

Base atual do GitHub: **4301bd5**, Sonora Offline 0.3.0. A cópia local inicial estava em db69928; foi preservada em 873e079 e o protótipo em 3031d80. A versão atual foi obtida por `git fetch origin main` (o fetch refspec desta máquina só atualiza origin/HEAD) e integrada por merge nesta branch. Nenhum reset, eliminação de trabalho local ou alteração de main.

Todo o código da 0.3 foi analisado: MainActivity, EditorControls, Options, MediaIo, AudioAnalysis, SceneRenderer, EffectLayers, SubtitleTrack/Renderer, MediaSequence, VideoFrames, GlEncoder, VideoExport, ExportService/Provider, manifests, builds e testes. A versão web Flask/FFmpeg também foi inspecionada e permanece intacta.

A 0.3 usa Java nativo/API 26+, PCM em disco com FFT de 2048 amostras, Canvas para efeitos, EGL/OpenGL ES2 para composição e MediaCodec/MediaMuxer H.264/AAC. Já possui oito efeitos detalhados/modo leve, SRT multiencoding, fotos/vídeos sequenciais, cortes, dissolução, repetição de clips, enquadramento, 720p/1080p/24 FPS, três proporções, partilha e serviço de exportação em primeiro plano. O áudio de vídeos era silenciado e substituído pela música. Não havia documento de projeto, timeline multifaixa nem transcrição automática.

## Integração apresentada antes das alterações do motor

O editor é um APK **Sonora Lab** separado (`pt.sonora.editor`), para coexistir com `pt.sonora.offline`. Não desinstala nem migra destrutivamente dados da 0.3. O fluxo 0.3 fica disponível no botão **Modo compatível**, com todas as suas funções e motor originais. O novo editor usa APIs locais, sem permissão INTERNET nem SDK pago.

- `Project`: JSON versionado com clips, URIs, faixa, posição, cortes, volume, mute, fades, efeitos, legendas e perfil de exportação. Projeto é uma descrição; mídia original nunca é regravada.
- `ProjectStore`: gravação atómica, projetos nomeados e autosave; rejeita documentos de versões futuras antes de substituir o projeto aberto. Exportação JSON contém referências, não um pacote dos ficheiros.
- `MainActivity`/`TimelineView`: separadores Projeto/Efeitos/Áudio/Legendas/Exportar, transporte, timeline com arrasto/cortes e diálogo de tempos. Painéis de ferramentas têm scroll próprio; o editor não é um formulário contínuo. Áreas de preview/timeline recolhíveis para ecrãs pequenos.
- `PreviewView` e `SceneRenderer`: relógio da mistura PCM reproduzida, procura e frames de vídeo fora da UI. A mesma função determinística de desenho é usada pelo encoder. A cadência de preview depende do aparelho e pode ser inferior à exportação.
- `EditorExporter`/`AudioMath`: descodificador 0.3 reutilizado por `LegacyBridge`; mistura em blocos estéreo 48 kHz com reamostragem linear, volumes, mute e fades. PCM em disco, não toda a música em RAM. Extração para M4A e separação para faixa não removem o vídeo nem separam voz/instrumentos.
- `Spectrum`/`VisualEffects`: FFT 2048, bandas de frequência, energia e transientes aproximados. Parâmetros persistentes; estilos antigos via renderizador original e novas geometrias limitadas por densidade.
- `Subtitles`: reutiliza `SubtitleTrack` 0.3 para preservar UTF-8/UTF-16/Windows-1252, sobreposições e limpeza de marcação; adiciona edição, exportação SRT, estilos, animações e marcação manual de letra conhecida.
- `LocalTranscriber`: só motor Android on-device, Android 13+ para áudio de ficheiro e modelo **pt-PT instalado**. Não inicia modelo/serviço online. Android 14+ pode fornecer tempos por palavra; sem esses dados entrega rascunho para correção, não tempos inventados. Sem motor disponível, edição/SRT continuam utilizáveis.
- `EditorExportService`: exportação em primeiro plano com wake lock, progresso, cancelamento e recuperação do último resultado. `ExportOptions` verifica memória, surface, dimensões/FPS e bitrate anunciados pelo codec. Gravação de resultado atómica. O serviço antigo continua preservado no modo compatível.

## Fases experimentais

Os APKs usam o mesmo núcleo e ativação progressiva dos novos painéis através de `SONORA_PHASE=1..5`. Isto permite testar a integração cumulativa; não representa cinco motores independentes. A versão 0.3 e o modo compatível mantêm as funções já existentes em todas as fases.

1. Projeto, navegação, importação, preview, timeline, guardar/reabrir e exportação inicial.
2. Ativar painel de áudio: original ativo por defeito no editor novo, mute, faixa independente, extração M4A, mistura, volumes e fades.
3. Ativar painel de efeitos: anteriores detalhados/leves e novos efeitos parametrizados, sincronização espectral, transformações.
4. Ativar painel de legendas: SRT/edição/letra, tipografia/animações e reconhecimento local condicionado a suporte real.
5. Ativar perfis sociais e opções avançadas de resolução/FPS/bitrate, verificações e controlo da exportação.

## Decisões e riscos

Sem migração para Compose/Media3 nesta entrega: reduziria a continuidade com Java/MediaCodec e exigiria novas dependências/Gradle. Media3 Transformer é candidato para otimizar composição e áudio no futuro. O GLES2 existente faz composição de texturas; os novos efeitos são desenho 2D procedural/FFT, não simulação volumétrica nem solver físico de fluidos. Caleidoscópio usa simetria geométrica, fractal usa ramificação recursiva; transientes são aproximações de batidas. Uma futura implementação GLSL pode acelerar fractais/distorções por pixel, mas precisa de perfilagem e comparação visual antes de substituir o caminho atual.

No novo caminho, extração de frames usa MediaMetadataRetriever e pode ser lenta; o caminho sequencial SurfaceTexture/MediaCodec da 0.3 continua no modo compatível. 4K/60 anunciado por codec não garante memória, velocidade, temperatura ou estabilidade. Não foi medido desempenho em telefone físico. Exportação 1440p/4K, 25/30/60 FPS e transcrição pt-PT precisam de validação em hardware compatível.

URIs persistentes podem ficar inválidas se a mídia for movida/apagada; existe relocalização. O Lab tem sandbox próprio: não lê automaticamente os dados privados do Sonora Offline já instalado. O utilizador pode selecionar os ficheiros originais sem alterar a aplicação anterior. A exportação JSON não transporta mídia para outro telefone. O projeto usa até dez minutos por composição, como o limite musical da 0.3; coleções e documentos têm limites de recursos. O modo compatível preserva a gestão anterior de sequências e repetição.

## Publicação

Apenas branch `develop/editor-avancado`. Nenhum merge para main, tag definitiva, Play Store, serviço pago ou envio de mídia. APKs de teste identificados por fase em downloads/experimental. Os testes web não substituem testes Android. Ver VALIDATION-EDITOR.md para evidência e limites da entrega.
