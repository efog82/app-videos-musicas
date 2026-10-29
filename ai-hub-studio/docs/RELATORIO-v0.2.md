# Relatório funcional v0.2.0 — 26-10-2026

**Execução integral aprovada:** https://github.com/efog82/app-videos-musicas/actions/runs/40314110724

Código executável testado: `090002e58ac755cb2762d068aad36187febf442d`.

## Critério de aceitação: aprovado no Android emulado

APK instalado em Android API 30. O teste `LiveProviderTest` usou a própria interface:

1. Abriu Configuração e autorizou o envio ao serviço público.
2. Descobriu e testou um modelo real Pollinations Classic, sem chave nem PC.
3. Abriu Chat, escreveu `Quanto é 2+2? Responde apenas 4.` e premiu Enviar.
4. Recebeu **`4`** do fornecedor e verificou que a conversa ficou guardada no SQLite Android.

O teste demorou **38,588 segundos**, incluindo a espera de quota. Não utiliza MockTransport, servidor simulado ou resposta fixa. Evidência extraída do logcat publicado pelo CI:

```text
REAL_NETWORK_UI_TEST
UI: discover and test returned a real model response
Question sent through UI: Quanto é 2+2?
Actual provider response: 4
PASS
```

Foi corrigida a contagem de espera entre o teste inicial e o pedido seguinte, com relógio real e uma repetição limitada apenas quando o fornecedor instrui uma espera Retry-After válida. A disponibilidade e quota futuras do serviço não são garantidas por este teste.

## Transporte e regressões

| Prova | Resultado |
|---|---|
| Compilação Kotlin/Compose, lint e assinatura debug | Aprovados |
| Testes JVM SRT/timeline | 3 aprovados |
| API/armazenamento/failover/quotas/FFmpeg/TLS em Linux | 24 descobertos, 20 aprovados, 4 ignorados por falta de chaves |
| Servidor/TLS e sintaxe PowerShell no Windows | 24 descobertos, 19 aprovados, 5 ignorados: quatro chaves ausentes e FFmpeg não instalado nesse runner |
| Android real no emulador → companion Python real no host, HTTPS `10.0.2.2:8787` | Aprovado; certificado/pin e token corretos aceites, pin e token incorretos recusados |
| Projeto local persistente após recriar atividade e navegação para Chat | Aprovado |
| Total instrumentado Android | **3 aprovados, 0 falhas, 0 ignorados**, 47,888 s |

O teste de transporte Android-PC abre sockets e TLS reais e não usa ASGITransport. O alias `10.0.2.2` é exclusivo do emulador e não é recomendado para telemóvel físico. Os testes ASGI e contratos com respostas controladas continuam identificados como tal. O teste FFmpeg em Linux gera um MP4 real com áudio/legendas a partir de fixtures sintéticas explícitas, não é geração por IA.

## Integrações ainda dependentes de configuração externa

- **Gemini, Groq, OpenRouter e Mistral:** adaptadores e testes reais incluídos, mas sem credenciais no ambiente; os quatro testes externos foram **ignorados**, não aprovados. Configure uma chave própria elegível no Android e use Descobrir e testar IA. Os contratos Gemini/OpenRouter/compatibilidade são testados separadamente com respostas controladas.
- **Hugging Face, Cerebras e SambaNova:** configurações diretas incluídas, contas/permissões/modelos/quota ainda precisam de teste real pelo utilizador.
- **Ollama:** protocolo e descoberta testados em contrato; inferência local não executada porque não há pesos/Ollama instalados neste ambiente. O instalador Windows oferece instalação/download explícitos.
- **ComfyUI imagem/vídeo/áudio:** transporte e integração existentes preservados. Execução real requer ComfyUI, nós, pesos, workflow válido e hardware. Não foi gerado vídeo por IA neste teste. Animação não é substituída por zoom.
- **Imagem pública Pollinations:** ligação acrescentada, mas geração de imagem não foi exercitada nesta execução; não confundir o teste de texto com teste de imagem.
- **Agente textual:** planeamento/execução por modelo implementados; não foi ensaiada uma cadeia completa nesta execução. O plano PC determinístico permanece identificado separadamente.
- **Descoberta mDNS, regras de firewall e instalação winget num Windows doméstico:** código e sintaxe validados, mas não há telemóvel físico na LAN do utilizador. A prova em emulador/TLS não equivale a um teste da rede Wi-Fi doméstica ou do instalador interativo completo.

## Artefactos

APK: `AI-Hub-Studio-v0.2.0-debug.apk`, **9 699 652 bytes**.

SHA256: `ba988c81c1de531455295097ff1ac8c103f0e01bfc0b0994dc69deab0c5e1489`.

[APK no GitHub](https://github.com/efog82/app-videos-musicas/actions/runs/40314110724/artifacts/12645748816) · [Servidor Windows](https://github.com/efog82/app-videos-musicas/actions/runs/40314110724/artifacts/12646311568) · [Relatórios Android/emulador](https://github.com/efog82/app-videos-musicas/actions/runs/40314110724/artifacts/12645783628)

Os artefactos Actions são ZIP e podem exigir sessão GitHub. Há cópias diretas do APK e pacote Windows entregues na conversa. A assinatura de desenvolvimento é diferente da v0.1.0; não é uma assinatura estável de produção. Não há chaves API no código ou APK.
