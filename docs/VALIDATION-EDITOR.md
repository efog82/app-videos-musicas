# Validação do Sonora Lab 0.4 experimental

## Executado nesta entrega

- Branch de desenvolvimento integra a base atual 0.3.0 (4301bd5) sem alterar main nem os APKs anteriores.
- 24 asserções JVM do editor: round-trip de projeto, Unicode, snapshots independentes, rejeição de schema futuro, tempos, volume, mute, fades, áudio original, saturação, SRT e isolamento de bandas FFT.
- 36 verificações do teste SRT existente da 0.3: encodings, entidades, tempos, fronteiras, atrasos, procura, sobreposições e entradas inválidas/limites.
- 39 asserções de instrumentação em Android 11/API 30, emulador x86_64 com GPU SwiftShader e CPU por software: descodificação real, presença simultânea dos tons de música/áudio original, remoção apenas do tom original ao silenciar, fade da faixa de música, nove desenhos novos/modo sem efeito, oito estilos anteriores detalhados/leves, gravação/reabertura, exportação H.264/AAC horizontal e vertical, dimensões/duração, cancelamento e abertura dos cinco painéis com espaço de edição. Resultado -1 do runner, sem falha. Log: validation/android-integration.txt.
- Teste separado da preparação de mistura para preview, exportação por serviço após fechar Activity, resultado MP4 acessível pelo provider de leitura e preservação do ficheiro de origem. Log: validation/android-service.txt.
- Versão web inalterada: dois testes unittest executados, incluindo conversões MP4 nos dois formatos e download/rejeição de entrada inválida.
- Verificação externa do MP4 integrado com ffprobe: 1280×720, 24 frames/24 FPS, vídeo H.264 de 1,000 s e AAC estéreo/48 kHz de 0,981333 s; diferença de 18,7 ms. Registo em validation/ffprobe-export.json.
- Cinco APKs cumulativos gerados, identificados por fase, assinaturas verificadas, códigos 41..45, pacote pt.sonora.editor, Android mínimo 26. Sem permissão INTERNET. Checksums em downloads/experimental/SHA256SUMS.txt; metadados em validation/apks.json.

A instrumentação completa foi executada no núcleo cumulativo da fase 5. Os APKs das fases 1..4 têm o mesmo núcleo e painéis progressivamente ativados; foram compilados/assinados/verificados, mas não receberam cinco execuções completas independentes da instrumentação. As alterações finais de anotação de letras/recuperação foram compiladas e a preparação de preview/serviço foi verificada no APK final.

## Limites que permanecem

- Sem Android físico: não há promessa de compatibilidade universal, fluidez, temperatura, bateria ou velocidade. O emulador sem aceleração demorou vários minutos a arrancar/exportar; não representa desempenho de um telefone.
- 1080p, 1440p/4K, 25/30/60 FPS: há verificações de codec/memória e tratamento de erros, mas estes perfis não foram todos exportados nesta validação. Só 720p/24 FPS nos dois formatos recebeu exportação integrada nesta entrega.
- O emulador API30 não tem o serviço on-device pt-PT nem suporta a API de ficheiros do reconhecimento Android13+. Transcrição e tempos de palavras Android14+ foram integrados com verificação de disponibilidade e fallback manual/SRT, mas **não validados com um modelo pt-PT real**. Não apresentar a função como reconhecimento universal ou alinhamento exato de canto.
- Efeitos são 2D procedurais e composição GLES; kaleidoscópio geométrico, partículas limitadas, fractal recursivo e fluxos sinusoidais. Não há solver físico, simulação volumétrica ou motor GLSL avançado prometido como concluído.
- O modo compatível preserva o motor/fluxos 0.3. O novo documento multifaixa não importa automaticamente os dados privados da aplicação 0.3 instalada separadamente. Sequências/repetição e opções específicas continuam disponíveis nesse modo; não houve migração destrutiva.
- SAF no telefone, centenas de ficheiros, pouco armazenamento, falhas do fabricante e modelo de reconhecimento continuam a exigir testes no aparelho. Recuperação depende de guardar os ficheiros de origem; JSON não é arquivo com a mídia incluída.

## Reproduzir

```sh
./android/setup-tools.sh
SONORA_PHASE=5 ./android/build-editor.sh
./android/test-core.sh
./android/test-device.sh  # Android/emulador ligado por ADB
./android/test-service.sh
.venv/bin/python -m unittest discover -s tests -v
```

Não alterar scripts nem APKs durante os testes. A primeira tentativa de instalação foi feita antes de o PackageManager terminar o arranque e falhou; aguardou-se o serviço e a instalação foi repetida com sucesso. O primeiro runner foi editado enquanto estava ativo, invalidando o seu wrapper; as verificações finais usaram runner congelado e saída raw com INSTRUMENTATION_CODE -1. Nenhum teste ou assert foi desativado para obter aprovação.

O teste de serviço detetou ainda uma janela entre publicar o caminho do MP4 concluído e terminar a libertação de recursos do serviço: o runner verificava `running` cedo demais. A espera foi corrigida para exigir resultado **e** término do serviço, mantendo a asserção e o prazo. A falha original está registada em validation/android-service-initial-failure.txt; o resultado após correção fica em validation/android-service.txt.

Na captura visual final apareceu também um ANR do **System UI do emulador**, não da Activity do Sonora. Aguardar permitiu capturar o editor normalmente. Ambos os ecrãs estão registados (validation/systemui-anr.png e validation/editor-screen.png). Esta instabilidade do Android em CPU emulada reforça que não foi validada fluidez num aparelho físico. A primeira tentativa de copiar o MP4 por ADB shell foi bloqueada pela proteção de ficheiros do Android; foi repetida com ADB root no emulador de testes próprio, sem alterar a app nem as suas permissões.
