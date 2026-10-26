# Relatório de testes

## Servidor — execução local em 26-10-2026

Comando: `python -m unittest discover -s server/tests -v`

**16 testes aprovados, 0 falhas, 0 ignorados; 1,584 segundos.** Python 3.12, Linux, FFmpeg disponível.

Verificados: autenticação/rate-limit; cifragem de chaves e ausência em respostas; descoberta Ollama, chat, pesquisa e eliminação de histórico; consentimento remoto e bloqueio de preços desconhecidos/pagos; failover após HTTP 429 e captura de quotas; contexto de projetos e registos inexistentes; upload, extração TXT, download e eliminação; validação URL; bindings ComfyUI; SRT e deslocamento temporal; tarefas concluídas/falhadas e recuperação após reinício; identificação explícita do plano determinístico.

**Exportação multimédia real:** o teste cria áudio senoidal e um clip azul como fixtures de teste, executa corte/efeito fade, montagem, música normalizada, legendas SRT queimadas no vídeo, gera MP4 e verifica com ffprobe resolução 720×1280, duração aproximada de 1 segundo e presença de vídeo/áudio. As fixtures são identificadas como sintéticas e só existem nos testes, não são apresentadas como geração IA.

As chamadas Ollama/OpenRouter usam HTTPX MockTransport para testar contratos, quotas e regras sem chaves ou custos. Isso não prova disponibilidade externa. Foram também testados o protocolo nativo Gemini (header de autenticação, descoberta, systemInstruction), o percurso ComfyUI prompt/history/download com outputs sintéticos explícitos e o limite/cancelamento da fila. Gemini e ComfyUI reais, workflows de vídeo, TTS Android e serviços de transcrição ainda não foram exercitados contra serviços/dispositivos reais.

O cliente ASGI de testes usa um timer curto para permitir notificações de threads neste runtime restrito; não altera regras da aplicação nem abre sockets de rede. As rotas FastAPI síncronas continuam a executar no threadpool habitual.

## Android

Código inclui três testes JVM do domínio SRT/timeline. Compilação Kotlin/Compose, esses testes e lint estão configurados no workflow GitHub Actions. O ambiente local não dispõe de SDK/Gradle; não se considera compilação nem testes Android aprovados até obter a execução CI correspondente.

Ainda necessário: instalação em Android físico, navegação, Keystore, permissões, SAF, playback, TLS com CA local, USB reverse, ciclo de vida, desempenho e notificações. Não há emulador nem testes instrumentados nesta entrega.
