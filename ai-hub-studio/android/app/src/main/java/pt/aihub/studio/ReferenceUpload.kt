package pt.aihub.studio

import java.io.File
import java.io.OutputStream
import java.net.URLConnection
import java.util.UUID

/** Multipart envelope only: the original image bytes, dimensions and EXIF stay untouched. */
class ReferenceUpload(private val file:File) {
    val boundary="AIHub"+UUID.randomUUID()
    private val extension=file.extension.lowercase().takeIf{it in setOf("jpg","jpeg","png","webp","gif","heic","heif","avif","bmp")}.orEmpty()
    val name=if(extension.isEmpty())"reference" else "reference.$extension"
    private val mime=URLConnection.guessContentTypeFromName(name)?:when(extension){"webp"->"image/webp";"heic"->"image/heic";"heif"->"image/heif";"avif"->"image/avif";else->"application/octet-stream"}
    private val prefix="--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"$name\"\r\nContent-Type: $mime\r\n\r\n".toByteArray()
    private val suffix="\r\n--$boundary--\r\n".toByteArray()
    val length:Long get()=prefix.size.toLong()+file.length()+suffix.size
    fun write(output:OutputStream){output.write(prefix);file.inputStream().use{it.copyTo(output,65536)};output.write(suffix)}
}
