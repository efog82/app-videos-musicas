# Validação da versão 0.3.0

## Verificado

- Compilação do APK, assinatura v2/v3 e identificador `pt.sonora.offline`, versão 0.3.0, código 3, Android mínimo 8.0.
- Mesmo certificado do APK 0.1.0 desta sessão; instalações como atualização aceites pelo Android do emulador.
- Ausência de permissão INTERNET no APK.
- 36 verificações JVM do leitor SRT: tempos, fronteiras exatas dos intervalos, procura para trás, atraso/avanço, sobreposições, acentos, UTF-8/UTF-16/Windows-1252, marcação ASS e rejeição de ficheiros inválidos/grandes.
- Testes Android do desenho das legendas nos três formatos, ocultação, atraso, desaparecimento no fim de cada intervalo e reação dos oito estilos aos dados de áudio.
- FFT Android com tons reais de graves, médios e agudos; leitura WAV e títulos vazios/ocultos/manuais.
- MP4 com legendas incorporadas, H.264/AAC e durações de áudio/vídeo compatíveis.
- Sequência de fotografias, com a imagem certa no início/meio/fim da música.
- Sequência de clips com corte, ordem e repetição corretos, verificadas em frames descodificados.
- Substituição da banda sonora: o áudio exportado contém o tom musical escolhido e não os tons graves dos clips originais.
- Rotação dos metadados do vídeo original, comparada com um fixture MP4 com matriz de rotação real.

## Limites da verificação

A instrumentação foi executada num emulador Android 10 sem aceleração de virtualização. O sistema do emulador reiniciou serviços e bloqueou durante os testes de abertura da interface; um arranque limpo voltou a falhar durante a instalação do APK de testes. Por isso, a verificação completa da interface — seletores, importações por botões e exportação iniciada pelo utilizador — ficou por concluir. O código dessas opções compilou, mas precisa de validação num telemóvel real.

O codec do emulador não anuncia 720p vertical, que foi testado a 360×640; o desenho das legendas foi verificado nas três proporções. O MP4 1080p foi gerado, mas o leitor de miniaturas do emulador não devolveu um frame nesse caso. Isto não permite afirmar compatibilidade universal de 1080p ou desempenho em dispositivos de gama média.

A versão distribuída é experimental. Não há uma declaração de que toda a interface passou na instrumentação ou de que foi medida a fluidez num Android físico.
