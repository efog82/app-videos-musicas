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
class WebTransport(private val observe:((Int,Map<String,String>,String)->Unit)?=null) {
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
                val safeNames=setOf("retry-after","ratelimit","ratelimit-policy","x-ratelimit-limit","x-ratelimit-remaining","x-ratelimit-reset","x-ratelimit-limit-requests","x-ratelimit-remaining-requests","content-type","cf-ray")
                val responseHeaders=c.headerFields.entries.filter{it.key!=null&&it.key.lowercase() in safeNames}.associate{it.key.lowercase() to it.value.joinToString(",")}
                if(status in 200..299)observe?.invoke(status,responseHeaders,"")
                if(status in 300..399&&allowRedirect)throw MediaRedirect(URL(c.url,c.getHeaderField("Location")?:error("Redirecionamento sem destino")).toString())
                if(status !in 200..299) {
                    val raw=c.errorStream?.bufferedReader()?.use{it.readText().take(4000)}.orEmpty()
                    val safeRaw=headers.filterKeys{it.equals("Authorization",true)}.values.fold(raw){text,secret->text.replace(secret, "[credencial omitida]").replace(secret.removePrefix("Bearer "),"[credencial omitida]")}
                    observe?.invoke(status,responseHeaders,safeRaw)
                    val diagnosis=pt.aihub.core.MediaQuotaParser.parse(if(address.host=="api.cloudflare.com")"cloudflare" else "",status,responseHeaders,safeRaw,System.currentTimeMillis())
                    val message=diagnosis.state.label+if(diagnosis.resetAt!=null)" — "+diagnosis.summary(System.currentTimeMillis()).substringAfter('\n').substringBefore('\n') else ""
                    val detail=runCatching{JSONObject(safeRaw).optString("detail").ifBlank{JSONObject(safeRaw).optJSONObject("error")?.optString("message").orEmpty()}.ifBlank{JSONObject(safeRaw).optJSONArray("errors")?.toString().orEmpty()}}.getOrDefault("")
                    throw AiHttpFailure(status,pt.aihub.core.MediaQuotaParser.retryAt(c.getHeaderField("Retry-After"),System.currentTimeMillis())?.let{((it-System.currentTimeMillis())/1000).coerceAtLeast(0)},"$message (HTTP $status)"+if(detail.isBlank())"" else ": "+detail.take(350))
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
