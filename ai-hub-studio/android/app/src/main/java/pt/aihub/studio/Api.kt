package pt.aihub.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.InputStream
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AiHttpFailure(val status:Int,val retryAfter:Long?,message:String):java.io.IOException(message)

class Api(private val base: () -> String, private val token: () -> String, private val headers: () -> Map<String,String> = { emptyMap() }, private val fingerprint: () -> String = { "" }, private val isPc:Boolean=false) {
    private fun connection(path: String, method: String): HttpURLConnection {
        require(base().isNotBlank()) { "Servidor PC não configurado. Use a configuração inicial ou introduza o IP privado do PC; não use 127.0.0.1 por Wi-Fi." }
        val url = URL(base().trimEnd('/') + path)
        require(url.protocol == "https" || (url.protocol == "http" && url.host in listOf("127.0.0.1","localhost","10.0.2.2"))) { "Utilize HTTPS; HTTP só é permitido no acesso local por USB/emulador" }
        if(isPc){val host=java.net.InetAddress.getByName(url.host);require(host.isLoopbackAddress||host.isSiteLocalAddress){"O servidor PC deve usar um endereço privado da rede local"}}
        require(url.userInfo == null) { "Não coloque credenciais no URL" }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10000; readTimeout = 180000; instanceFollowRedirects = false
            if (token().isNotBlank()) setRequestProperty("Authorization","Bearer ${token()}")
            headers().forEach { (k,v) -> setRequestProperty(k,v) }
            if(this is javax.net.ssl.HttpsURLConnection && fingerprint().isNotBlank())sslSocketFactory=pinnedTls(fingerprint())
        }
    }
    private fun response(c: HttpURLConnection): String {
        if (c.responseCode !in 200..299) {
            val raw = c.errorStream?.bufferedReader()?.use { it.readText().take(2000) }.orEmpty()
            val parsed=runCatching{JSONObject(raw)}.getOrNull()
            val detail=parsed?.optJSONObject("error")?.optString("message") ?: parsed?.optString("detail") ?: ""
            val reason=when(c.responseCode){
                401->if(isPc)"Token de emparelhamento inválido; copie o token do PC" else "Chave API inválida ou em falta; teste a chave nas definições do fornecedor"
                403->"Acesso recusado: permissões da chave, modelo ou região não autorizados"
                402,429->"Quota gratuita esgotada ou limite de frequência. Aguarde ${c.getHeaderField("Retry-After") ?: "o próximo período"}; não há pagamento automático"
                404->"URL/API incorreto ou modelo não instalado/disponível. Atualize a descoberta de modelos"
                in 500..599->"Serviço indisponível (HTTP ${c.responseCode}); tente mais tarde ou outro fornecedor configurado"
                else->"Pedido recusado (HTTP ${c.responseCode})"
            }
            throw AiHttpFailure(c.responseCode,c.getHeaderField("Retry-After")?.toLongOrNull(),reason+if(detail.isNotBlank())". "+detail.take(500) else "")
        }
        return c.inputStream.bufferedReader().use { it.readText() }
    }
    companion object {private val pool=java.util.concurrent.Executors.newCachedThreadPool()}
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): String = suspendCancellableCoroutine { continuation ->
        val active=java.util.concurrent.atomic.AtomicReference<HttpURLConnection?>()
        continuation.invokeOnCancellation{active.get()?.let{c->pool.execute{c.disconnect()}}}
        pool.execute {
            var c:HttpURLConnection?=null
            try {
                c=connection(path,method);active.set(c)
                if(!continuation.isActive){c.disconnect();return@execute}
                if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
                val value=response(c)
                if(continuation.isActive)continuation.resume(value)
            } catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}finally{c?.disconnect()}
        }
    }
    suspend fun obj(path: String, method: String = "GET", body: JSONObject? = null) = JSONObject(request(path,method,body))
    suspend fun list(path: String) = JSONArray(request(path)).objects()
    suspend fun upload(name: String, stream: InputStream): JSONObject = withContext(Dispatchers.IO) {
        val c = connection("/v1/files","POST")
        try {
            val boundary = "AIHub" + java.util.UUID.randomUUID().toString()
            c.doOutput = true; c.setChunkedStreamingMode(1024*1024)
            c.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
            c.outputStream.use { output ->
                val safeName = name.replace(Regex("[\\r\\n\"\\\\]"),"_")
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
                stream.copyTo(output)
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            JSONObject(response(c))
        } finally { c.disconnect() }
    }
    suspend fun binary(path: String): ByteArray = withContext(Dispatchers.IO) {
        val c = connection(path,"GET")
        try {
            if(c.responseCode !in 200..299)response(c)
            require(c.contentType?.startsWith("image/")==true) { "O serviço não devolveu uma imagem" }
            c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }
    suspend fun download(id: String, output: java.io.OutputStream) = withContext(Dispatchers.IO) {
        val c = connection("/v1/files/$id/download","GET")
        try {
            require(c.responseCode in 200..299) { "Não foi possível transferir o ficheiro: HTTP ${c.responseCode}" }
            c.inputStream.use { it.copyTo(output) }
        } finally { c.disconnect() }
    }
}
