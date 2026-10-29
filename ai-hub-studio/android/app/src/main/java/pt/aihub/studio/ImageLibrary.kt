package pt.aihub.studio

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ImportedOriginal(val file:File,val name:String,val width:Int,val height:Int)

/** Copies the user-selected stream exactly; never edits or deletes the source URI. */
class OriginalImageImporter(private val context:Context) {
    suspend fun copy(uri:Uri):ImportedOriginal {
        require(uri.scheme=="content"){"Selecione uma imagem através do seletor Android"}
        val resolver=context.contentResolver
        val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->if(cursor.moveToFirst())cursor.getString(0) else null}?:"Imagem da galeria"
        val folder=File(context.cacheDir,"image-imports").apply{mkdirs()}
        val file=File(folder,UUID.randomUUID().toString()+".original")
        try {
            val coroutine=currentCoroutineContext()
            resolver.openInputStream(uri)?.use{input->file.outputStream().use{output->
                val buffer=ByteArray(65536)
                while(true){coroutine.ensureActive();val count=input.read(buffer);if(count<0)break;output.write(buffer,0,count)}
            }}?:error("O Android não permitiu ler a imagem selecionada")
            val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeFile(file.path,options)
            require(options.outWidth>0&&options.outHeight>0){"O ficheiro não é uma imagem compatível com este Android; o original não foi alterado"}
            return ImportedOriginal(file,name,options.outWidth,options.outHeight)
        } catch(e:Exception){file.delete();throw e}
    }
}

/** Keep text while removing only the deleted media reference. */
fun removeImageReferences(messages:List<JSONObject>,removed:Set<String>):List<JSONObject> = messages.mapNotNull { message->
    if(message.optString("assetId") !in removed)message
    else if(message.optString("content").isNotBlank()||(message.optJSONArray("attachments")?.length()?:0)>0)JSONObject(message.toString()).apply{remove("assetId")}
    else null
}

class ImageLibrary(private val context:Context,private val db:LocalData) {
    fun delete(id:String) {
        val database=db.writableDatabase
        database.beginTransaction()
        try {
            val asset=db.list("asset").firstOrNull{it.optString("id")==id}?:return
            require(db.list("generation").none{it.optString("reference")==id&&it.optString("state") in listOf("queued","running")}){"Esta imagem está a ser usada numa geração. Cancele ou aguarde a tarefa antes de eliminar."}
            val root=File(context.filesDir,"media").canonicalFile
            val candidate=asset.optString("path").takeIf{it.isNotBlank()}?.let{File(it).canonicalFile}
            val shared=candidate!=null&&db.list("asset").any{it.optString("id")!=id&&it.optString("path").isNotBlank()&&File(it.getString("path")).canonicalFile==candidate}
            // Paths imported by older versions can point outside media. Never delete those.
            if(candidate!=null&&!shared&&candidate.path.startsWith(root.path+File.separator)&&candidate.isFile) {
                check(candidate.delete()){"Não foi possível eliminar a cópia privada. A imagem foi mantida no histórico."}
            }
            db.delete(id)
            for(conversation in db.list("conversation")) {
                val before=conversation.optJSONArray("messages")?.objects().orEmpty()
                if(before.any{it.optString("assetId")==id})db.save("conversation",JSONObject(conversation.toString()).put("messages",JSONArray(removeImageReferences(before,setOf(id)))),conversation.getString("id"))
            }
            for(task in db.list("generation").filter{it.optString("reference")==id})db.save("generation",JSONObject(task.toString()).put("reference","").put("referenceDeleted",true),task.getString("id"))
            database.setTransactionSuccessful()
        }finally{database.endTransaction()}
    }
}
