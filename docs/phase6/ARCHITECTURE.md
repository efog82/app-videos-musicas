# Fase 6 — estratégia antes da implementação

Base analisada: develop/editor-avancado, 0931925 (Sonora Lab 0.4.1). Branch de trabalho: develop/sonora-ui-fase6. Main e APKs anteriores são preservados.

## Constatações

MainActivity tem 1868 linhas e reúne composição visual, formulários, imports e ciclo de vida. TimelineView tem linhas independentes mas sem miniaturas/PCM, seleção de legendas ou distinção visual do áudio original. PreviewView, Project/ProjectStore, EditorExporter/EditorExportService, SceneRenderer, LocalTranscriber e os motores 0.3 estão separados. O schema JSON é 1. A chave de teste 0.4.1 existe fora do checkout; compatibilidade será comparada por certificado, não apenas pelo nome do ficheiro.

## Integração

Manter Java/APIs Android 26+, sem Compose/AndroidX ou dependências comerciais. MainActivity torna-se adaptador de ciclo de vida; EditorController conserva sessão e operações existentes. Componentes separados: HomeScreen/ProjectBrowser, EditorScreen, Player, TimelineView/MediaArtwork, Toolbar/ContextTools, MediaPickerSheet, EffectsPanel, SubtitlePanel, ExportPanel e tema vetorial comum. Opções avançadas anteriores continuam em LegacyPanels e o modo 0.3 mantém-se acessível.

O fluxo começa no início; Novo projeto entra no editor e abre a importação. Timeline ocupa a área central, tem miniaturas e ondas PCM reais calculadas fora da UI, linhas para vídeo/imagem/música/áudio original/legendas e seleção contextual. O áudio original é uma linha ligada ao vídeo, não uma duplicação que o misture duas vezes. Split/duplicação são operações não destrutivas sobre descrições JSON. Velocidade por clip, recorte manual e movimento de imagem não são falsamente ligados à velocidade dos efeitos: limitações explícitas até suporte de motor.

Guardar/renomear/duplicar/eliminar opera apenas em documentos e posters privados, nunca nas URIs originais. Autosave 0.4.1 é recuperável. Schema 1 conserva campos antigos; qualquer atributo novo de estilo tem default compatível. Alteração mínima ao renderer apenas se necessária para cor/estilo de legenda; motores de codecs, mistura, extração e efeitos permanecem intactos.

Exportação tem painel próprio, estimativa matemática de bytes baseada em bitrate/duração, checagem existente de memória/codecs, progresso real e cancelamento. Reconhecimento local é preservado com diagnóstico e disponibilidade; não se promete pt-PT sem motor/modelo do aparelho.

## Validação planeada

Testes JVM para operações de projeto, split/duplicate e JSON anterior; instrumentação para início/projetos/imports/contexto/timeline/efeitos/SRT/player/fullscreen/export/cancelamento e dimensões pequenas. Compilação/assinatura separadas de execução. Os emuladores anteriores falharam no sistema sem KVM; preparar um Android 8 limpo e documentar qualquer bloqueio, sem converter testes não executados em aprovação. APK experimental novo, certificado comparado com 0.4.1, download verificado na branch nova. Nenhum release definitivo nem merge automático.
