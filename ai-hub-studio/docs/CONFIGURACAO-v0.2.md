# Configuração v0.2.0

## Começar sem PC nem chave

1. Abra **Configuração**, mantenha **Ligar diretamente às IAs** ativo e escolha **Pollinations — sem chave**.
2. Leia as condições e autorize o envio ao serviço público.
3. Prima **Descobrir e testar IA**. O teste envia uma mensagem mínima real; deve aparecer o modelo e a resposta recebida.
4. Abra Chat, escreva a pergunta e envie. A API pública impõe um intervalo: a app mostra a espera, sem contornar o limite. HTTP 402/429 não significa que tem de pagar; pode aguardar ou escolher outro fornecedor configurado.

Este serviço é de melhor esforço e partilha limites por IP. Se estiver indisponível não se deve declarar a integração aprovada apenas por conseguir obter o catálogo.

## Chave gratuita (sem PC)

Na mesma configuração escolha Gemini, Groq, OpenRouter ou outro fornecedor. **Obter chave** abre o site oficial. Crie a chave na sua conta e cole-a no campo protegido. Não envie chaves por chat/GitHub. Confirme que não há faturação automática, autorize envio, e execute **Descobrir e testar IA**. Apenas uma resposta bem-sucedida habilita o automático. Para OpenRouter só são listados preços zero; para outros a elegibilidade real depende da conta/modelo/quota.

- Gemini: https://aistudio.google.com/apikey — documentação: https://ai.google.dev/gemini-api/docs/billing
- Groq: https://console.groq.com/keys — limites: https://console.groq.com/docs/rate-limits
- OpenRouter: https://openrouter.ai/settings/keys — https://openrouter.ai/pricing
- Mistral: https://console.mistral.ai/ — https://docs.mistral.ai/getting-started/quickstarts/studio/activate-and-generate-api-key
- Público Pollinations: https://github.com/pollinations/pollinations/blob/master/APIDOCS.md

Chaves gratuitas não são distribuídas no APK. A app não ativa faturação, não compra créditos nem muda de plano.

## Windows e telemóvel na mesma rede

1. Descompacte o pacote PC numa pasta. Execute **Instalar-e-Iniciar.cmd**. Para criar regras de firewall execute como administrador uma vez. Pode ser necessário reabrir depois da instalação inicial de Python para atualizar o PATH.
2. A rede doméstica deve estar marcada **Privada** no Windows. O script não abre portas em redes públicas e não altera o router.
3. O script mostra o IP, gera TLS/token e abre `emparelhamento.json` no Bloco de Notas. Mantenha o servidor aberto.
4. Android: Definições → **Procurar PC na rede**. Selecione o endereço, compare a impressão digital no PC, introduza token e confirme a comparação. Alternativa: copie os três valores do JSON ou preencha o IP manualmente.
5. Desative o modo IA direta apenas se quer encaminhar o chat pelo PC. As ferramentas PC podem coexistir com chat direto: guarde e teste a ligação PC uma vez; depois pode voltar a escolher o chat direto.
6. **Guardar e testar** verifica que é um servidor AI Hub, autentica e mostra diagnósticos. Em Modelos, descubra e teste Ollama/ComfyUI. ComfyUI acessível não prova que pesos/workflows estão instalados.

Instalar e descarregar um modelo de texto local explicitamente:

```cmd
Instalar-e-Iniciar.cmd -InstallOllama -Model qwen3:1.7b
```

O download depende de Internet, licença, espaço e disponibilidade do identificador; confirme-o no catálogo Ollama. Não é preciso expor Ollama à rede: no PC o bridge usa `127.0.0.1:11434`. Para escolher a placa de rede:

```cmd
Instalar-e-Iniciar.cmd -BindAddress 192.168.1.20
```

ComfyUI: `-InstallComfyUI` abre o instalador oficial. Escolha pesos/workflows adequados; não se assume suporte a vídeo pesado num Ryzen/Radeon integrado com 16 GB.

## USB (alternativa explícita)

```sh
python -m server.launch
adb reverse tcp:8787 tcp:8787
adb reverse tcp:11434 tcp:11434
```

**Só neste modo** `http://127.0.0.1:8787` no Android chega ao servidor por USB. Sem reverse aponta para o próprio telemóvel. O modo USB local não usa pin TLS; limpe o campo impressão digital e use o token do PC. O Ollama direto usa a porta 11434 redirecionada. O emulador Android usa `10.0.2.2` para o host; não se aplica a um telemóvel físico.

## Diagnóstico

- Nenhum PC: servidor fechado, firewall, multicast bloqueado, redes diferentes, VPN ou isolamento Wi-Fi. Experimente IP manual; mDNS é apenas descoberta.
- Ligação recusada: confirme IP, porta, processo e firewall privada.
- TLS: confirme relógio e pin; alteração de IP/certificado pode exigir novo emparelhamento. Nunca ignore um pin diferente sem o comparar no PC.
- HTTP 401: token PC ou chave API inválidos/em falta. São credenciais diferentes.
- HTTP 403: permissões, região ou modelo não autorizado.
- HTTP 404: endpoint errado ou modelo removido/não instalado.
- HTTP 402/429: limite/quota; respeite o período indicado. Não há pagamento automático.
- HTTP 5xx: fornecedor indisponível. O automático só tenta outras opções verificadas e configuradas.
