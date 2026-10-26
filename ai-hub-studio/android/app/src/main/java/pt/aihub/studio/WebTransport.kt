package pt.aihub.studio

import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONObject

/** Reads on worker threads; cancellation closes exactly this connection. Never disables TLS. */
class MediaRedirect(val destination:String):Exception()
class WebTransport {
    companion object {private val executor=Executors.newCachedThreadPool()}
    suspend fun <T> call(url:String,method:String="GET",headers:Map<String,String> = emptyMap(),body:ByteArray?=null,contentType:String="application/json",allowRedirect:Boolean=false,bodyWriter:((java.io.OutputStream)->Unit)?=null,bodyLength:Long?=null,read:(HttpURLConnection)->T):T = suspendCancellableCoroutine { continuation ->
        val reference=AtomicReference<HttpURLConnection?>()
        continuation.invokeOnCancellation {reference.get()?.disconnect()}
        executor.execute {
            var c:HttpURLConnection?=null
            try {
                val address=URL(url)
                require(address.protocol=="https"&&address.userInfo==null){"O fornecedor multimédia exige HTTPS sem credenciais no URL"}
                c=address.openConnection() as HttpURLConnection
                reference.set(c)
                if(!continuation.isActive){c.disconnect();return@execute}
                c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=360000;c.instanceFollowRedirects=false
                headers.forEach{(k,v)->c.setRequestProperty(k,v)}
                if(body!=null||bodyWriter!=null){
                    c.doOutput=true;c.setRequestProperty("Content-Type",contentType)
                    if(body!=null)c.setFixedLengthStreamingMode(body.size) else if(bodyLength!=null)c.setFixedLengthStreamingMode(bodyLength) else c.setChunkedStreamingMode(65536)
                    c.outputStream.use{output->if(body!=null)output.write(body) else bodyWriter!!.invoke(output)}
                }
                val status=c.responseCode
                if(status in 300..399&&allowRedirect)throw MediaRedirect(URL(c.url,c.getHeaderField("Location")?:error("Redirecionamento sem destino")).toString())
                if(status !in 200..299) {
                    val raw=c.errorStream?.bufferedReader()?.use{it.readText().take(1000)}.orEmpty()
                    val message=when(status){401,403->"Autenticação/permissão recusada; a API pode exigir uma conta gratuita ou token";402,429->"Quota gratuita esgotada. Respeite Retry-After e tente outro fornecedor configurado";404->"A API/modelo deixou de estar disponível neste endereço";in 300..399->"O serviço redirecionou o pedido; confirme o endpoint oficial, não foram reenviadas credenciais";else->"Serviço indisponível ou pedido recusado"}
                    val detail=runCatching{JSONObject(raw).optString("detail").ifBlank{JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()}}.getOrDefault("")
                    throw AiHttpFailure(status,c.getHeaderField("Retry-After")?.toLongOrNull(),"$message (HTTP $status)"+if(detail.isBlank())"" else ": "+detail.take(350))
                }
                val result=read(c)
                if(continuation.isActive)continuation.resume(result)
            } catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)} finally{c?.disconnect()}
        }
    }
    suspend fun json(url:String,body:JSONObject?=null,headers:Map<String,String> = emptyMap()):JSONObject=call(url,if(body==null)"GET" else "POST",headers,body?.toString()?.toByteArray()){JSONObject(it.inputStream.bufferedReader().use{r->r.readText()})}
    suspend fun text(url:String,headers:Map<String,String> = emptyMap()):String=call(url,headers=headers){it.inputStream.bufferedReader().use{r->r.readText()}}
    suspend fun bytes(url:String,headers:Map<String,String> = emptyMap()):ByteArray=call(url,headers=headers){c->
        val out=java.io.ByteArrayOutputStream();var size=0
        c.inputStream.use { input->val buffer=ByteArray(65536);while(true){val count=input.read(buffer);if(count<0)break;size+=count;require(size<=20*1024*1024){"Resposta superior a 20 MiB"};out.write(buffer,0,count)}}
        out.toByteArray()
    }
    suspend fun file(url:String,destination:java.io.File,headers:Map<String,String> = emptyMap()) {
        var next=url;var currentHeaders=headers
        repeat(4) {
            try {
                call(next,headers=currentHeaders,allowRedirect=true){c->
                    require(c.contentLengthLong<=200L*1024*1024){"Ficheiro superior a 200 MiB"}
                    c.inputStream.use{input->destination.outputStream().use{output->val buffer=ByteArray(65536);var size=0L;while(true){val n=input.read(buffer);if(n<0)break;size+=n;require(size<=200L*1024*1024){"Ficheiro demasiado grande"};output.write(buffer,0,n)}}}
                }
                return
            } catch(e:MediaRedirect){next=e.destination;currentHeaders=emptyMap()}
        }
        error("Demasiados redirecionamentos no ficheiro do fornecedor")
    }
}
