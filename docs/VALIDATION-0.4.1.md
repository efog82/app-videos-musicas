# Sonora Lab 0.4.1 experimental

## Alterações

- Remover elemento selecionado num botão persistente junto à timeline, com confirmação e preservação do ficheiro original.
- Lista de todos os elementos, com tipo e nome; seleção e remoção também disponíveis por essa lista.
- Uma linha por elemento, incluindo clips sobrepostos. Imagens e sons deixam de ficar escondidos atrás da primeira faixa de vídeo. Seleção do elemento importado desloca a timeline para essa linha.
- Importações passam para o início do painel Projeto; nomes de ficheiros também são recuperados para URIs de ficheiro.
- Preview em ecrã inteiro com reprodução/pausa, procura, retorno ao editor e rotação. Reutiliza o mesmo PreviewView/player e preserva a posição.
- Modo compatível: permite remover a última imagem/vídeo da sequência, a música e o fundo; o ficheiro original não é apagado.
- Transcrição: listas de idiomas vazias passam a significar compatibilidade desconhecida, não modelo em falta; aceita `pt` genérico e `pt_PT`, sem substituir automaticamente Portugal por Brasil. Usa pipe PCM para entrada de ficheiro, espera limitada por timeout, alternativa de sessão sem segmentação quando recusada e erros específicos. A permissão autorizada retoma o pedido automaticamente.
- Diagnóstico no separador Legendas com versão Android, serviço de reconhecimento, distinção entre TTS/saída de texto e reconhecimento, abertura das definições e pedido opcional de modelo ao Android. Qualquer download de modelo exige ação explícita no diálogo; áudio não é enviado a um serviço online pelo Sonora.

## Verificado

- Compilação e assinatura v2/v3; pacote pt.sonora.editor, código 55, Android mínimo 26. Atualiza o Sonora Lab anterior com a mesma assinatura, sem desinstalar nem migrar destrutivamente projetos.
- 35 asserções JVM de projeto/áudio/SRT/FFT, remoção de todos os tipos e último elemento, persistência do projeto vazio e decisões de compatibilidade de reconhecimento.
- 36 testes existentes do parser SRT continuam a passar.
- APK dos testes de interface compilado e assinado, sem executar testes no dispositivo; código criado em android/tests/EditorUiInstrumentation.java para importação, linhas independentes, seleção, confirmação/cancelamento/remoção, preservação de originais e ida/volta do ecrã inteiro.

## Limites desta entrega experimental

O emulador Android 11 reiniciou o system_server com `Lost network stack`, deixando PackageManager e StorageManager indisponíveis. As tentativas de instalar o APK para os testes da nova interface falharam no sistema antes de executar os testes. O emulador Android 9 alternativo também falhou na instalação com uma transação do serviço package interrompida. Não há /dev/kvm no host, e a execução em CPU emulada não é fiável neste runtime. **Não se declara que os testes de interface da 0.4.1 passaram enquanto não houver resultado correspondente.**

A transcrição real não foi validada num Android 13 com serviço/modelo pt-PT. O serviço do telefone precisa de suportar reconhecimento local a partir de ficheiro. Uma voz instalada em Saída de texto não confirma essa capacidade. As correções e o diagnóstico não são garantia de funcionamento em todos os serviços; não é necessário ditar ou cantar a letra ao Sonora. A origem pretendida é o áudio importado. Canto pode ser menos preciso do que voz falada.

Os testes Android da versão 0.4 anterior não substituem os testes da interface alterada nem comprovam a transcrição em Android 13. O APK é experimental e precisa de teste no aparelho.

Os registos desta atualização estão em docs/validation/0.4.1/. O teste de interface pode ser compilado sem dispositivo com `SONORA_UI_BUILD_ONLY=1 ./android/test-ui.sh`; executar sem essa variável exige Android/ADB funcional. Os 71 testes JVM passaram, mas não validam botões nem o reconhecimento de voz real.
