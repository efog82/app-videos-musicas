# AI Hub Studio v0.3.2

Esta atualização aplica apenas três melhorias à ponta mais recente do ramo `ai-hub-studio/implementation` disponível no GitHub: base `e38df4fb7dfe931d6c40245ec657b236c6508cc7`. Os ficheiros de execução locais foram comparados com a árvore Git antes das alterações. Não foi usado um APK anterior como fonte nem repostos ficheiros de versões antigas.

Versão Android: **0.3.2**, versionCode **5**, applicationId `pt.aihub.studio`.

## 1. Eliminar imagens com confirmação

O caixote do lixo fica acessível no canto de cada imagem e o botão anterior continua na linha de ações. Surge uma confirmação antes de eliminar; Cancelar mantém os dados.

A operação remove o registo da imagem, as referências multimédia nas conversas e a cópia privada em `files/media` quando já não é utilizada por outro registo. O texto das conversas é preservado. Ficheiros partilhados entre registos só são removidos depois do último. Referências em tarefas concluídas são limpas; imagens ainda usadas por tarefas em curso exigem cancelar ou aguardar a tarefa.

A app **não elimina ficheiros da galeria Android**. Caminhos antigos ou externos à pasta de multimédia gerida não são apagados. A cópia exportada para a galeria e a fotografia original selecionada permanecem intactas.

## 2. Escolher da galeria

Na área Imagens, **Escolher da galeria** abre o Android Photo Picker, limitado a imagens. Em Androids onde o Photo Picker não esteja disponível, o contrato oficial AndroidX usa o seletor de documentos do sistema. Não foram adicionadas permissões de leitura geral da galeria.

A imagem é copiada para armazenamento privado em segundo plano, sem redimensionar, recomprimir ou alterar os bytes recebidos do Android. A pré-visualização abre automaticamente. Pode ampliar a imagem ou tocar em **Animar imagem**, escolhendo um modelo compatível, como LTX ou Wan. Também fica disponível na seleção de referências da área Vídeos e nos projetos.

Escolher ou pré-visualizar não envia a fotografia para um fornecedor de IA. Só o pedido de geração autorizado envia a referência ao fornecedor escolhido. O upload transmite o ficheiro original por streaming, com o tipo/extensão corretos, sem duplicar a imagem inteira na memória. O modelo remoto pode ajustar o resultado à sua própria resolução/proporção; esse processamento é distinto da importação e da pré-visualização originais.

Geradores que apenas aceitam texto continuam a não receber imagens como se suportassem edição por referência. Formatos que o Android ou o fornecedor não consiga descodificar são recusados com erro; não há conversão silenciosa com perda de qualidade.

## 3. Ícone AI Hub

Novo símbolo geométrico AI com circuito/hexágono, fundo escuro e gradiente azul/violeta. Recursos Android:

- Ícone adaptativo normal e redondo desde API 26.
- Camadas de fundo e primeiro plano em vetores.
- Camada monocromática para ícones temáticos desde API 33.
- Recursos vetoriais de fallback.

[Arte vetorial do símbolo](assets/aihub-icon-v0.3.2.svg).

## Preservação

Os separadores Chat, Imagens, Vídeos e Projetos, o catálogo de fornecedores/modelos e as definições avançadas foram mantidos. A base continua com o mesmo esquema e nome, sem migração destrutiva. As credenciais continuam no mesmo Keystore e preferências cifradas. Sonora está fora dos caminhos alterados.

Instale a atualização por cima da versão existente; não desinstale para preservar os dados privados. A continuidade da assinatura e os resultados de testes são registados no relatório final desta entrega.
