# Sonora Lab 0.4 — APKs experimentais

[Descarregar Sonora Lab 0.4.1 — atualização experimental](https://github.com/efog82/app-videos-musicas/raw/refs/heads/develop/editor-avancado/downloads/experimental/Sonora-0.4.1-experimental.apk)

A atualização 0.4.1 acrescenta remoção visível, identificação dos elementos na timeline, preview em ecrã inteiro e diagnóstico/correções de transcrição. [Validação e limitações da atualização](../../docs/VALIDATION-0.4.1.md). Os APKs por fase abaixo são a entrega 0.4 anterior, preservada para referência.

Instala **Sonora Lab** ao lado do Sonora Offline anterior, sem o desinstalar nem substituir os seus dados. Android 8 ou posterior. Não é uma versão definitiva nem uma publicação na Play Store.

| APK | Novos painéis disponíveis |
| --- | --- |
| [Fase 1](Sonora-fase1-experimental.apk) | Projeto, importação, preview, timeline, guardar/reabrir e exportação inicial |
| [Fase 2](Sonora-fase2-experimental.apk) | Acrescenta áudio original, extração, faixa independente, mistura, volumes e fades |
| [Fase 3](Sonora-fase3-experimental.apk) | Acrescenta efeitos parametrizados e sincronização espectral |
| [Fase 4](Sonora-fase4-experimental.apk) | Acrescenta SRT, edição, letra e integração de transcrição local condicionada |
| [Fase 5](Sonora-fase5-experimental.apk) | Acrescenta perfis e opções avançadas de exportação, progressos e cancelamento |

São versões cumulativas do mesmo núcleo, com ativação gradual dos painéis. Recomenda-se a fase 5. Instalar uma fase posterior atualiza o Lab; o Android não permite normalmente instalar uma fase anterior por cima. Não desinstalar para fazer downgrade se pretender conservar projetos.

O botão **Modo compatível** conserva as funções Sonora 0.3: efeitos detalhados/leves, sequências, repetição, SRT, opções de fundo e exportação. No editor novo, vídeo mantém áudio original por defeito. Os dados privados do Sonora Offline já instalado não são lidos automaticamente pelo Lab: escolher a mídia original no seletor.

Transcrição requer Android13+, motor local e modelo pt-PT; tempos por palavra dependem do motor/Android14+. Sem esse suporte, usar SRT ou editar manualmente. Nenhum serviço online é usado. 1440p/4K e FPS elevados dependem de memória/codecs e não têm garantia de desempenho.

[Instruções](../../android/README-editor.md) · [Arquitetura e plano](../../docs/EDITOR-ARCHITECTURE.md) · [Validação e limites](../../docs/VALIDATION-EDITOR.md) · [Checksums](SHA256SUMS.txt)
