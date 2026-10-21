# Sonora Offline 0.3.0 — APK de teste

[Descarregar Sonora Offline 0.3.0](https://github.com/efog82/app-videos-musicas/raw/refs/heads/main/downloads/sonora-offline-v0.3.0.apk)

Requer Android 8.0 ou superior. Usa o mesmo identificador e certificado do APK Sonora Offline 0.1.0 desta sessão: instalar por cima preserva os dados. Se o Android pedir, permitir a instalação a partir da aplicação usada para descarregar o APK.

## O que foi acrescentado à aplicação existente

- Oito efeitos com acabamento detalhado, brilho, gradientes e mais profundidade; opção **Leve · Aspeto original**.
- Ficheiros **SRT** sincronizados pelos tempos do ficheiro, na pré-visualização e incorporados no MP4. Mostrar/ocultar/remover, tamanho, posição e ajuste de atraso/avanço em segundos.
- Sequência de várias imagens, sem limite fixo de quantidade, com duração automática até ao fim da música, reordenação e dissolução ou corte direto.
- Importação de vários vídeos, reordenação e corte do início/fim de cada clip. O som original é removido e substituído pela música escolhida. Se necessário, a sequência repete até terminar a música.
- Enquadramento para preencher ou mostrar a imagem inteira. Opção de ocultar a visualização para exportar apenas fotografias/vídeos, música, título e legendas.

## Experimentar

1. Escolher a música.
2. Importar um SRT opcional. Ajuste positivo atrasa as legendas; negativo adianta. O SRT deve estar temporizado para a música; não há transcrição automática.
3. Escolher o efeito ou adicionar fotografias/clips na secção **Editor**. Os novos ficheiros são acrescentados ao fim da sequência; usar os botões de ordenar/cortar para a editar.
4. Pré-visualizar, escolher formato/qualidade e gerar o MP4. Guardar o resultado numa pasta ou partilhá-lo.

Tudo continua local, sem permissão de internet, conta ou marca de água. O espaço livre e o seletor Android limitam as importações; o desempenho depende do dispositivo. Usar o modo Leve se necessário. Os efeitos são desenhados até 640 px no lado maior e ampliados à resolução de saída. É uma versão experimental, a testar no teu telemóvel.

[Exemplo SRT](exemplo-legendas.srt) · [Código e instruções Android](../android/README.md)

## Validação

Foram verificados o motor de legendas, o desenho dos efeitos e a exportação de imagens/clips com cortes, repetição, rotação e música substituída. A verificação completa da nova interface ficou por concluir porque o emulador reiniciou serviços do Android. É necessário testar os botões/importações no teu telemóvel. [Detalhes da validação](../android/VALIDATION.md).
