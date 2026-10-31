# Sonora Lab 0.6.1 — correções experimentais

[Descarregar APK](Sonora-Lab-0.6.1-correcoes.apk)

Android 8+, código 66. Mesma assinatura da prévia 0.6.0 e da versão 0.4.1; instalar por cima, sem desinstalar. APKs anteriores preservados.

- Novas imagens e vídeos entram em sequência na faixa principal.
- Clips consecutivos partilham a linha; sobreposições mantêm linhas independentes.
- Para projetos existentes: Média → Organizar imagens e vídeos em sequência → confirmar.
- Para ficar apenas com som: selecionar vídeo → Extrair → Ficar só com o áudio na timeline. A referência visual é substituída por uma única referência de áudio, preservando o ficheiro original.
- Para importar só o som diretamente: Áudio → Só o áudio de um vídeo.
- Separar mantendo o vídeo e guardar M4A continuam disponíveis.
- Pré-visualização de vídeo com descodificação sequencial MediaCodec quando suportada pelo equipamento. Efeitos e legendas continuam compostos localmente; motor de exportação preservado.

## Verificação

Compilação e assinatura verificadas; instalação de atualização realizada no emulador Android API 26. Testes Java: 53 verificações de projeto/áudio e 36 de legendas passaram. 14 verificações Android com o renderer, a timeline, o codec e os comandos reais do editor passaram no emulador API 26. Resultados Android detalhados em [testes](../../../docs/phase6/tests-0.6.1/). Estes testes cobrem especificamente as regressões corrigidas, não constituem validação completa da Fase 6.

A fluidez no telefone Android 13 do utilizador ainda não foi medida. Se o codec não fornecer saída YUV, a aplicação mantém o percurso anterior de leitura de fotogramas, que pode ser lento. Fase 6 continua experimental.

[Relatório técnico](../../../docs/phase6/CORRECOES-0.6.1.md).

SHA-256 do APK: `4d124b255f8c437d85832bd6c18e7ec7d2fcf62bf69b1c96d63c0e87a430d025`.
