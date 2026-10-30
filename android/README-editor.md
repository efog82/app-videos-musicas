# Sonora Lab — editor audiovisual experimental

Android 8+, processamento local, sem permissão INTERNET. Coexiste com Sonora Offline 0.3 e conserva o seu motor no **Modo compatível**. O APK anterior e main não são substituídos.

## Experimentar

1. Instalar o APK experimental da fase desejada; recomenda-se a fase 5, cumulativa.
2. No separador Projeto importar música, vídeo ou imagens. O vídeo mantém o som por defeito no editor novo.
3. Selecionar clips na timeline. Arrastar corpo para deslocar ou extremidades para cortar; o diálogo de tempos permite precisão em segundos.
4. Reproduzir, pausar ou procurar. A primeira reprodução prepara a mistura e a análise local. O botão ▥ recolhe/mostra preview e timeline para dar espaço às ferramentas.
5. Ajustar Áudio, Efeitos e Legendas. No Áudio, separar cria faixa referenciada e silencia apenas o som associado ao clip; extrair escreve M4A sem modificar a origem.
6. Guardar projeto (💾), abrir projetos nomeados ou exportar/importar JSON. O JSON referencia a mídia, não a inclui. Relocalizar a origem se o ficheiro for movido.
7. Exportar verifica as capacidades reais. Escolher Guardar MP4, Reproduzir ou Partilhar quando concluir. Cancelamento disponível na aplicação e notificação. Exportação executa em serviço em primeiro plano, sujeita às regras de bateria do Android.

O Modo compatível mantém as sequências, repetição, opções de fundo, efeitos e funções da 0.3. A versão instalada anteriormente tem dados privados próprios e não é migrada automaticamente. Os originais nunca são apagados pelo editor.

## Legendas e reconhecimento

SRT conserva o parser 0.3 (UTF-8, UTF-16/BOM, Windows-1252), edição de texto/tempos e exportação. Letra conhecida: uma frase por linha e Marcar agora durante a reprodução; rever os tempos finais. Animação por palavras sem tempos individuais é identificada como ritmo estimado.

Transcrição de ficheiros só em Android 13+ com serviço on-device e modelo pt-PT instalado. O motor pode não suportar ficheiros, canto, sessões segmentadas ou tempos por palavra. Se não fornecer tempos, é criado um rascunho a editar. Sem motor/modelo, a aplicação informa da indisponibilidade e não usa serviços online. Esta integração requer teste em dispositivo compatível.

## Construir

```sh
cd /workspace/app-videos-musicas
./android/setup-tools.sh
SONORA_PHASE=5 ./android/build-editor.sh
./android/test-core.sh
```

`SONORA_PHASE=1..5` ativa progressivamente os painéis. Saída: `android/dist/Sonora-faseN-experimental.apk`. Pacote pt.sonora.editor, versão 0.4 fase N; chave de teste mantida fora do repositório em /workspace/android-tools/sonora-development.p12. Não usar esta assinatura de desenvolvimento para distribuição definitiva. Fases mais recentes atualizam o mesmo Lab; Android bloqueia instalar um código de versão inferior por cima, portanto não se recomenda downgrade/desinstalação que perca projetos.

O build original 0.3 permanece em `android/build.sh`, com Gradle/Android Studio também preservados em app/. A compilação experimental é separada e inclui o código original com uma adaptação de autoridade do provider para coexistência.

Teste Android separado (com emulador ou dispositivo ligado):

```sh
SONORA_PHASE=5 ./android/build-editor.sh
./android/test-device.sh
```

Não alterar scripts/APKs durante uma execução de testes. Resultados atuais em docs/VALIDATION-EDITOR.md e android/.build/device-test/results.txt.

## Limites

MP4 H.264/AAC; 720/1080/1440/2160p e 24/25/30/60 FPS apenas quando memória e codec aceitam. Não há garantia de desempenho 4K/60. Desenho e extração de frames podem ser lentos. Música/composição até dez minutos, áudio mono/estéreo, recursos de memória/disco limitados. Reamostragem linear e clamp de saturação; reduzir volumes se distorcer. Efeitos 2D procedurais, sem simulação física de fluidos ou separação de voz/instrumentos.

Ver docs/EDITOR-ARCHITECTURE.md para análise, integração e riscos; docs/VALIDATION-EDITOR.md para validação real. É um protótipo experimental, não uma versão definitiva.

## Atualização 0.4.1

APK cumulativo: downloads/experimental/Sonora-0.4.1-experimental.apk, código 55. Atualiza o Lab existente; não desinstalar para preservar projetos. Inclui remoção junto à timeline, lista de elementos e preview em ecrã inteiro. A compilação atual por fase usa códigos 51..55; os APKs históricos 0.4 preservados usam 41..45. Ver docs/VALIDATION-0.4.1.md para evidência e limitações, em particular a transcrição Android 13.
