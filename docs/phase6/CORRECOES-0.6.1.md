# Prévia 0.6.1 — sequência, áudio extraído e pré-visualização

## Problemas e correções

- A interface anterior atribuía uma linha a cada ficheiro, mesmo com tempos consecutivos. A timeline agora distribui clips por faixas e por sobreposição: clips consecutivos da mesma faixa partilham a linha; sobreposições continuam visíveis em linhas independentes. A seleção e o recorte usam a linha e o retângulo do clip.
- As importações visuais agora acrescentam o clip ao fim da montagem, na mesma faixa principal; novas imagens têm inicialmente 5 segundos. Música pode sobrepor-se à montagem. O limite de 10 minutos é verificado antes de alterar o documento.
- Projetos existentes não têm os tempos alterados silenciosamente. Em Média, «Organizar imagens e vídeos em sequência» permite ordenar os clips visuais pela ordem do documento, preservando durações e ficheiros, mediante confirmação.
- «Extrair» agora apresenta três operações distintas: ficar apenas com áudio na timeline (substituir a referência visual por uma referência de áudio), separar áudio mantendo o vídeo, ou guardar M4A. A primeira deixa uma única faixa de áudio; não duplica o ficheiro nem apaga o original.
- Áudio → «Só o áudio de um vídeo» importa diretamente uma única faixa a partir do vídeo, sem introduzir a parte visual.
- A faixa virtual do som original deixa de aparecer quando esse som foi separado/desativado. Um clip de áudio não recebe uma segunda faixa virtual. Vídeos normais continuam com som original por defeito.
- A pré-visualização passa a tentar descodificar vídeo sequencialmente com MediaCodec e saída YUV, redimensionada antes da composição. Evita a procura independente de cada fotograma pelo MediaMetadataRetriever. Mantém a composição de efeitos e legendas; exportação continua a usar o percurso anterior, determinístico. Se o descodificador do equipamento não fornecer imagens YUV, existe fallback para o percurso anterior, registado no log; esse percurso pode continuar lento.

## Preservação e limitações

Sem migração de schema, eliminação de originais, alteração do motor de exportação, dependências novas ou serviços online. Branch `develop/sonora-ui-fase6`; main e APKs anteriores preservados. APK novo com código 66, nome 0.6.1-phase5-experimental (phase5 identifica os motores anteriores ativos).

A sequência de ficheiros importados em lote segue a ordem devolvida pelo seletor Android. Alterar manualmente posições pode criar sobreposições intencionais. A duração das imagens continua editável. Não existe garantia de FPS no telefone: a composição, os efeitos, o formato de vídeo e o descodificador do dispositivo condicionam a pré-visualização.

## Testes

Resultados finais registados junto ao APK de correção. O teste `android/test-timeline-fix.sh` usa codecs Android reais, duas imagens de cores diferentes e a implementação real de TimelineView: verifica mudança da imagem no tempo, linha partilhada, extração para uma única faixa, preservação do original, avanço dos fotogramas e procura para trás. Não substitui testes completos da Fase 6 ou medições no telefone do utilizador.

Resultados de 2026-10-31: compilação e assinatura passaram; 53 verificações core e 36 SRT passaram; 14 verificações instrumentadas Android API 26 passaram. Foram exercitados a importação real via EditorController, seleção por toque do segundo clip na mesma linha, comando real de extração para uma faixa e importação direta do áudio de vídeo. Registos em `tests-0.6.1/`. O tempo impresso no teste Android inclui os fluxos do editor além da abertura/procura do codec; não é uma medição de FPS ou velocidade no telefone. Instalação com `adb install -r` passou, sem desinstalação. Certificado SHA-256 igual ao das versões anteriores: `cd923774b384cee98f9971ece24c3270ed298f63fbdf207db10ab79ac08b0941`.
