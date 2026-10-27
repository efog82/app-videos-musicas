package pt.aihub.studio

import android.content.Context
import pt.aihub.core.videoFailure
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID

class MediaGenerationFailure(message:String):java.io.IOException(message)

/** Direct, authorised remote inference. No local engine, credit purchase or website automation. */
class OnlineMedia(private val context:Context,private val vault:Vault) {
    private val quota=MediaStatusStore(LocalData(context))
    private var activeProvider="";private var activeModel=""
    private var lastResponseHeaders=emptyMap<String,String>()
    private val http=WebTransport {status,headers,body->
        lastResponseHeaders=headers
        if(activeProvider.isNotBlank()&&(status !in 200..299||headers.keys.any{it.contains("ratelimit")||it=="retry-after"})) {
            quota.put(activeProvider,activeModel,pt.aihub.core.MediaQuotaParser.parse(activeProvider,status,headers,body,System.currentTimeMillis()))
        }
    }
    private var nextClassic=0L
    private var cancellation:(suspend()->Unit)?=null
    var cancellationSupported=false;private set
    private fun auth(p:MediaProvider):Map<String,String> {
        val key=if(p.id=="cloudflare")vault.get("media_cloudflare_key") else vault.get("key_huggingface")
        return if(key.isBlank())emptyMap() else mapOf("Authorization" to "Bearer $key")
    }
    private var verifiedFreeHfKey=""
    private suspend fun requireFreeHuggingFace(p:MediaProvider) {
        if(!p.id.startsWith("hf-"))return
        val token=vault.get("key_huggingface")
        if(token.isBlank())return
        val identity=CloudflareImages.hash(token.toByteArray())
        if(identity==verifiedFreeHfKey)return
        val account=http.json("https://huggingface.co/api/whoami-v2",headers=mapOf("Authorization" to "Bearer $token"))
        require(account.optString("type")=="user"&&account.has("isPro")&&!account.optBoolean("isPro",true)) {
            "Só é permitido usar tokens de uma conta Hugging Face Free verificada. Contas PRO/organizações podem consumir créditos automaticamente; use acesso anónimo ou um token gratuito."
        }
        verifiedFreeHfKey=identity
    }
    data class ApiInfo(val prefix:String,val endpoint:String,val parameters:List<JSONObject>,val config:JSONObject)
    suspend fun info(p:MediaProvider,reference:Boolean=false):ApiInfo {
        require(p.id.startsWith("hf-")){"Este fornecedor não usa Gradio"}
        var prefix="/gradio_api"
        val metadata=try{http.json(p.base+prefix+"/info",headers=auth(p))}catch(e:AiHttpFailure){if(e.status!=404)throw e;prefix="";http.json(p.base+"/info",headers=auth(p))}
        val named=metadata.optJSONObject("named_endpoints")?:error("O Space não publica uma API de geração")
        val endpoint=((if(p.kind=="video")listOf(if(reference)"/image_to_video" else "/text_to_video") else emptyList())+listOf("/"+p.api,"/generate_video","/generate","/infer")).distinct().firstOrNull{named.has(it)}?:error("API alterada: nenhum endpoint de geração compatível foi publicado")
        val config=http.json(p.base+"/config",headers=auth(p))
        return ApiInfo(prefix,endpoint,named.getJSONObject(endpoint).getJSONArray("parameters").objects(),config)
    }
    suspend fun test(p:MediaProvider):String=when {
        p.id.startsWith("hf-")-> {val i=info(p);"API ${i.endpoint} acessível. Isto não prova geração/GPU; faça uma geração experimental para confirmar."}
        p.id=="cloudflare"->{val key=vault.get("media_cloudflare_key");require(key.isNotBlank()){ "Introduza o token Cloudflare" };http.json(p.base+"/user/tokens/verify",headers=auth(p));"Token verificado. A geração e o acesso ao modelo ainda precisam de teste real."}
        else->{"Pollinations Classic: a ligação é testada apenas ao gerar uma imagem real; nenhuma imagem de demonstração é usada."}
    }
    suspend fun cancelRemote(){val action=cancellation;withContext(NonCancellable){withTimeoutOrNull(10000){runCatching{action?.invoke()}}}}
    private fun enc(value:String)=URLEncoder.encode(value,"UTF-8").replace("+","%20")
    suspend fun generate(p:MediaProvider,prompt:String,width:Int,height:Int,seed:Long,duration:Double,model:String,reference:File?,cloudflareSteps:Int=8,progress:(String)->Unit):File {
        require(prompt.isNotBlank()){ "Escreva uma descrição" }
        require(reference==null||p.reference){"Este fornecedor não aceita imagens de referência; escolha um compatível"}
        if(p.kind=="video")p.video.validate(duration,reference!=null)
        val now=System.currentTimeMillis()
        val previous=quota.get(p.id,model)
        require(!(previous.state==pt.aihub.core.MediaAvailability.EXHAUSTED&&(previous.resetAt?.let{it>now}==true))){previous.summary(now)}
        try { requireFreeHuggingFace(p) } catch(e:CancellationException){throw e} catch(e:Exception) {
            quota.put(p.id,model,if(e is AiHttpFailure)pt.aihub.core.MediaQuotaParser.parse(p.id,e.status,emptyMap(),connectionMessage(e),System.currentTimeMillis()) else pt.aihub.core.MediaQuota(state=pt.aihub.core.MediaAvailability.INVALID_CREDENTIALS,observedAt=System.currentTimeMillis(),source="Verificação da conta Free",detail=connectionMessage(e)))
            throw e
        }
        activeProvider=p.id;activeModel=model
        cancellation=null;cancellationSupported=false
        val directory=File(context.filesDir,"media").apply{mkdirs()}
        val file=File(directory,UUID.randomUUID().toString()+if(p.kind=="video")".mp4" else ".jpg")
        try {
            when {
                p.id=="pollinations-image"->{
                    // Only retry the same idempotent public image GET; never switch to paid access.
                    for(attempt in 0..2) {
                        while(System.currentTimeMillis()<nextClassic){progress("A aguardar quota pública (${(nextClassic-System.currentTimeMillis())/1000+1}s)");delay(1000)}
                        progress("A gerar imagem no Pollinations… (tentativa ${attempt+1}/3)")
                        nextClassic=System.currentTimeMillis()+46000
                        try {
                            http.file(p.base+"/prompt/"+enc(prompt)+"?width=$width&height=$height&seed=$seed&safe=true",file)
                            nextClassic=System.currentTimeMillis()+46000
                            break
                        } catch(e:AiHttpFailure) {
                            if(e.status!=503 || attempt==2 || (e.retryAfter?:0)>120)throw e
                            val waitSeconds=maxOf(46L,(e.retryAfter?:46L)+1)
                            nextClassic=maxOf(nextClassic,System.currentTimeMillis()+waitSeconds*1000)
                            progress("Pollinations temporariamente indisponível; a respeitar a espera pública antes de repetir o mesmo pedido.")
                        }
                    }
                }
                p.id=="cloudflare"->{
                    require(vault.get("media_cloudflare_free")=="true"){"Confirme uma conta Workers Free sem faturação, nas definições"}
                    val account=vault.get("media_cloudflare_account")
                    require(account.matches(Regex("[a-fA-F0-9]{32}"))){"Account ID Cloudflare inválido"}
                    require(model in p.models){"Modelo não autorizado na lista gratuita desta integração"}
                    require(vault.get("media_cloudflare_key").isNotBlank()){ "Token Cloudflare em falta" }
                    progress("A gerar no Cloudflare Workers AI…")
                    require(prompt.length<=2048){"O modelo Cloudflare aceita no máximo 2048 caracteres"}
                    val body=CloudflareImages.body(model,prompt,width,height,seed,cloudflareSteps)
                    http.call(p.base+"/accounts/$account/ai/run/$model","POST",auth(p),body.toString().toByteArray()){c->
                        val evidence=CloudflareImages.read(c.inputStream,c.contentType.orEmpty(),file)
                        evidence.put("provider",p.id).put("modelRequested",model).put("modelEndpoint",model).put("modelReportedByApi",JSONObject.NULL).put("request",body).put("http",c.responseCode).put("created",System.currentTimeMillis()).put("headers",JSONObject(lastResponseHeaders)).put("qualityAssessment","Não avaliada automaticamente; comparar visualmente o original com o pedido")
                        val diagnostics=File(context.filesDir,"media-diagnostics").apply{mkdirs()}
                        File(diagnostics,file.nameWithoutExtension+".json").writeText(evidence.toString(2))
                    }
                }
                else->{
                    progress("A validar API Gradio e capacidades atuais…")
                    val api=info(p,reference!=null)
                    val values=mutableMapOf<String,Any?>("prompt" to prompt,"negative_prompt" to "blurry, distorted, low quality","seed" to seed,"seed_ui" to seed,"randomize_seed" to false,"width" to width,"height" to height,"num_inference_steps" to if(p.id=="hf-zimage")9 else 4,
                        "input_image_filepath" to null,"input_video_filepath" to null,"height_ui" to height,"width_ui" to width,"mode" to if(reference==null)"text-to-video" else "image-to-video","duration_ui" to duration,"ui_frames_to_use" to 9,"ui_guidance_scale" to 3.0,"improve_texture_flag" to false)
                    if(p.id=="hf-wan22-i2v") {
                        values["input_image"]=null;values["steps"]=4;values["duration_seconds"]=duration
                        values["guidance_scale"]=1.0;values["guidance_scale_2"]=1.0
                    }
                    if(reference!=null) {
                        progress("A enviar a referência para o Space selecionado…")
                        val upload=ReferenceUpload(reference)
                        val paths=http.call(p.base+api.prefix+"/upload","POST",auth(p),contentType="multipart/form-data; boundary=${upload.boundary}",bodyWriter=upload::write,bodyLength=upload.length){JSONArray(it.inputStream.bufferedReader().use{r->r.readText()})}
                        val uploaded=JSONObject().put("path",paths.getString(0)).put("orig_name",upload.name).put("meta",JSONObject().put("_type","gradio.FileData"))
                        values["input_image_filepath"]=uploaded
                        if(p.id=="hf-wan22-i2v")values["input_image"]=uploaded
                    }
                    val data=JSONArray()
                    for(parameter in api.parameters) {
                        val name=parameter.getString("parameter_name")
                        val value=if(values.containsKey(name))values[name] else if(parameter.optBoolean("parameter_has_default"))parameter.opt("parameter_default") else error("O Space mudou: parâmetro obrigatório não suportado ($name)")
                        data.put(value?:JSONObject.NULL)
                    }
                    val session=UUID.randomUUID().toString()
                    val dependencies=api.config.optJSONArray("dependencies")?.objects().orEmpty()
                    val index=dependencies.indexOfFirst{it.optString("api_name")==api.endpoint.removePrefix("/")}
                    require(index>=0){"A API não publica a função da fila selecionada"}
                    val fn=dependencies[index].optInt("id",index)
                    // Match the official Gradio client: ZeroGPU Spaces require queue/join,
                    // not the simplified /call API (which can report 404 inside SSE).
                    val headers=auth(p)+mapOf("x-gradio-user" to "api")
                    val submitted=http.json(p.base+api.prefix+"/queue/join",JSONObject().put("data",data).put("fn_index",fn).put("session_hash",session),headers)
                    val event=submitted.getString("event_id")
                    cancellationSupported=true
                    cancellation={http.json(p.base+api.prefix+"/cancel",JSONObject().put("event_id",event).put("session_hash",session).put("fn_index",fn),headers);Unit}
                    progress("Pedido aceite — na fila do fornecedor. O progresso pode ser indeterminado.")
                    val output=http.call(p.base+api.prefix+"/queue/data?session_hash="+enc(session),headers=headers){c->
                        c.inputStream.bufferedReader().use{reader->
                            var completed:String?=null
                            while(true){val line=reader.readLine()?:break
                                if(!line.startsWith("data:"))continue
                                val payload=line.removePrefix("data:").trim()
                                if(payload.isBlank())continue
                                val message=JSONObject(payload)
                                if(message.has("event_id")&&message.optString("event_id")!=event)continue
                                when(message.optString("msg")) {
                                    "queue_full"->error("A fila pública do Space está cheia; tente mais tarde")
                                    "unexpected_error"->error("O Space falhou: "+message.optString("message").take(500))
                                    "estimation"->{val rank=message.optInt("rank",-1);if(rank>=0){
                                        val detail="Na fila do fornecedor — posição ${rank+1}"
                                        quota.put(p.id,model,pt.aihub.core.MediaQuota(state=pt.aihub.core.MediaAvailability.WAITING,observedAt=System.currentTimeMillis(),source="Fila Gradio",detail=detail));progress(detail)
                                    }}
                                    "process_starts","process_generating"->progress("O fornecedor está a processar o pedido…")
                                    "process_completed"->{
                                        val result=message.optJSONObject("output")?:error("O Space terminou sem resposta")
                                        if(!message.optBoolean("success",false)||!result.isNull("error")) {
                                            val detail=result.optString("error","Resposta sem sucesso")
                                            quota.put(p.id,model,pt.aihub.core.MediaQuotaParser.parse(p.id,0,emptyMap(),detail,System.currentTimeMillis()))
                                            throw MediaGenerationFailure(videoFailure(detail))
                                        }
                                        completed=result.getJSONArray("data").toString();break
                                    }
                                    "close_stream"->break
                                }
                            }
                            completed?:error("A ligação à fila terminou sem resultado; o trabalho pode continuar no fornecedor. Não repita sem verificar.")
                        }
                    }
                    fun locate(value:Any?):String?=when(value){
                        is JSONObject->value.optString("url").takeIf{it.startsWith("https://")} ?: value.keys().asSequence().mapNotNull{locate(value.opt(it))}.firstOrNull() ?: value.optString("path").takeIf{it.isNotBlank()}
                        is JSONArray->(0 until value.length()).asSequence().mapNotNull{locate(value.opt(it))}.firstOrNull()
                        else->null
                    }
                    val location=locate(JSONArray(output))?:error("O fornecedor terminou sem ficheiro de imagem/vídeo")
                    val url=if(location.startsWith("https://"))location else p.base+api.prefix+"/file="+enc(location)
                    progress("A descarregar o resultado para o Android…")
                    http.file(url,file,if(url.startsWith(p.base+"/"))auth(p) else emptyMap())
                }
            }
            require(file.length()>0){"O fornecedor devolveu um ficheiro vazio"}
            if(p.kind=="image") {
                val options=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
                android.graphics.BitmapFactory.decodeFile(file.path,options)
                require(options.outWidth>0&&options.outHeight>0){"O resultado não é uma imagem válida (pode ser uma página de erro)"}
            } else {
                val header=ByteArray(12);java.io.RandomAccessFile(file,"r").use{it.readFully(header)}
                require(String(header,4,4,Charsets.US_ASCII)=="ftyp"){"O fornecedor não devolveu um contentor MP4; o ficheiro não será exportado com uma extensão incorreta"}
                val retriever=android.media.MediaMetadataRetriever()
                try {retriever.setDataSource(file.path);require((retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0)>0){"O resultado não é um vídeo válido"}}finally{retriever.release()}
            }
            quota.success(p.id,model)
            return file
        } catch(e:CancellationException){cancelRemote();file.delete();throw e} catch(e:Exception){
            file.delete()
            if(e !is AiHttpFailure&&e !is MediaGenerationFailure)quota.put(p.id,model,pt.aihub.core.MediaQuotaParser.parse(p.id,0,emptyMap(),connectionMessage(e),System.currentTimeMillis()))
            throw e
        } finally {activeProvider="";activeModel=""}
    }
}
