package pt.aihub.studio

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID

/** Direct, authorised remote inference. No local engine, credit purchase or website automation. */
class OnlineMedia(private val context:Context,private val vault:Vault) {
    private val http=WebTransport()
    private var nextClassic=0L
    private var cancellation:(suspend()->Unit)?=null
    var cancellationSupported=false;private set
    private fun auth(p:MediaProvider):Map<String,String> {
        val key=if(p.id=="cloudflare")vault.get("media_cloudflare_key") else vault.get("key_huggingface")
        return if(key.isBlank())emptyMap() else mapOf("Authorization" to "Bearer $key")
    }
    data class ApiInfo(val prefix:String,val endpoint:String,val parameters:List<JSONObject>,val config:JSONObject)
    suspend fun info(p:MediaProvider,reference:Boolean=false):ApiInfo {
        require(p.id.startsWith("hf-")){"Este fornecedor não usa Gradio"}
        var prefix="/gradio_api"
        val metadata=try{http.json(p.base+prefix+"/info",headers=auth(p))}catch(e:AiHttpFailure){if(e.status!=404)throw e;prefix="";http.json(p.base+"/info",headers=auth(p))}
        val named=metadata.optJSONObject("named_endpoints")?:error("O Space não publica uma API de geração")
        val endpoint=((if(p.kind=="video")listOf(if(reference)"/image_to_video" else "/text_to_video") else emptyList())+listOf("/"+p.api,"/generate","/infer")).distinct().firstOrNull{named.has(it)}?:error("API alterada: nenhum endpoint de geração compatível foi publicado")
        val config=http.json(p.base+"/config",headers=auth(p))
        return ApiInfo(prefix,endpoint,named.getJSONObject(endpoint).getJSONArray("parameters").objects(),config)
    }
    suspend fun test(p:MediaProvider):String=when {
        p.id.startsWith("hf-")-> {val i=info(p);"API ${i.endpoint} acessível. Isto não prova geração/GPU; faça uma geração experimental para confirmar."}
        p.id=="cloudflare"->{val key=vault.get("media_cloudflare_key");require(key.isNotBlank()){ "Introduza o token Cloudflare" };http.json(p.base+"/user/tokens/verify",headers=auth(p));"Token verificado. A geração e o acesso ao modelo ainda precisam de teste real."}
        else->{"Pollinations Classic: a ligação é testada apenas ao gerar uma imagem real; nenhuma imagem de demonstração é usada."}
    }
    suspend fun cancelRemote(){val action=cancellation;withContext(NonCancellable){runCatching{action?.invoke()}}}
    private fun enc(value:String)=URLEncoder.encode(value,"UTF-8").replace("+","%20")
    suspend fun generate(p:MediaProvider,prompt:String,width:Int,height:Int,seed:Long,duration:Double,model:String,reference:File?,progress:(String)->Unit):File {
        require(prompt.isNotBlank()){ "Escreva uma descrição" }
        require(reference==null||p.reference){"Este fornecedor não aceita imagens de referência; escolha um compatível"}
        cancellation=null;cancellationSupported=false
        val directory=File(context.filesDir,"media").apply{mkdirs()}
        val file=File(directory,UUID.randomUUID().toString()+if(p.kind=="video")".mp4" else ".jpg")
        try {
            when {
                p.id=="pollinations-image"->{
                    while(System.currentTimeMillis()<nextClassic){progress("A aguardar quota pública (${(nextClassic-System.currentTimeMillis())/1000+1}s)");delay(1000)}
                    progress("A gerar imagem no Pollinations…")
                    nextClassic=System.currentTimeMillis()+46000
                    http.file(p.base+"/prompt/"+enc(prompt)+"?width=$width&height=$height&seed=$seed&safe=true",file)
                    nextClassic=System.currentTimeMillis()+46000
                }
                p.id=="cloudflare"->{
                    require(vault.get("media_cloudflare_free")=="true"){"Confirme uma conta Workers Free sem faturação, nas definições"}
                    val account=vault.get("media_cloudflare_account")
                    require(account.matches(Regex("[a-fA-F0-9]{32}"))){"Account ID Cloudflare inválido"}
                    require(model in p.models){"Modelo não autorizado na lista gratuita desta integração"}
                    require(vault.get("media_cloudflare_key").isNotBlank()){ "Token Cloudflare em falta" }
                    progress("A gerar no Cloudflare Workers AI…")
                    require(prompt.length<=2048){"O modelo Cloudflare aceita no máximo 2048 caracteres"}
                    val body=JSONObject().put("prompt",prompt).put("seed",seed)
                    if(model.contains("flux"))body.put("steps",4) else body.put("width",width).put("height",height).put("num_steps",20)
                    http.call(p.base+"/accounts/$account/ai/run/$model","POST",auth(p),body.toString().toByteArray()){c->
                        if(c.contentType.orEmpty().contains("json")) {
                            val result=JSONObject(c.inputStream.bufferedReader().use{it.readText()})
                            val encoded=result.optJSONObject("result")?.optString("image").orEmpty()
                            require(encoded.isNotBlank()){ "A API não devolveu uma imagem; confirme a quota, modelo e permissões" }
                            file.writeBytes(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT))
                        } else c.inputStream.use{input->file.outputStream().use{input.copyTo(it)}}
                    }
                }
                else->{
                    progress("A validar API Gradio e capacidades atuais…")
                    val api=info(p,reference!=null)
                    val values=mutableMapOf<String,Any?>("prompt" to prompt,"negative_prompt" to "blurry, distorted, low quality","seed" to seed,"seed_ui" to seed,"randomize_seed" to false,"width" to width,"height" to height,"num_inference_steps" to 4,
                        "input_image_filepath" to null,"input_video_filepath" to null,"height_ui" to height,"width_ui" to width,"mode" to if(reference==null)"text-to-video" else "image-to-video","duration_ui" to duration,"ui_frames_to_use" to 9,"ui_guidance_scale" to 3.0,"improve_texture_flag" to false)
                    if(reference!=null) {
                        progress("A enviar a referência para o Space selecionado…")
                        val boundary="AIHub"+UUID.randomUUID()
                        val bytes=java.io.ByteArrayOutputStream().apply{
                            write("--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"reference.png\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray());write(reference.readBytes());write("\r\n--$boundary--\r\n".toByteArray())
                        }.toByteArray()
                        val paths=http.call(p.base+api.prefix+"/upload","POST",auth(p),bytes,"multipart/form-data; boundary=$boundary"){JSONArray(it.inputStream.bufferedReader().use{r->r.readText()})}
                        values["input_image_filepath"]=JSONObject().put("path",paths.getString(0)).put("orig_name","reference.png").put("meta",JSONObject().put("_type","gradio.FileData"))
                    }
                    val data=JSONArray()
                    for(parameter in api.parameters) {
                        val name=parameter.getString("parameter_name")
                        val value=if(values.containsKey(name))values[name] else if(parameter.optBoolean("parameter_has_default"))parameter.opt("parameter_default") else error("O Space mudou: parâmetro obrigatório não suportado ($name)")
                        data.put(value?:JSONObject.NULL)
                    }
                    val session=UUID.randomUUID().toString()
                    val submitted=http.json(p.base+api.prefix+"/call"+api.endpoint,JSONObject().put("data",data).put("session_hash",session),auth(p))
                    val event=submitted.getString("event_id")
                    val dependencies=api.config.optJSONArray("dependencies")?.objects().orEmpty()
                    val fn=dependencies.indexOfFirst{it.optString("api_name")==api.endpoint.removePrefix("/")}
                    cancellationSupported=fn>=0
                    if(cancellationSupported)cancellation={http.json(p.base+api.prefix+"/cancel",JSONObject().put("event_id",event).put("session_hash",session).put("fn_index",fn),auth(p));Unit}
                    progress("Pedido aceite — na fila do fornecedor. O progresso pode ser indeterminado.")
                    val output=http.call(p.base+api.prefix+"/call"+api.endpoint+"/"+event,headers=auth(p)){c->
                        c.inputStream.bufferedReader().use{reader->
                            var type="";val payload=StringBuilder();var completed:String?=null
                            while(true){val line=reader.readLine()?:break
                                when {
                                    line.startsWith("event:")->type=line.removePrefix("event:").trim()
                                    line.startsWith("data:")->payload.append(line.removePrefix("data:").trim())
                                    line.isEmpty()->{
                                        if(type=="error")error("O Space recusou/falhou a geração: "+payload.toString().take(500))
                                        if(type=="complete"){completed=payload.toString();break}
                                        if(type=="generating")progress("O fornecedor está a processar o pedido…")
                                        type="";payload.clear()
                                    }
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
                val retriever=android.media.MediaMetadataRetriever()
                try {retriever.setDataSource(file.path);require((retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0)>0){"O resultado não é um vídeo válido"}}finally{retriever.release()}
            }
            return file
        } catch(e:CancellationException){cancelRemote();file.delete();throw e} catch(e:Exception){file.delete();throw e}
    }
}
