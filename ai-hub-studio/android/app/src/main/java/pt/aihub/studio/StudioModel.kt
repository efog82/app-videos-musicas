package pt.aihub.studio

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class StudioModel(app:Application):AndroidViewModel(app) {
    val vault=Vault(app);val db=LocalData(app);val ai=DirectAi(vault)
    val mediaStatus=MediaStatusStore(db)
    private val mediaClient=OnlineMedia(app,vault);private val queue=Mutex();private val handler=Handler(Looper.getMainLooper())
    private val workers=mutableMapOf<String,Job>()
    var dark by mutableStateOf(vault.get("theme_v3")!="light");private set
    var tab by mutableStateOf("Chat")
    var status by mutableStateOf("Escolha uma IA e autorize o envio em Fornecedores. Os pedidos gratuitos têm quotas.")
    var importingImage by mutableStateOf(false);private set
    var settings by mutableStateOf(false)
    var models by mutableStateOf(ai.cachedModels());private set
    var messages by mutableStateOf(emptyList<JSONObject>());private set
    var conversations by mutableStateOf(db.list("conversation"));private set
    var assets by mutableStateOf(db.list("asset"));private set
    var projects by mutableStateOf(db.list("project"));private set
    var tasks by mutableStateOf(db.list("generation"));private set
    var conversationId by mutableStateOf("");private set
    var currentProject by mutableStateOf("")
    var selection by mutableStateOf(vault.get("preferred_model_v3"))
    var consent by mutableStateOf(vault.get("remote_consent")=="true")
    var chatBusy by mutableStateOf(false);private set
    var configurationBusy by mutableStateOf(false);private set
    private var chatWorker:Job?=null
    init {
        for(job in tasks) if(job.optString("state") in listOf("queued","running"))saveTask(job.put("state","interrupted").put("message","A aplicação foi reiniciada. O fornecedor pode continuar; repita apenas após verificar."))
        if(vault.get("migration_v3")!="done") {
            for(old in db.list("local_file")) {
                val file=File(old.optString("path"));if(file.isFile)runCatching{createAsset(file,"image",old.optString("name","Imagem anterior"),"v0.2",old.optString("project"),"",0,false)}
            }
            vault.set("migration_v3","done");assets=db.list("asset")
        }
    }
    fun theme(){dark=!dark;vault.set("theme_v3",if(dark)"dark" else "light")}
    fun refresh(){conversations=db.list("conversation");projects=db.list("project");assets=db.list("asset");tasks=db.list("generation");models=ai.cachedModels()}
    fun newChat(){chatWorker?.cancel();conversationId="";messages=emptyList();status="Nova conversa"}
    fun openConversation(c:JSONObject){chatWorker?.cancel();conversationId=c.getString("id");messages=c.optJSONArray("messages")?.objects().orEmpty();currentProject=c.optString("project");tab="Chat"}
    fun deleteConversation(id:String){db.delete(id);if(id==conversationId)newChat();refresh()}
    private fun existingMediaMessages(values:List<JSONObject>):List<JSONObject> {
        val existing=db.list("asset").map{it.optString("id")}.toSet()
        return removeImageReferences(values,values.mapNotNull{it.optString("assetId").takeIf{id->id.isNotBlank()&&id !in existing}}.toSet())
    }
    fun persistConversation(){
        messages=existingMediaMessages(messages)
        if(messages.isEmpty())return
        if(conversationId.isBlank())conversationId=UUID.randomUUID().toString()
        val existing=conversations.firstOrNull{it.optString("id")==conversationId}
        val first=messages.firstOrNull{it.optString("role")=="user"}?.optString("content").orEmpty()
        db.save("conversation",JSONObject().put("title",existing?.optString("title")?:first.trim().replace(Regex("\\s+")," ").take(64).ifBlank{"Conversa"}).put("messages",JSONArray(messages)).put("project",currentProject),conversationId)
        conversations=db.list("conversation")
    }
    fun chooseModel(value:String){selection=value;vault.set("preferred_model_v3",value)}
    fun configure(provider:String,key:String,free:Boolean,test:Boolean) {
        if(configurationBusy)return
        vault.set("key_$provider",key.trim());vault.set("free_$provider",free.toString());vault.set("remote_consent",consent.toString());vault.set("ai_provider",provider)
        configurationBusy=true
        viewModelScope.launch {
            try {
                status=if(test)"A descobrir e testar uma resposta real…" else "A descobrir modelos…"
                if(test) {
                    status=ai.test(provider,if(selection.substringBefore('|')==provider)selection.substringAfter('|') else "",consent,free)
                    ai.automaticModels().firstOrNull{it.optString("provider")==provider}?.let{chooseModel(provider+"|"+it.getString("id"))}
                } else {ai.discover(provider);status="Catálogo recebido. Teste ou envie uma pergunta para verificar o modelo."}
            } catch(e:Exception){status=connectionMessage(e)}finally{models=ai.cachedModels();configurationBusy=false}
        }
    }
    fun supportsAttachment():Boolean {
        val provider=selection.substringBefore('|')
        return (provider=="gemini"&&selection.substringAfter('|').startsWith("gemini"))||models.firstOrNull{it.optString("provider")+"|"+it.optString("id")==selection}?.optJSONArray("capabilities")?.toString()?.contains("vision")==true
    }
    fun send(text:String,attachments:List<JSONObject> = emptyList(),regenerate:Boolean=false) {
        if(chatBusy||(!regenerate&&text.isBlank()&&attachments.isEmpty()))return
        if(selection.isBlank()&&ai.automaticModels().isEmpty()){settings=true;status="Ligue e teste uma IA antes de enviar";return}
        val previous=if(regenerate)messages.dropLastWhile{it.optString("role")!="user"} else messages
        val input=if(regenerate)previous else previous+JSONObject().put("role","user").put("content",text).put("attachments",JSONArray(attachments))
        messages=input;persistConversation();val id=conversationId
        chatBusy=true
        chatWorker=viewModelScope.launch {
            try {
                status="A IA está a responder…"
                val context=projects.firstOrNull{it.optString("id")==currentProject}?.optString("context").orEmpty()
                val history=(if(context.isNotBlank())listOf(JSONObject().put("role","system").put("content",context)) else emptyList())+input.filter{!it.has("assetId")}
                val answer=ai.chat(selection,history,consent,false){value->handler.post{status=value}}
                if(conversationId==id){val mediaAdded=messages.drop(input.size).filter{it.has("assetId")};messages=input+JSONObject().put("role","assistant").put("content",answer)+mediaAdded;persistConversation()}
                else {
                    val c=conversations.firstOrNull{it.optString("id")==id}
                    if(c!=null)db.save("conversation",JSONObject(c.toString()).put("messages",JSONArray(existingMediaMessages(input+JSONObject().put("role","assistant").put("content",answer)))),id)
                }
                status="Resposta recebida";models=ai.cachedModels()
            } catch(e:CancellationException){status="Espera interrompida. O fornecedor pode continuar a processar o pedido.";throw e}
            catch(e:Exception){status=connectionMessage(e)}finally{chatBusy=false;refresh()}
        }
    }
    fun stopChat(){chatWorker?.cancel()}
    fun saveProject(name:String,context:String){require(name.isNotBlank());db.save("project",JSONObject().put("name",name).put("context",context));refresh()}
    fun assignAsset(asset:JSONObject,project:String){db.save("asset",JSONObject(asset.toString()).put("project",project),asset.getString("id"));refresh()}
    private fun saveTask(task:JSONObject){db.save("generation",task,task.getString("id"));tasks=db.list("generation")}
    private fun updateTask(id:String,state:String,message:String){val t=tasks.firstOrNull{it.getString("id")==id}?:return;if(t.optString("state") in setOf("done","failed","cancelled","interrupted")&&state in setOf("queued","running"))return;saveTask(JSONObject(t.toString()).put("state",state).put("message",message))}
    fun generate(provider:MediaProvider,prompt:String,width:Int,height:Int,seed:Long,duration:Double,model:String,reference:JSONObject?,count:Int,origin:String="",cloudflareSteps:Int=8) {
        if(!consent){status="Autorize o envio de conteúdo ao fornecedor nas definições";settings=true;return}
        require(count in 1..10){"Escolha entre 1 e 10 pedidos por lote para controlar a quota"}
        repeat(count){offset->
            val id=UUID.randomUUID().toString();val project=currentProject
            saveTask(JSONObject().put("id",id).put("provider",provider.id).put("kind",provider.kind).put("prompt",prompt).put("state","queued").put("message","Em espera na fila da aplicação").put("project",project).put("created",System.currentTimeMillis()).put("conversation",origin).put("reference",reference?.optString("id")?:"").put("durationRequested",duration).put("widthRequested",width).put("heightRequested",height).put("seed",seed+offset).put("model",model).put("cloudflareSteps",cloudflareSteps))
            workers[id]=viewModelScope.launch {
                try {
                    queue.withLock {
                        updateTask(id,"running","A contactar o fornecedor…")
                        val file=mediaClient.generate(provider,prompt,width,height,seed+offset,duration,model,reference?.let{File(it.getString("path"))},cloudflareSteps){message->handler.post{updateTask(id,"running",message)}}
                        val asset=withContext(Dispatchers.IO){createAsset(file,provider.kind,prompt,provider.id,project,origin,seed+offset,true,JSONObject().put("model",model).put("cloudflareDiagnostic",mediaClient.lastCloudflareDiagnostic?:JSONObject.NULL).put("cloudflareSteps",if(provider.id=="cloudflare"&&model==pt.aihub.core.CloudflareImageRequest.FLUX)cloudflareSteps else JSONObject.NULL).put("seedApplied",!(provider.id=="cloudflare"&&model==pt.aihub.core.CloudflareImageRequest.FLUX)))}
                        if(origin.isNotBlank()) {
                            val c=db.list("conversation").firstOrNull{it.getString("id")==origin}
                            if(c!=null) {
                                val list=c.getJSONArray("messages").objects()+JSONObject().put("role","assistant").put("content","").put("assetId",asset.getString("id"))
                                db.save("conversation",JSONObject(c.toString()).put("messages",JSONArray(list)),origin)
                                if(conversationId==origin)messages=list
                            }
                        }
                        vault.set("media_test_${provider.id}",System.currentTimeMillis().toString())
                        updateTask(id,"done","Geração real concluída e guardada no Android");status="${if(provider.kind=="video")"Vídeo" else "Imagem"} pronto";refresh()
                    }
                } catch(e:CancellationException){updateTask(id,"cancelled","Pedido interrompido localmente; a GPU remota pode continuar se a API não aceitar cancelamento");throw e}
                catch(e:Exception){updateTask(id,"failed",connectionMessage(e));status=connectionMessage(e)}
            }
        }
    }
    fun cancelTask(id:String){updateTask(id,"cancelled","Cancelado localmente; é enviado cancelamento ao fornecedor quando suportado");workers[id]?.cancel()}
    fun deleteTask(id:String){
        viewModelScope.launch {
            try {
                require(workers[id]?.isCompleted!=false){"O pedido ainda está a terminar. Aguarde antes de eliminar o relatório."}
                withContext(Dispatchers.IO){GenerationHistory(db).remove(id)}
                workers.remove(id);refresh();status="Relatório eliminado. As criações e os projetos foram mantidos."
            }catch(e:Exception){status=connectionMessage(e)}
        }
    }
    suspend fun prepareVisualDescription(text:String,kind:String):String {
        require(text.isNotBlank()&&text.length<=20000){"Escolha uma cena ou um texto até 20 000 caracteres"}
        require(consent){"Autorize o envio à IA de chat nas definições"}
        require(selection.isNotBlank()||ai.automaticModels().isNotEmpty()){"Configure uma IA de chat para preparar a descrição, ou edite-a manualmente"}
        val answer=ai.chat(selection,listOf(JSONObject().put("role","system").put("content",pt.aihub.core.VisualPromptRules.system(kind)),JSONObject().put("role","user").put("content",text)),consent,false)
        return pt.aihub.core.VisualPromptRules.reviewedCandidate(answer)
    }
    fun compareCloudflare(prompt:String="A synthetic infrared-inspired image of a realistic spiral galaxy, James Webb telescope visual style, clearly defined spiral arms, dense stellar core, fine dust lanes, distant stars, deep black background, natural astronomical detail, no text"){
        require(consent){"Autorize o envio aos fornecedores nas definições"}
        require(vault.get("media_cloudflare_free")=="true"){"Guarde primeiro a confirmação de uma conta Cloudflare Workers Free"}
        require(vault.get("media_cloudflare_key").isNotBlank()&&vault.get("media_cloudflare_account").matches(Regex("[a-fA-F0-9]{32}"))){"Guarde primeiro o Account ID e o token Cloudflare"}
        val provider=mediaProviders.first{it.id=="cloudflare"}
        generate(provider,prompt,512,512,424242,2.0,pt.aihub.core.CloudflareImageRequest.FLUX,null,1,cloudflareSteps=4)
        generate(provider,prompt,512,512,424242,2.0,pt.aihub.core.CloudflareImageRequest.FLUX,null,1,cloudflareSteps=8)
        generate(provider,prompt,1024,1024,424242,2.0,pt.aihub.core.CloudflareImageRequest.SDXL,null,1)
        tab="Imagens";settings=false;status="Comparação pedida: FLUX 4 passos, FLUX 8 passos e SDXL. Consome até 3 gerações da quota gratuita; resultados em Imagens."
    }
    fun markCloudflareResult(asset:JSONObject,abstract:Boolean){
        require(asset.optString("provider")=="cloudflare")
        db.save("asset",JSONObject(asset.toString()).put("qualityFeedback",if(abstract)"abstract_reported_by_user" else "matches_prompt_reported_by_user"),asset.getString("id"));refresh()
        status=if(abstract)"Resultado assinalado como abstrato. Pode partilhar o original com o diagnóstico; nenhum pedido foi repetido." else "Avaliação guardada. Não foi efetuado nenhum novo pedido."
    }
    private fun createAsset(input:File,kind:String,prompt:String,provider:String,project:String,origin:String,seed:Long,removeOriginal:Boolean,extra:JSONObject=JSONObject()):JSONObject {
        val hash=input.inputStream().use{stream->val digest=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536);while(true){val n=stream.read(b);if(n<0)break;digest.update(b,0,n)};digest.digest().joinToString(""){"%02x".format(it)}}
        val options=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
        if(kind=="image")android.graphics.BitmapFactory.decodeFile(input.path,options)
        val mime=if(kind=="video")"video/mp4" else options.outMimeType?:"image/jpeg"
        val extension=when(mime){"image/webp"->"webp";"image/png"->"png";"image/gif"->"gif";"image/heic"->"heic";"image/heif"->"heif";"image/avif"->"avif";"image/bmp","image/x-ms-bmp"->"bmp";"video/mp4"->"mp4";else->"jpg"}
        val dir=File(getApplication<Application>().filesDir,"media").apply{mkdirs()};val file=File(dir,"$hash.$extension")
        if(!file.exists())input.copyTo(file)
        if(removeOriginal&&input.path!=file.path)input.delete()
        val metadata=JSONObject(extra.toString())
        if(kind=="video") {
            val retriever=android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.path)
                metadata.put("durationMs",retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0)
                metadata.put("width",retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?:0)
                metadata.put("height",retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:0)
            }finally{retriever.release()}
        }
        return dbSaveAsset(metadata.put("path",file.path).put("kindMedia",kind).put("mime",mime).put("prompt",prompt).put("provider",provider).put("project",project).put("conversation",origin).put("seed",seed).put("hash",hash).put("created",System.currentTimeMillis()))
    }
    private fun dbSaveAsset(value:JSONObject):JSONObject{val id=db.save("asset",value);return JSONObject(value.toString()).put("id",id)}
    suspend fun importGalleryImage(uri:Uri):JSONObject? {
        if(importingImage)return null
        importingImage=true;status="A importar a imagem original, sem recompressão…"
        return try {
            val project=currentProject
            val asset=withContext(Dispatchers.IO){
                val original=OriginalImageImporter(getApplication()).copy(uri)
                try {createAsset(original.file,"image",original.name,"galeria",project,"",0,true,JSONObject().put("source","gallery").put("displayName",original.name).put("width",original.width).put("height",original.height))}finally{original.file.delete()}
            }
            refresh();status="Imagem original pronta para usar como referência, sem alterar a galeria";asset
        }catch(e:CancellationException){throw e}catch(e:Exception){status=connectionMessage(e);null}finally{importingImage=false}
    }
    fun deleteAsset(asset:JSONObject){
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO){ImageLibrary(getApplication(),db).delete(asset.getString("id"))}
                refresh()
                if(conversationId.isNotBlank())messages=conversations.firstOrNull{it.optString("id")==conversationId}?.optJSONArray("messages")?.objects().orEmpty()
                status="Removido da aplicação. Os ficheiros da galeria Android foram mantidos."
            }catch(e:Exception){status=connectionMessage(e)}
        }
    }
    suspend fun export(asset:JSONObject):Uri=withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT>=29){"Neste Android use Exportar ficheiro pelo seletor do sistema"}
        val context=getApplication<Application>();val resolver=context.contentResolver
        val existing=asset.optString("galleryUri")
        if(existing.isNotBlank()&&runCatching{resolver.openInputStream(Uri.parse(existing))?.use{true}?:false}.getOrDefault(false))return@withContext Uri.parse(existing)
        val video=asset.optString("kindMedia")=="video"
        val extension=File(asset.getString("path")).extension
        val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,"AIHub-${asset.getString("id").take(8)}.$extension");put(MediaStore.MediaColumns.MIME_TYPE,asset.getString("mime"));put(MediaStore.MediaColumns.RELATIVE_PATH,if(video)"Movies/AI Hub Studio" else "Pictures/AI Hub Studio");put(MediaStore.MediaColumns.IS_PENDING,1)}
        val uri=resolver.insert(if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:error("Não foi possível criar o ficheiro na galeria")
        try {resolver.openOutputStream(uri)!!.use{output->File(asset.getString("path")).inputStream().use{it.copyTo(output)}};resolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null);db.save("asset",JSONObject(asset.toString()).put("galleryUri",uri.toString()),asset.getString("id"));uri}
        catch(e:Exception){resolver.delete(uri,null,null);throw e}
    }
}
