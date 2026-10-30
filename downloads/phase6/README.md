# Sonora Lab — prévia da Fase 6

APK experimental em validação; a Fase 6 ainda não está concluída.

[Descarregar APK](Sonora-Lab-fase6-preview.apk)

Android 8 ou superior. Pacote `pt.sonora.editor`, versão `0.6.0-phase5-experimental`, código 65. O sufixo phase5 indica que todos os motores anteriores permanecem ativos; esta é uma prévia da interface da Fase 6.

Assinatura SHA-256: `cd923774b384cee98f9971ece24c3270ed298f63fbdf207db10ab79ac08b0941`, igual à do APK 0.4.1 existente. Instalação de atualização confirmada no emulador de teste, sem desinstalação. Não desinstalar a versão anterior no telefone para atualizar.

APK SHA-256: `4e36b7bddb29502613e6a4acc48aad5df2aff591d9cb7ebe9639299b886090ba`.

## Verificação desta prévia

- Compilação e verificação de assinatura passaram.
- 46 verificações de projeto/áudio/timing e 36 de SRT passaram no ambiente Java.
- Instalação e abertura do ecrã inicial/editor observadas em emulador Android 8/API 26.
- Ainda não foram validados nesta revisão todos os fluxos de importação, reprodução, exportação/cancelamento e interação com a timeline. Os testes instrumentados anteriores precisam de adaptação à nova arquitetura.
- O emulador sem aceleração apresentou um bloqueio do System UI; não constitui validação de estabilidade no telefone.
- Não foi testado um modelo português de reconhecimento local. A disponibilidade depende do motor Android e do modelo instalado.
- Velocidade de clips, recorte de imagem e movimento por imagem ainda têm limitações explicitadas na interface.

Não foram substituídos APKs anteriores. A branch main não foi alterada. Os ficheiros de média originais são referenciados pelo projeto, não apagados pelas operações de edição.

Arquitetura e integração: [documentação](../../docs/phase6/ARCHITECTURE.md).
