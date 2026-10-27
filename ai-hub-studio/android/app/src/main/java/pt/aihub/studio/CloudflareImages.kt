package pt.aihub.studio

import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import pt.aihub.core.CloudflareImageRequest

object CloudflareImages {
    fun body(model:String,prompt:String,width:Int,height:Int,seed:Long,steps:Int)=JSONObject(CloudflareImageRequest.parameters(model,prompt,width,height,seed,steps))
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun read(input:InputStream,contentType:String,file:File):JSONObject {
        val out=java.io.ByteArrayOutputStream();input.use{stream->val buffer=ByteArray(65536);while(true){val n=stream.read(buffer);if(n<0)break;require(out.size()+n<=30*1024*1024){"Resposta Cloudflare superior a 30 MiB"};out.write(buffer,0,n)}}
        val wire=out.toByteArray()
        val json=contentType.contains("json",true)||wire.firstOrNull{it.toInt().toChar() !in listOf(' ','\r','\n','\t')}?.toInt()?.toChar()=='{'
        val image=if(json){
            val result=JSONObject(wire.toString(Charsets.UTF_8))
            require(result.optBoolean("success",true)){"Cloudflare success=false: "+result.optJSONArray("errors").toString().take(500)}
            val encoded=result.optJSONObject("result")?.optString("image").orEmpty()
            require(encoded.isNotBlank()){ "A resposta Cloudflare não contém result.image; não é prova de geração" }
            val data=if(encoded.startsWith("data:image/")){require(encoded.substringBefore(',').contains(";base64"));encoded.substringAfter(',')} else encoded
            require(data.matches(Regex("[A-Za-z0-9+/=\\r\\n\\t ]+"))){"Base64 de imagem inválido"}
            Base64.decode(data,Base64.DEFAULT)
        }else{require(contentType.startsWith("image/",true)||contentType.startsWith("application/octet-stream",true)){"Content-Type Cloudflare inesperado: ${contentType.take(100)}"};wire}
        require(image.isNotEmpty()){ "A Cloudflare devolveu uma imagem vazia" }
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(image,0,image.size,bounds)
        require(bounds.outWidth>0&&bounds.outHeight>0){ "A Cloudflare devolveu dados que não são uma imagem" }
        val sample=BitmapFactory.Options().apply{inSampleSize=if(bounds.outWidth.toLong()*bounds.outHeight>4_000_000)4 else 1}
        val decoded=BitmapFactory.decodeByteArray(image,0,image.size,sample)?:error("Imagem Cloudflare incompleta ou não descodificável")
        decoded.recycle()
        file.writeBytes(image)
        val checksum=hash(image);require(hash(file.readBytes())==checksum){"A cópia Android não corresponde aos bytes recebidos"}
        return JSONObject().put("responseContentType",contentType).put("wireSha256",hash(wire)).put("imageSha256",checksum).put("storedSha256",checksum).put("width",bounds.outWidth).put("height",bounds.outHeight).put("imageMime",bounds.outMimeType).put("bytes",image.size).put("decodedFromJson",json)
    }
}

fun cloudflareDiagnosticShare(context:android.content.Context):android.content.Intent {
    val source=File(context.filesDir,"media-diagnostics").listFiles()?.filter{it.extension=="json"}?.maxByOrNull{it.lastModified()}?:error("Ainda não existe diagnóstico. Gere uma imagem Cloudflare primeiro.")
    val cache=File(context.cacheDir,"results").apply{mkdirs()};val target=File(cache,"AIHub-Cloudflare-diagnostico.json")
    source.copyTo(target,overwrite=true)
    val uri=androidx.core.content.FileProvider.getUriForFile(context,context.packageName+".files",target)
    val send=android.content.Intent(android.content.Intent.ACTION_SEND).apply{type="application/json";putExtra(android.content.Intent.EXTRA_STREAM,uri);addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}
    return android.content.Intent.createChooser(send,"Partilhar diagnóstico (inclui a descrição do pedido)")
}
