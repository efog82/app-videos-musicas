package pt.aihub.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.InputStream

class Api(private val base: () -> String, private val token: () -> String) {
    private fun connection(path: String, method: String): HttpURLConnection {
        val url = URL(base().trimEnd('/') + path)
        require(url.protocol == "https" || (url.protocol == "http" && url.host in listOf("127.0.0.1","localhost","10.0.2.2"))) { "Utilize HTTPS; HTTP só é permitido no acesso local por USB/emulador" }
        require(url.userInfo == null) { "Não coloque credenciais no URL" }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10000; readTimeout = 180000; instanceFollowRedirects = false
            if (token().isNotBlank()) setRequestProperty("Authorization","Bearer ${token()}")
        }
    }
    private fun response(c: HttpURLConnection): String {
        if (c.responseCode !in 200..299) {
            val error = c.errorStream?.bufferedReader()?.use { it.readText().take(2000) } ?: "HTTP ${c.responseCode}"
            val detail = runCatching { JSONObject(error).optString("detail",error) }.getOrDefault(error)
            error(detail)
        }
        return c.inputStream.bufferedReader().use { it.readText() }
    }
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val c = connection(path,method)
        try {
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type","application/json"); c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
            response(c)
        } finally { c.disconnect() }
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
    suspend fun download(id: String, output: java.io.OutputStream) = withContext(Dispatchers.IO) {
        val c = connection("/v1/files/$id/download","GET")
        try {
            require(c.responseCode in 200..299) { "Não foi possível transferir o ficheiro: HTTP ${c.responseCode}" }
            c.inputStream.use { it.copyTo(output) }
        } finally { c.disconnect() }
    }
}
