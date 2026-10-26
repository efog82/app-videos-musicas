package pt.aihub.studio

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Actual Android-to-provider adapters. No PC, app developer key, wallet or billing fallback. */
data class AiProvider(val id:String,val name:String,val url:String,val protocol:String,val keyUrl:String,val info:String,val local:Boolean=false)
val aiProviders = listOf(
    AiProvider("pollinations","Pollinations — sem chave","https://text.pollinations.ai","classic","https://github.com/pollinations/pollinations/blob/master/APIDOCS.md","Texto gratuito anónimo; um pedido de cada vez, intervalo mínimo de 30 s. Serviço de melhor esforço; não há pagamento automático."),
    AiProvider("gemini","Google Gemini — quota gratuita","https://generativelanguage.googleapis.com/v1beta","gemini","https://aistudio.google.com/apikey","Chave própria de projeto Free Tier, sem faturação. Os modelos e limites dependem da conta; não ative faturação."),
    AiProvider("groq","Groq — plano gratuito","https://api.groq.com/openai/v1","openai","https://console.groq.com/keys","Chave do plano Free. Pedidos/minuto, dia e tokens sujeitos aos limites da organização."),
    AiProvider("openrouter","OpenRouter — modelos gratuitos","https://openrouter.ai/api/v1","openai","https://openrouter.ai/settings/keys","Só modelos com todos os preços publicados a zero. Chave gratuita própria; quotas por minuto e dia."),
    AiProvider("mistral","Mistral — modo gratuito","https://api.mistral.ai/v1","openai","https://console.mistral.ai/","Modo gratuito da conta; chave própria e limites de utilização. Não ative o plano pago."),
    AiProvider("huggingface","Hugging Face — créditos gratuitos","https://router.huggingface.co/v1","openai","https://huggingface.co/settings/tokens","Token com permissão de inferência. Os créditos são limitados e não são uma quota gratuita ilimitada."),
    AiProvider("cerebras","Cerebras — plano gratuito","https://api.cerebras.ai/v1","openai","https://cloud.cerebras.ai/","Conta e chave próprias; confirme o plano gratuito e os modelos disponíveis."),
    AiProvider("sambanova","SambaNova — acesso da conta","https://api.sambanova.ai/v1","openai","https://cloud.sambanova.ai/","Confirme se a conta tem acesso/créditos gratuitos. Não se presume custo zero pela existência de uma chave."),
    AiProvider("ollama","Ollama — local sem chave","http://127.0.0.1:11434","ollama","https://ollama.com/","Modelo instalado no PC: por USB use adb reverse tcp:11434 tcp:11434. Não precisa do servidor AI Hub.",true),
    AiProvider("lmstudio","LM Studio / llama.cpp — local","http://127.0.0.1:1234/v1","openai","https://lmstudio.ai/","Servidor local compatível OpenAI, pesos e hardware próprios. Por USB redirecione a porta 1234.",true)
)

data class FreeService(val name:String,val modality:String,val url:String,val note:String)
val freeServices = listOf(
    FreeService("Gemini","Chat, visão, código","https://gemini.google.com/","Interface oficial; conta e limites próprios"),
    FreeService("Google AI Studio","Modelos e multimédia","https://aistudio.google.com/","Interface oficial e chaves gratuitas elegíveis"),
    FreeService("Groq Playground","Chat e código","https://console.groq.com/playground","Interface oficial, plano da conta"),
    FreeService("OpenRouter Chat","Chat com modelos gratuitos","https://openrouter.ai/chat","Escolha modelos gratuitos; conta própria"),
    FreeService("Hugging Face Spaces","Imagem, vídeo, áudio e ferramentas","https://huggingface.co/spaces","Cada Space tem licença, quota e disponibilidade próprias"),
    FreeService("Le Chat Mistral","Chat e documentos","https://chat.mistral.ai/","Plano gratuito da interface oficial"),
    FreeService("Microsoft Copilot","Chat e imagem","https://copilot.microsoft.com/","Interface oficial; funcionalidades e limites da conta"),
    FreeService("Perplexity","Pesquisa com IA","https://www.perplexity.ai/","Interface oficial, plano gratuito limitado"),
    FreeService("DeepSeek","Raciocínio e código","https://chat.deepseek.com/","Interface oficial; a API é um produto separado"),
    FreeService("Qwen Chat","Chat e multimédia","https://chat.qwen.ai/","Interface oficial; disponibilidade por região/conta"),
    FreeService("Suno","Música","https://suno.com/","Créditos gratuitos sujeitos a termos e licença de utilização"),
    FreeService("Kling","Vídeo generativo","https://klingai.com/","Créditos, região e filas dependem da conta"),
    FreeService("Luma Dream Machine","Vídeo generativo","https://lumalabs.ai/dream-machine","Verifique o plano atual; não se garante quota gratuita"),
    FreeService("Pika","Vídeo generativo","https://pika.art/","Plano, créditos e filas próprios"),
    FreeService("Pollinations","Texto, imagem e outros modelos","https://pollinations.ai/","API Classic gratuita; outras modalidades têm condições diferentes")
)

class DirectAi(private val vault:Vault) {
    private val nextUse = mutableMapOf<String,Long>()
    private suspend fun waitUntil(deadline:Long,message:String,onWait:(String)->Unit) {
        while(System.currentTimeMillis()<deadline) {
            val remaining=deadline-System.currentTimeMillis()
            onWait("$message (${remaining/1000+1}s)…")
            delay(minOf(remaining,1000).coerceAtLeast(1))
        }
    }
    fun provider(id:String) = aiProviders.firstOrNull {it.id==id} ?: aiProviders.first()
    private fun api(p:AiProvider):Api = Api(
        {if(p.id=="ollama")vault.get("ollama").ifBlank{p.url} else p.url},
        {if(p.protocol=="gemini"||p.protocol=="classic")"" else vault.get("key_${p.id}")},
        {if(p.protocol=="gemini")mapOf("x-goog-api-key" to vault.get("key_${p.id}")) else emptyMap()})
    fun hasKey(p:AiProvider) = p.local || p.protocol=="classic" || vault.get("key_${p.id}").isNotBlank()
    fun cachedModels():List<JSONObject> = runCatching{JSONArray(vault.get("direct_catalog")).objects()}.getOrDefault(emptyList())
    private fun saveModels(id:String,rows:List<JSONObject>) {
        val all=cachedModels().filter {it.optString("provider")!=id}+rows
        vault.set("direct_catalog",JSONArray(all).toString())
    }
    suspend fun discover(id:String):List<JSONObject> {
        val p=provider(id)
        require(hasKey(p)) {"${p.name}: crie uma chave gratuita no endereço oficial e guarde-a primeiro"}
        val client=api(p)
        val source=when(p.protocol) {
            "classic" -> JSONArray(client.request("/models"))
            "ollama" -> client.obj("/api/tags").getJSONArray("models")
            "gemini" -> client.obj("/models").optJSONArray("models") ?: JSONArray()
            else -> client.obj("/models").optJSONArray("data") ?: JSONArray()
        }
        val rows=source.objects().mapNotNull { row ->
            val model=row.optString("id").ifBlank{row.optString("name").removePrefix("models/")}
            if(model.isBlank())return@mapNotNull null
            if(p.protocol=="gemini" && !(row.optJSONArray("supportedGenerationMethods")?.toString()?.contains("generateContent") ?: false))return@mapNotNull null
            if(p.protocol=="openai" && model.contains("whisper",true))return@mapNotNull null
            val pricing=row.optJSONObject("pricing")
            val zero=pricing!=null&&pricing.has("prompt")&&pricing.has("completion")&&pricing.keys().asSequence().all{pricing.optString(it).toDoubleOrNull()==0.0}
            if(p.id=="openrouter" && !zero)return@mapNotNull null
            JSONObject().put("id",model).put("provider",p.id).put("name",row.optString("name",model)).put("free",p.local||p.protocol=="classic"||zero)
                .put("verified",false).put("observed",System.currentTimeMillis()).put("capabilities",JSONArray().put("chat")).put("pricing",pricing?:JSONObject())
        }
        saveModels(id,rows)
        return cachedModels()
    }
    fun automaticModels():List<JSONObject> = cachedModels().filter {
        it.optBoolean("verified") && System.currentTimeMillis()-it.optLong("verified_at")<1800000 && hasKey(provider(it.getString("provider")))
    }
    private fun verification(id:String,model:String,ok:Boolean) {
        val rows=cachedModels().map { if(it.optString("provider")==id && it.optString("id")==model) it.put("verified",ok).put("verified_at",System.currentTimeMillis()) else it }
        vault.set("direct_catalog",JSONArray(rows).toString())
    }
    suspend fun test(id:String,selection:String="",consent:Boolean,freePlan:Boolean):String {
        discover(id)
        val model=selection.ifBlank { cachedModels().firstOrNull{it.optString("provider")==id}?.optString("id").orEmpty() }
        require(model.isNotBlank()) {"Nenhum modelo de chat disponível neste fornecedor"}
        val answer=chat("$id|$model",listOf(JSONObject().put("role","user").put("content","Responde apenas OK.")),consent,freePlan)
        return "${provider(id).name} / $model: resposta real recebida: ${answer.take(160)}"
    }
    suspend fun chat(selection:String,messages:List<JSONObject>,consent:Boolean,freePlan:Boolean,onWait:(String)->Unit={}):String {
        if(selection.isBlank()) {
            val candidates=automaticModels()
            require(candidates.isNotEmpty()) {"Não há modelos testados e acessíveis. Use Configuração inicial > Descobrir e testar IA."}
            val failures=mutableListOf<String>()
            for(m in candidates) try {
                return chat(m.getString("provider")+"|"+m.getString("id"),messages,consent,freePlan,onWait)
            } catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) { failures.add(provider(m.getString("provider")).name+": "+connectionMessage(e)) }
            error(failures.joinToString("\n"))
        }
        var id=selection.substringBefore('|',"pollinations")
        var model=selection.substringAfter('|',"")
        if(selection.isBlank()) {id="pollinations";model=""}
        val p=provider(id)
        require(p.local||consent) {"Autorize o envio do pedido ao serviço externo na opção acima"}
        require(hasKey(p)) {"Configure primeiro a chave gratuita de ${p.name} em Modelos"}
        if(!p.local&&p.protocol!="classic"&&p.id!="openrouter")require(freePlan||vault.get("free_${p.id}")=="true") {"Confirme que a conta está no plano gratuito e sem faturação em Modelos"}
        if(model.isBlank()) {
            discover(id)
            model=cachedModels().firstOrNull{it.optString("provider")==id}?.getString("id") ?: error("O fornecedor não devolveu modelos utilizáveis")
        }
        if(p.id=="openrouter") {
            val m=cachedModels().firstOrNull{it.optString("provider")==id&&it.optString("id")==model}
            require(m!=null&&m.optBoolean("free")&&System.currentTimeMillis()-m.optLong("observed")<3600000) {"Atualize o catálogo gratuito OpenRouter antes de enviar"}
        }
        if(p.protocol=="classic") {
            val wait=(nextUse[id]?:0)-System.currentTimeMillis()
            if(wait>0)waitUntil(System.currentTimeMillis()+wait,"A aguardar a próxima quota gratuita",onWait)
            nextUse[id]=System.currentTimeMillis()+31000
        }
        val body=JSONObject().put("model",model).put("messages",JSONArray(messages)).put("stream",false)
        if(p.id=="openrouter")body.put("provider",JSONObject().put("max_price",JSONObject().put("prompt",0).put("completion",0)))
        val client=api(p)
        try {
        suspend fun complete():String=when(p.protocol) {
            "ollama"->client.obj("/api/chat","POST",body).getJSONObject("message").getString("content")
            "gemini"->{
                val contents=messages.filter{it.optString("role")!="system"}.map {JSONObject().put("role",if(it.optString("role")=="assistant")"model" else "user").put("parts",JSONArray().put(JSONObject().put("text",it.getString("content"))))}
                val b=JSONObject().put("contents",JSONArray(contents))
                val system=messages.filter{it.optString("role")=="system"}.joinToString("\n"){it.optString("content")}
                if(system.isNotBlank())b.put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",system))))
                client.obj("/models/$model:generateContent","POST",b).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.objects()?.joinToString(""){it.optString("text")}?.takeIf{it.isNotBlank()} ?: error("O modelo não devolveu texto; verifique os filtros e a disponibilidade")
            }
            else->client.obj(if(p.protocol=="classic")"/openai" else "/chat/completions","POST",body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
        val result=try{complete()}catch(e:AiHttpFailure){
            if(p.protocol!="classic"||e.status !in listOf(402,429)||e.retryAfter==null||e.retryAfter !in 1..120)throw e
            waitUntil(System.currentTimeMillis()+(e.retryAfter+1)*1000,"O serviço pediu espera antes da próxima quota gratuita",onWait)
            complete() // One retry only, after the provider's exact Retry-After. No payments.
        }
        if(p.protocol=="classic")nextUse[id]=System.currentTimeMillis()+31000
        require(result.isNotBlank()) {"O modelo não devolveu uma resposta textual"}
        verification(id,model,true)
        return result
        } catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) {verification(id,model,false);throw e}
    }
    suspend fun image(prompt:String,width:Int,height:Int,seed:Long,consent:Boolean,onWait:(String)->Unit):ByteArray {
        require(consent){"Autorize o envio das instruções ao Pollinations"}
        val wait=(nextUse["image"]?:0)-System.currentTimeMillis()
        if(wait>0)waitUntil(System.currentTimeMillis()+wait,"A aguardar a quota de imagem",onWait)
        nextUse["image"]=System.currentTimeMillis()+46000
        val path="/prompt/"+URLEncoder.encode(prompt,"UTF-8").replace("+","%20")+"?width=$width&height=$height&seed=$seed&safe=true"
        return Api({"https://image.pollinations.ai"},{""}).binary(path)
    }
}
