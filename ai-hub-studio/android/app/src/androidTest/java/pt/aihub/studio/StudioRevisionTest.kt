package pt.aihub.studio

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking

/** Explicit fixtures for history/UI/decoder tests; no simulated provider success. */
class StudioRevisionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun task(kind:String,state:String):String=compose.activity.studio.db.save("generation",JSONObject().put("kind",kind).put("state",state).put("provider",if(kind=="video")"hf-ltx" else "cloudflare").put("prompt","TESTE LOCAL — pedido $kind").put("message","Resposta de teste: quota do fornecedor indisponível; sem inferência real.").put("created",System.currentTimeMillis()))
    @Test fun failedImageAndVideoReportsCanBeCancelledThenDeletedAndStayDeleted(){
        val vm=compose.activity.studio
        val image=task("image","failed");val video=task("video","failed")
        compose.runOnIdle{vm.refresh();vm.tab="Imagens"}
        compose.onNodeWithTag("open-request-history").performClick()
        compose.onNodeWithTag("task-history-list").performScrollToNode(hasTestTag("delete-task-$image"))
        compose.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->File(compose.activity.filesDir,"ui034-history-fixture.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
        compose.onNodeWithTag("delete-task-$image").performClick()
        compose.onNodeWithText("Cancelar",useUnmergedTree=true).performClick()
        assertTrue(vm.db.list("generation").any{it.getString("id")==image})
        compose.onNodeWithTag("delete-task-$image").performClick();compose.onNodeWithTag("confirm-delete-task").performClick()
        compose.waitUntil(5000){vm.tasks.none{it.getString("id")==image}}
        compose.onNodeWithContentDescription("Fechar pedidos").performClick()
        compose.onNodeWithTag("nav-Vídeos").performClick();compose.onNodeWithTag("open-request-history").performClick()
        compose.onNodeWithTag("task-history-list").performScrollToNode(hasTestTag("delete-task-$video"));compose.onNodeWithTag("delete-task-$video").performClick();compose.onNodeWithTag("confirm-delete-task").performClick()
        compose.waitUntil(5000){vm.tasks.none{it.getString("id")==video}}
        compose.onNodeWithContentDescription("Fechar pedidos").performClick();compose.activityRule.scenario.recreate()
        assertFalse(compose.activity.studio.tasks.any{it.getString("id") in setOf(image,video)})
    }
    @Test fun activeReportsAreProtectedAndCompletedReportDeletionPreservesAllOtherData(){
        val vm=compose.activity.studio;val queued=task("image","queued");val running=task("video","running")
        for(id in listOf(queued,running)){try{GenerationHistory(vm.db).remove(id);fail("Active report removed")}catch(expected:IllegalArgumentException){};assertTrue(vm.db.list("generation").any{it.getString("id")==id})}
        val media=File(compose.activity.filesDir,"media/test-report-preservation.png").apply{parentFile!!.mkdirs();writeBytes(byteArrayOf(1,2,3,4))}
        val asset=vm.db.save("asset",JSONObject().put("path",media.path).put("kindMedia","image").put("prompt","Ficheiro de teste, não gerado por IA"))
        val project=vm.db.save("project",JSONObject().put("name","Projeto conservado"));val conversation=vm.db.save("conversation",JSONObject().put("messages",JSONArray().put(JSONObject().put("content","Conservar"))))
        val done=task("image","done");GenerationHistory(vm.db).remove(done)
        assertFalse(vm.db.list("generation").any{it.getString("id")==done})
        assertTrue(vm.db.list("asset").any{it.getString("id")==asset});assertTrue(vm.db.list("project").any{it.getString("id")==project});assertTrue(vm.db.list("conversation").any{it.getString("id")==conversation});assertArrayEquals(byteArrayOf(1,2,3,4),media.readBytes())
        GenerationHistory(vm.db).remove(asset);assertTrue(vm.db.list("asset").any{it.getString("id")==asset})
        // Remove only explicit fixtures to avoid interfering with the live regression tests.
        listOf(queued,running,asset,project,conversation).forEach{vm.db.delete(it)};media.delete()
    }
    @Test fun cloudflareSdxlFormShowsCorrectInferenceResolutionBeforeAnyRequest(){
        val before=compose.activity.studio.tasks.map{it.getString("id")}.toSet()
        compose.runOnIdle{compose.activity.studio.tab="Imagens"}
        compose.onNodeWithContentDescription("Gerar imagem").performClick();compose.onNodeWithText("Pollinations Classic").performClick();compose.onNodeWithText("Cloudflare Workers AI").performClick()
        compose.onNodeWithTag("generation-form").performScrollToNode(hasText("@cf/black-forest-labs/flux-1-schnell"))
        compose.onNodeWithText("@cf/black-forest-labs/flux-1-schnell").performClick();compose.onNodeWithText("@cf/stabilityai/stable-diffusion-xl-base-1.0").performClick()
        compose.onNodeWithTag("generation-form").performScrollToNode(hasTestTag("sdxl-generation-size"));compose.onNodeWithTag("sdxl-generation-size").assertTextContains("1344×768",substring=true)
        compose.onNodeWithTag("generation-form").performScrollToNode(hasText("Proporção"));compose.onNodeWithText("16:9").performClick();compose.onNodeWithText("9:16").performClick()
        compose.onNodeWithTag("sdxl-generation-size").assertTextContains("768×1344",substring=true)
        assertEquals(before,compose.activity.studio.tasks.map{it.getString("id")}.toSet())
        println("FIXTURE_ONLY: repaired SDXL inference dimensions shown; no API call")
    }
    @Test fun diagnosticBundleContainsExactOriginalAndDecoderAcceptsWhitespaceBase64(){
        val context=compose.activity
        val bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.RED)}
        val bytes=java.io.ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val encoded=android.util.Base64.encodeToString(bytes,android.util.Base64.DEFAULT)
        val file=File(context.filesDir,"media/test-cf-bundle.png").apply{parentFile!!.mkdirs()}
        val diagnostic=CloudflareImages.read(java.io.ByteArrayInputStream(JSONObject().put("success",true).put("result",JSONObject().put("image",encoded)).toString().toByteArray()),"application/json",file)
        assertArrayEquals(bytes,file.readBytes())
        val asset=JSONObject().put("id","bundle-fixture").put("path",file.path).put("hash",CloudflareImages.hash(bytes)).put("cloudflareDiagnostic",diagnostic)
        val chooser=runBlocking{cloudflareImageDiagnosticShare(context,asset)}
        @Suppress("DEPRECATION") val send=chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION") val uri=send.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!
        val archive=mutableMapOf<String,ByteArray>();ZipInputStream(context.contentResolver.openInputStream(uri)!!).use{zip->while(true){val entry=zip.nextEntry?:break;archive[entry.name]=zip.readBytes()}}
        assertArrayEquals(bytes,archive["original.png"]);assertEquals(CloudflareImages.hash(bytes),JSONObject(archive["diagnostico.json"]!!.toString(Charsets.UTF_8)).getString("imageSha256"))
        file.delete();println("FIXTURE_ONLY: Base64 whitespace and exact original+diagnostic share bundle; no Cloudflare inference")
    }
}
