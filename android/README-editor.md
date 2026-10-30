# Sonora para Android — experimental

APK nativo, sem ligação ao servidor Flask, sem permissões de Internet, sem publicidade e sem dependências pagas. Android 8 ou posterior (API 26). Escolhe música e imagem com o seletor do sistema; escreve o MP4 na pasta escolhida pelo utilizador.

Usa MediaExtractor/MediaCodec para descodificar áudio PCM e codificar AAC; Canvas/OpenGL ES para desenhar título e barras reativas à energia da música; MediaCodec para H.264 e MediaMuxer para MP4. Não utiliza FFmpeg no telefone.

## Instalar

Transfere `dist/Sonora.apk` para o Android e abre-o. Se o sistema pedir, permite a instalação de aplicações desta origem para o navegador ou gestor de ficheiros utilizado. Podes desativar essa permissão após a instalação. Não é necessário desativar o Play Protect.

Abre Sonora, escolhe uma música até 5 minutos, opcionalmente uma imagem, escreve um título, escolhe a orientação e toca em Gerar vídeo. Mantém a app aberta durante o processo. Quando terminar, toca em Guardar vídeo e escolhe a pasta. A geração pode aquecer o dispositivo e demorar vários minutos. Usa apenas conteúdos autorizados.

## Construir neste ambiente

Java 21, curl, unzip, zip e keytool; o compilador Eclipse gera bytecode Java 8. Ferramentas oficiais Android API/build tools 35 e compilador ECJ 3.38.0 são descarregados com verificação dos checksums publicados via HTTPS.

```sh
cd /workspace/app-videos-musicas
./android/setup-tools.sh
./android/build.sh
```

Saída: `android/dist/Sonora.apk`. Assinatura de desenvolvimento v2/v3; chave em `/workspace/android-tools/sonora-development.p12`, fora do repositório. Preserva-a para instalar atualizações sobre esta versão. Não é uma configuração de assinatura para publicação na Play Store.

## Estado da validação

Compilação e verificação de assinatura APK executadas neste ambiente. Não há dispositivo Android nem emulador configurado; conversão nativa e instalação ainda não foram verificadas em execução. Os testes Python na raiz pertencem à versão web e não validam o APK.

## Limitações

Versão experimental. Requer codificadores H.264 e AAC e OpenGL ES 2 suportados pelo dispositivo. Áudio PCM de 16 bits suportado pelo descodificador Android; alguns formatos podem ser rejeitados. Máximo de 5 minutos, formatos 720×1280 e 1280×720 a 24 fps. A animação representa a energia da música, não uma forma de onda exata. Sem serviço de fundo: manter a app aberta; interromper ou o Android terminar o processo pode perder a conversão. Ficheiros intermédios eliminados no final; vídeo gerado retido na cache até ser substituído ou eliminado pelo Android. Não promete compatibilidade com todos os aparelhos.
