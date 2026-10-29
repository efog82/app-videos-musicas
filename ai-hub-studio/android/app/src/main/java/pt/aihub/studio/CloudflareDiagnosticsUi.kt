package pt.aihub.studio

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable fun CloudflareComparisonDialog(onDismiss:()->Unit,onStart:(String)->Unit){
    var apple by remember{mutableStateOf(false)}
    val prompt=if(apple)"A single red apple on a wooden table, realistic photograph, natural window light, clearly recognizable apple, no text" else "A synthetic infrared-inspired image of a realistic spiral galaxy, James Webb telescope visual style, clearly defined spiral arms, dense stellar core, fine dust lanes, distant stars, deep black background, natural astronomical detail, no text"
    AlertDialog(onDismissRequest=onDismiss,title={Text("Comparar a Cloudflare")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Executa até 3 gerações reais: FLUX com 4 passos, FLUX com 8 passos e SDXL a 1024×1024. Consome quota gratuita. Os resultados aparecem em Imagens; não há ativação de faturação nem repetição após esgotamento conhecido.")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=!apple,onClick={apple=false},label={Text("Galáxia")});FilterChip(selected=apple,onClick={apple=true},label={Text("Maçã")})}
        Text(prompt,style=MaterialTheme.typography.bodySmall)
        Text("Guarda primeiro o Account ID, o token e a confirmação Workers Free. Uma imagem aceite não comprova qualidade; compara visualmente os três resultados.",style=MaterialTheme.typography.labelSmall)
    }},confirmButton={TextButton(onClick={onStart(prompt)}){Text("Autorizar 3 pedidos gratuitos")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}
fun findCloudflareDiagnostic(context:Context,asset:JSONObject):JSONObject {
    asset.optJSONObject("cloudflareDiagnostic")?.let{return JSONObject(it.toString())}
    val hash=asset.optString("hash")
    return File(context.filesDir,"media-diagnostics").listFiles()?.filter{it.extension=="json"}?.sortedByDescending{it.lastModified()}?.asSequence()?.mapNotNull{runCatching{JSONObject(it.readText())}.getOrNull()}?.firstOrNull{it.optString("imageSha256")==hash}
        ?:error("Esta imagem é anterior ao diagnóstico por resultado, ou o diagnóstico já não existe. O ficheiro original continua disponível para guardar/partilhar.")
}
suspend fun cloudflareImageDiagnosticShare(context:Context,asset:JSONObject):Intent=withContext(Dispatchers.IO){
    val diagnostic=findCloudflareDiagnostic(context,asset)
    val file=File(asset.getString("path"));val root=File(context.filesDir,"media").canonicalFile
    require(file.isFile&&file.canonicalFile.toPath().startsWith(root.toPath())){"A imagem não está no armazenamento privado gerido pela app"}
    require(CloudflareImages.hash(file.readBytes())==diagnostic.optString("imageSha256")){"Os hashes da imagem e do diagnóstico não correspondem"}
    val dir=File(context.cacheDir,"results").apply{mkdirs()};val bundle=File(dir,"AIHub-Cloudflare-${asset.getString("id").take(8)}.zip")
    ZipOutputStream(bundle.outputStream()).use{zip->
        zip.putNextEntry(ZipEntry("diagnostico.json"));zip.write(diagnostic.toString(2).toByteArray());zip.closeEntry()
        zip.putNextEntry(ZipEntry("original.${file.extension}"));file.inputStream().use{it.copyTo(zip)};zip.closeEntry()
    }
    val uri=FileProvider.getUriForFile(context,context.packageName+".files",bundle)
    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Partilhar original e diagnóstico (inclui o pedido)")
}
@Composable fun CloudflareResultDialog(asset:JSONObject,onDismiss:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var diagnostic by remember{mutableStateOf<JSONObject?>(null)};var error by remember{mutableStateOf("")};var sharing by remember{mutableStateOf(false)}
    LaunchedEffect(asset.optString("id")){try{diagnostic=withContext(Dispatchers.IO){findCloudflareDiagnostic(context,asset)}}catch(e:Exception){error=connectionMessage(e)}}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Diagnóstico desta imagem")},text={Column(Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("A imagem apresentada e exportada mantém os bytes recebidos. Hashes iguais comprovam a cópia; não comprovam correspondência ao pedido.",style=MaterialTheme.typography.bodySmall)
        diagnostic?.let{data->val request=data.optJSONObject("request");Text("Modelo no endpoint: ${data.optString("modelEndpoint")}\nSaída: ${data.optInt("width")}×${data.optInt("height")} · ${data.optString("imageMime")}\nHTTP ${data.optInt("http")}\nPassos: ${request?.opt("num_steps")?:request?.opt("steps")?:"não informado"}",style=MaterialTheme.typography.bodySmall)
            Text("Pedido enviado",style=MaterialTheme.typography.titleSmall);Text(request?.optString("prompt").orEmpty(),style=MaterialTheme.typography.bodySmall)
            Text("O servidor não devolveu uma identificação independente dos pesos executados. Qualidade visual por avaliar.",style=MaterialTheme.typography.labelSmall)
        }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=diagnostic!=null&&!sharing,onClick={sharing=true;scope.launch{try{context.startActivity(cloudflareImageDiagnosticShare(context,asset))}catch(e:Exception){error=connectionMessage(e)}finally{sharing=false}}}){Text(if(sharing)"A preparar…" else "Partilhar original + diagnóstico")}},dismissButton={TextButton(onClick=onDismiss){Text("Fechar")}})
}
