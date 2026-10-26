# AI Hub Studio v0.3.2 — relatório de entrega

Versão Android **0.3.2**, versionCode **5**. Base: ponta mais recente disponível no GitHub antes desta alteração, `e38df4fb7dfe931d6c40245ec657b236c6508cc7`. Código executável validado: `5011523eeeb44585d552d75a2d67f70412aea9c0`.

[Workflow final 40402688948](https://github.com/efog82/app-videos-musicas/actions/runs/40402688948): os cinco jobs ficaram aprovados. O job Linux teve inicialmente timeout na instalação de FFmpeg e foi repetido, sem alteração do servidor. A compilação final, o lint e a assinatura do APK passaram.

## Alterações — apenas três melhorias

1. **Eliminar imagens com confirmação.** Novo caixote do lixo acessível no canto da imagem, mantendo a linha anterior de ações. Cancelar não altera os dados. Confirmar remove a imagem e as referências no histórico, preserva o texto das conversas e elimina a cópia privada quando não é partilhada por outro registo. Os originais e as cópias da galeria Android não são apagados.
2. **Escolher da galeria.** Android Photo Picker, pré-visualização automática e cópia privada sem recompressão/redimensionamento. A referência pode ser enviada aos modelos de vídeo compatíveis existentes. Upload por streaming com extensão/MIME corretos e bytes preservados. A escolha e a pré-visualização não iniciam geração nem enviam a fotografia a uma IA.
3. **Ícone AI Hub.** Fundo escuro, símbolo AI/circuito e gradiente azul/violeta. Ícones adaptativos normal/redondo, fallback vetorial e camada monocromática para Android 13+.

[Funcionamento e detalhes](ALTERACOES-v0.3.2.md).

## Testes executados

### Android 11/API 30 — 8 testes, 0 falhas, 0 erros, 0 ignorados

- Confirmação de eliminação: Cancelar mantém ficheiro/registo; Confirmar remove a cópia privada e todas as referências multimédia nas conversas, mantendo o texto e as preferências cifradas.
- Importação: bytes originais idênticos, dimensões não quadradas mantidas, imagem e ficheiro persistentes após recriar a Activity.
- Multipart: ficheiro original inteiro e sem recompressão dentro do corpo de upload; comprimento e extensão corretos.
- Segurança da eliminação: cópias privadas partilhadas só desaparecem após remover o último registo; caminhos fora de `files/media` e originais da galeria continuam intactos.
- Chat público: descoberta e resposta real persistida.
- Regressão multimédia: gerações reais Pollinations e FLUX; LTX imagem→vídeo; imagens no chat, primeiro fotograma no leitor, exportação e persistência.
- Projetos e ligação HTTPS/token/pin ao servidor PC: passaram.
- **Referência importada da galeria → Wan real:** original lido por ContentResolver, cópia byte a byte, seleção no formulário, upload, receção do MP4, reprodução, exportação, URI de partilha e projeto persistente. Resultado: **480×832, 2 063 ms, 107 084 bytes**; o original continuou legível e inalterado.

As imagens de entrada e os dados dos testes de armazenamento são fixtures claramente identificadas. As gerações Pollinations/FLUX/LTX/Wan e a resposta de chat foram pedidos reais, não respostas simuladas.

### Android 15/API 35 — Photo Picker real

**1 teste, 0 falhas, 0 erros, 0 ignorados**. O botão da aplicação abriu a ação Android `android.provider.action.PICK_IMAGES`. A fotografia foi selecionada no seletor do sistema; o resultado foi importado e pré-visualizado na app, com **361×619** e bytes originais idênticos. A ação Animar imagem mostrou a referência selecionada. O ícone carregado pelo PackageManager era adaptativo e continha a camada monocromática.

### Outros controlos

- **6 testes JVM** aprovados; lint sem erros.
- Comparação da árvore Git: nenhuma alteração fora de AI Hub e do seu workflow. Sonora não foi modificado.
- `MediaProviders.kt`, `DirectAi.kt`, `ProviderSettings.kt`, `LegacyActivity.kt` e `LocalData.kt` idênticos à base, por SHA de blob Git. Catálogo de modelos/fornecedores, definições avançadas, esquema da base e código do Keystore preservados.
- Nenhuma permissão de leitura geral da galeria foi acrescentada. A importação recebe apenas o URI escolhido pelo utilizador.
- A primeira compilação detetou uma declaração duplicada do ícone no manifesto; corrigida. O lint pediu `SdkSuppress` no teste API 35; corrigido, sem desativar o controlo.

Evidências: [resumo dos testes](evidence-v032/tests-summary.json), [XML Android 11](evidence-v032/android11-tests.xml), [XML Android 15](evidence-v032/android15-tests.xml), [Photo Picker](evidence-v032/photo-picker-real.json), [Wan com referência importada](evidence-v032/reference-wan-real.json), [comparação de código preservado](evidence-v032/preserved-source.json).

## APK e atualização

Ficheiro: `AI-Hub-Studio-v0.3.2-debug.apk`.
Tamanho: 21122571 bytes.
SHA-256: `6c93becfa36a758ea8caab878ed38b95ff803b8df38ec7be1c4153bdfbcf38c1`.
Certificado SHA-256: `9f2c433083b8383e49c1da36b28adaa758c60122f1d410f2025efce77875d23b`, igual ao APK anterior entregue. Instalar por cima mantém os dados; não é necessário desinstalar. A assinatura é de desenvolvimento.

[APK no GitHub Actions](https://github.com/efog82/app-videos-musicas/actions/runs/40402688948/artifacts/12689069202).

## Limites da verificação

Ensaios em emuladores Android 11 e Android 15, sem telefone físico nesta sessão. O fallback para o seletor de documentos em aparelhos sem Photo Picker é fornecido pelo contrato oficial AndroidX, mas não foi exercitado como seletor nesta execução. A preservação byte a byte foi testada com PNG; a implementação copia os streams dos restantes formatos suportados sem os recodificar. O fornecedor remoto pode redimensionar/recortar a referência conforme os limites do seu modelo, já existentes.

As APIs autenticadas sem credenciais e todas as ferramentas avançadas não foram novamente ensaiadas em serviços externos nesta atualização; o respetivo código foi preservado. A partilha foi validada pela Intent e leitura do URI, sem enviar ficheiros a pessoas. O primeiro fotograma é confirmado pelo evento do leitor; não se usa captura de superfície de vídeo como prova de píxeis.
