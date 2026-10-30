# Sonora Offline — Android

Versão Android nativa acrescentada ao projeto Sonora existente. A aplicação web em Flask mantém-se intacta na pasta superior. Os oito efeitos e controlos da versão web foram adaptados para Canvas/OpenGL e os serviços de áudio/vídeo locais do Android. Não incorpora servidor, Python, WebView remoto ou serviços online.

## Instalar e testar

APK de teste: `build/sonora-offline-teste.apk`. Android 8.0 (API 26) ou superior, sem dependências nativas por arquitetura. Assinado com certificado de teste; não é uma publicação na Play Store.

1. Transferir o APK para o telemóvel e abri-lo.
2. Se o Android pedir, permitir a instalação a partir da aplicação usada para abrir o ficheiro.
3. Abrir **Sonora Offline**, escolher um áudio local e um dos oito efeitos.
4. Ouvir e pré-visualizar, ajustar as opções e gerar o vídeo MP4.
5. Usar **Guardar MP4 no dispositivo** para escolher uma pasta ou **Partilhar vídeo**.

Pode testar em modo de avião. A aplicação não declara a permissão INTERNET. A seleção de ficheiros é feita pelo seletor de documentos do Android; ficheiros em fornecedores de nuvem devem ser transferidos previamente pelo utilizador.

A permissão de notificações é opcional e permite ver o progresso da exportação. O processamento continua num serviço em primeiro plano, com bloqueio parcial de suspensão, e pode ser cancelado na interface ou na notificação. Restrições de bateria impostas pelo fabricante ou encerramento forçado podem interromper a exportação; nesse caso, voltar a abri-la e exportar novamente.

## Funcionalidades

- Título vazio não desenha texto. Título manual com até 48 caracteres e opção de mostrar/ocultar.
- Oito estilos: equalizador, espectro circular, partículas, ondas, túnel, mandala, osciloscópio e galáxia.
- Seis cores, intensidade e velocidade entre 0,25× e 2×, fundo preto/imagem/animado, composição centralizada ou em todo o ecrã.
- Pré-visualização em tempo real com reprodução do áudio original, pausa e procura na música.
- FFT de 2048 amostras, janela Hann, 48 bandas logarítmicas e graves/médios/agudos. Forma de onda PCM real, sem simulação de áudio. A velocidade visual não altera a música.
- MP4 H.264 + AAC 192 kbps, 24 fps, horizontal 16:9, vertical 9:16 e quadrado 1:1. 720p e 1080p sujeitos às capacidades anunciadas pelo codificador do dispositivo.
- Até 10 minutos por música, mono ou estéreo, nos formatos que o Android do dispositivo conseguir descodificar. Áudio preservado musicalmente, recodificado em AAC; não é cópia binária sem perdas.
- Sem conta, internet ou marca de água.

## Desempenho e armazenamento

O desenho usa no máximo 640 px no lado maior e é ampliado pela GPU para a resolução de saída. 1080p não significa desenho nativo a 1080p. As partículas estão limitadas a 240 pontos; o túnel usa polígonos concêntricos e a galáxia uma espiral com halo suave, sem simulação volumétrica.

O áudio descodificado fica num ficheiro PCM privado (até cerca de 115 MB para dez minutos em estéreo a 48 kHz). Apenas pequenas janelas de amostras ficam em memória. A exportação verifica o espaço livre estimado antes de começar. MP4s e música selecionada ficam na área privada da aplicação até limpar os dados ou desinstalar. Usar Guardar/Partilhar para preservar os resultados fora da aplicação. Não há envio de dados.

A exportação foi desenhada para codificação acelerada quando suportada. A compatibilidade e a velocidade variam com os codecs/GPU e as políticas de bateria de cada fabricante. Testes de emulador não substituem medições em telemóveis de gama média.

## Compilar

Pode abrir esta pasta no Android Studio (AGP 8.7.3, SDK 35, Java 17+). As dependências de compilação exigem internet apenas na preparação do ambiente, nunca na aplicação instalada.

Também há uma compilação sem Gradle para ambientes com SDK instalado:

```sh
ANDROID_SDK_ROOT=/caminho/android-sdk SONORA_KEYSTORE=/caminho/chave-de-teste.jks ./build.sh
```

Requer JDK com o módulo `jdk.compiler`, Python 3 e Android SDK Platform 35 / Build Tools 35.0.0. Se a chave não existir, o script gera uma chave de teste com palavra-passe `android`; não usar esta chave num lançamento público. Guardar a chave para permitir futuras atualizações do mesmo APK.

## Validação Android

`tests/SonoraTests.java` é uma instrumentação separada do APK distribuído. Testa ausência de permissão de internet, bandas FFT com tons reais, título vazio/oculto/manual, descodificação WAV, exportação de oito estilos, dimensões nos três formatos, 1080p quando suportado, áudio AAC com duração sincronizada, descodificação de um frame de cada MP4 e abertura da interface.

```sh
./build.sh
./tests/build.sh
adb install -r build/sonora-offline-teste.apk
adb install -r build/tests/sonora-tests.apk
adb shell am instrument -w pt.sonora.offline.tests/pt.sonora.offline.SonoraTests
```

## Atualização 0.2.0 — efeitos e legendas SRT

Esta atualização preserva a aplicação, os oito estilos e o pipeline de áudio/exportação que já funcionava. O modo **Detalhado** acrescenta brilho em camadas, gradientes, reflexos no equalizador, anéis com espectro interpolado, partículas com profundidade, ondas em fitas, perspetiva no túnel, mandalas com pétalas e uma galáxia com braços e halo. O modo **Leve · Aspeto original** mantém os desenhos anteriores para quem prefere menos carga gráfica. Ambos usam os mesmos dados FFT/PCM do áudio.

### Usar legendas

1. Escolher a música e tocar em **Importar legendas .srt**.
2. Escolher um SRT já temporizado para essa música. As legendas aparecem na pré-visualização e ficam desenhadas permanentemente no MP4 exportado, visíveis em qualquer leitor.
3. Se necessário, alterar **Ajuste de sincronização (segundos)**. `0` respeita os tempos originais; `1.500` atrasa tudo 1,5 segundos; `-0.500` adianta meio segundo. Aceita ponto ou vírgula, com precisão de milissegundos e limite de ±600 segundos.
4. Escolher tamanho e posição. Desmarcar **Mostrar legendas** para exportar sem as legendas ou usar **Remover legendas** para retirar o ficheiro.

Não há reconhecimento de voz ou sincronização automática de um texto sem tempos: o relógio da música é comparado com os tempos do SRT, independentemente da velocidade visual. A procura na música recalcula a legenda correta, incluindo recuos. A precisão visual no MP4 fica limitada às imagens a 24 fps (cerca de 42 ms).

UTF-8, UTF-16 com BOM e Windows-1252 são aceites. Suporta várias linhas, tempos com vírgula ou ponto, índices opcionais e legendas sobrepostas. Formatação HTML/ASS é removida e entidades comuns são convertidas para texto simples. Limites: 2 MB, 10000 legendas, 2000 caracteres por legenda, até oito sobreposições e 6000 caracteres em simultâneo. Ficheiros inválidos não substituem as legendas já carregadas.

As legendas e as suas opções ficam guardadas na área privada da aplicação e sobrevivem a uma atualização instalada com o mesmo certificado. A versão 0.2.0 usa o mesmo identificador e certificado do APK 0.1.0 disponibilizado nesta sessão: instalar por cima, sem desinstalar, preserva os dados.

O teste JVM do leitor de legendas pode ser executado sem emulador:

```sh
mkdir -p build/host
java com.sun.tools.javac.Main -d build/host app/src/main/java/pt/sonora/offline/SubtitleTrack.java tests/SubtitleTrackHostTest.java
java -cp build/host pt.sonora.offline.SubtitleTrackHostTest
```

A instrumentação Android também verifica o desenho das legendas nos três formatos, ajuste de atraso, ocultação, desaparecimento no fim de cada intervalo, reação dos oito estilos ao áudio e preservação do modo Leve. Os MP4 de teste incluem legendas SRT; a interface é testada com importação válida e inválida.

## Atualização 0.3.0 — editor de fundos com imagens e vídeos

Acrescenta ferramentas de edição à mesma aplicação, mantendo os efeitos, o áudio, os formatos, as legendas SRT e a exportação já existentes:

- **Adicionar imagens · Seleção múltipla**: escolher várias fotografias no seletor Android. Novas importações são acrescentadas à sequência. Sem limite fixo de quantidade; o espaço livre e o seletor do Android são os limites práticos. Até 25 MB por imagem de origem.
- A duração de cada fotografia é calculada automaticamente dividindo a duração da música pelo número de imagens. É possível ordenar ou remover imagens e escolher corte direto ou dissolução suave. A exportação termina com a música. Como em qualquer vídeo a 24 fps, só pode ser mostrada uma imagem distinta por frame; selecionar mais imagens do que frames disponíveis não permite mostrar todas individualmente.
- **Adicionar vídeos · Som original removido**: importar um ou vários clips locais. O áudio original dos clips fica totalmente silenciado; o MP4 utiliza apenas a música selecionada no passo 1.
- **Ordenar / cortar vídeos**: mudar a ordem dos clips, remover clips ou escolher o início/fim de cada um em segundos. São cortes por tempo, não um editor de pistas com arrasto. A sequência repete se terminar antes da música e a exportação para exatamente no fim da música (com a precisão de um frame).
- **Preencher** corta margens para ocupar o formato escolhido; **Ajustar** preserva a imagem inteira com margens pretas quando necessário.
- Desmarcar **Mostrar visualização musical sobre o fundo** para produzir um vídeo de fotografias/clips com música, título opcional e legendas. Esta opção não interfere com mostrar/ocultar o título e as legendas.
- Os novos fundos estão no seletor **Fundo → Sequência de imagens / Os meus vídeos**. Continua a ser possível escolher preto, imagem única ou animado.

As imagens são normalizadas uma a uma e o renderizador mantém apenas a imagem atual e a seguinte em memória. Os clips são copiados para a área privada, descodificados sequencialmente com MediaCodec e compostos por OpenGL diretamente na exportação; o seu áudio nem sequer entra no pipeline de áudio. A pré-visualização dos clips usa um leitor separado com volume zero, sincronizado pelo relógio da música. Vídeos de origem com rotação são enquadrados segundo essa orientação.

Importações podem ser canceladas. Uma importação inválida ou cancelada mantém a sequência anterior. Os ficheiros importados e resultados ocupam armazenamento privado; limpar os dados ou desinstalar remove-os, pelo que os resultados importantes devem ser guardados numa pasta escolhida pelo utilizador. A edição não altera os ficheiros originais fora da aplicação.

Para a instrumentação do editor, gerar os pequenos vídeos de teste antes de compilar o APK de testes:

```sh
./tests/create-fixtures.sh  # Requer FFmpeg apenas na máquina de desenvolvimento
./tests/build.sh
adb shell am instrument -w -e editorOnly true pt.sonora.offline.tests/pt.sonora.offline.SonoraTests
```

Estes testes verificam a sequência de fotografias pela duração da música, a ordem/corte/repetição dos clips, a orientação, a troca de banda sonora e a importação de ficheiros pela interface. Os fixtures não fazem parte do APK distribuído. Na imagem de emulador Android 10, 720p vertical não é anunciado pelo codificador e é testado a 360×640; os três formatos também são testados no desenho das legendas. O leitor de miniaturas do emulador pode não devolver um frame 1080p, pelo que os MP4 exportados devem ser também inspecionados externamente com FFmpeg.

### Estado desta entrega

Ver [VALIDATION.md](VALIDATION.md) para os testes realmente executados e as limitações do ambiente. A exportação e os testes do motor de legendas/media foram verificados; a validação completa da nova interface ficou por concluir no emulador e deve ser feita no telemóvel.
