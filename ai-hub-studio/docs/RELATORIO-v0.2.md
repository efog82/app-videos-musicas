# Validação funcional v0.2.0 — em curso

## Ambiente local

24 testes descobertos: **19 aprovados, 5 ignorados, 0 falhas** (1,763 s). Os cinco ignorados são quatro fornecedores externos sem credenciais (Gemini, Groq, Mistral, OpenRouter) e o socket HTTPS real não autorizado neste sandbox. Não são apresentados como integrações reais aprovadas.

Os testes executados cobrem autenticação, cofre, contratos de fornecedores, retirada de modelos do automático, teste antes do automático, diagnósticos, identidade TLS estável com SAN/pin, fila, biblioteca, ComfyUI controlado, SRT e exportação FFmpeg real.

## Provas externas obrigatórias no CI

- Compilar APK e executar lint/testes JVM.
- Instalar em emulador e verificar persistência/navegação.
- Descobrir modelos e obter resposta real através do Android, sem mocks, da API pública Pollinations. Limites por IP respeitados. Resposta e resultado guardados num relatório; falha do serviço deve ser registada como falha, não como sucesso ou simulação.
- Abrir servidor HTTPS real, validar certificado, testar recusa sem token e resposta autenticada, em Linux e Windows.
- Analisar sintaxe dos scripts PowerShell. Isto não equivale a executar winget, firewall e instalação num Windows doméstico.

Os resultados e os links da execução serão acrescentados após terminar o CI. A existência de artefacto APK não é o critério de aceitação por si só.
