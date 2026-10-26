# Funcionalidades, requisitos e limites

| Componente | Implementação | Validação / dependência |
|---|---|---|
| Android | Kotlin, Compose, tema escuro e dez áreas | Compilação e lint pelo workflow; teste físico ainda necessário |
| Histórico e projetos | SQLite Android e SQLite PC; pesquisa local/PC | Testes API e persistência; UI Android requer dispositivo |
| Credenciais | Android Keystore; Fernet no PC | Testes de cifragem e ausência em respostas API |
| Ollama | `/api/tags`, `/api/chat`; direto Android ou via PC | Testes de contrato com respostas controladas; requer Ollama real e pesos |
| LM Studio / llama.cpp | Compatibilidade OpenAI `/models` e `/chat/completions` | Contratos testados; servidor compatível necessário |
| OpenRouter | Descoberta, preços, chat, política gratuita | Contratos testados; chave/plano, disponibilidade e termos atuais |
| Gemini | `/models` e `generateContent`, systemInstruction | Adaptador implementado, não testado com credenciais reais |
| Groq / Mistral / HF / Cerebras / SambaNova | Predefinições de API compatível OpenAI, endpoint extensível | Autenticação, modelos e quotas variam; não testados externamente |
| Quotas | Cabeçalhos ratelimit e retry-after, cooldown e failover | Teste 429; períodos não documentados ficam desconhecidos |
| Crédito monetário | Não implementado | Não inventa saldos nem quota diária/mensal |
| Sem chave | Ollama, endpoints locais, TTS Android, SRT local | Nenhum acesso indevido a interfaces privadas; links oficiais abrem navegador |
| ComfyUI imagem/vídeo/áudio | Importação de workflows API, binding, upload, prompt, history, download | Validação de bindings testada; geração real requer serviço, nós, pesos e hardware |
| Consistência, animação corporal, in/out frames, extensão, upscaling, fundos | Encaminháveis através de workflows adequados | Sem workflows ou garantias específicas incluídos; controlos dependem de bindings |
| Editor | Uma sequência visual, uma faixa musical, SRT, corte, reorder, fade, preto/branco, MP4 H.264/AAC | Teste FFmpeg real de ficheiro, música e legendas; não é timeline multipista completa |
| Legendas | Importação, edição, desvio e exportação SRT | Testes de roundtrip, tempos e exportação |
| Transcrição | OpenAI-compatible multipart, formato SRT | Requer modelo/endpoint que devolva SRT; sem teste externo |
| TTS | Voz do Android e reprodução | Requer motor/voz pt-PT instalados; sem exportação de narração nesta versão |
| Áudio | RMS, picos, estimativa BPM, normalização loudnorm | Testes FFmpeg; não reconhece estrutura musical semântica ou letras |
| Agente | Plano determinístico de cenas e requisitos | Testado; não é agente autónomo de execução ponta a ponta |
| Documentos | Extração PDF/TXT/SRT/JSON para prompt | Texto PDF até 100 páginas / 200 mil caracteres; sem OCR ou análise visual |
| Multimodal chat | Metadados de capacidades de imagem OpenRouter | Envio de imagens ao chat ainda não implementado |
| Programação | Chat dedicado com contexto e histórico | Não executa código gerado; não existe sandbox de execução |
| Pesquisa internet | Links oficiais abertos por intenção Android | Não há ferramenta de pesquisa integrada ao raciocínio |
| Ligação PC | Token, USB reverse ou LAN TLS com IP explícito | Sem descoberta mDNS ou configuração automática de rede |
| Tarefas | Fila única de 20 tarefas, persistência, erro e reinício, cancelamento em espera | Testes unitários; em execução não cancelável |
| Notificações | Polling Android enquanto app aberta | Sem monitorização em background com app fechada |
| GitHub | Workflow de testes, lint, assinatura debug, APK e relatórios | Artefactos só após workflow bem-sucedido |

## Ainda por implementar

Editor multipista com sobreposições visuais, crossfades reais, keyframes, pan/zoom/rotação, equalizadores animados, partículas/fractais/caleidoscópios; alinhamento de palavras/letras a música; separação de fontes incluída e validada; SDKs dedicados de vídeo remoto; execução autónoma completa do agente; inferência de pesos no Android; descoberta de servidores LAN; pesquisa web integrada; análise visual no chat; gestão detalhada de créditos; cliente web/desktop; assinatura estável de produção e testes instrumentados Android.

Não se apresenta nenhuma destas funcionalidades como concluída. Os adaptadores são código real, mas testes de contrato com fornecedores controlados não equivalem a chamadas bem-sucedidas a serviços públicos atuais. Termos, preços, licenças e quotas devem ser confirmados nos fornecedores no momento de utilização. Dependências e projetos possuem licenças próprias; os pesos não são distribuídos com esta aplicação.
