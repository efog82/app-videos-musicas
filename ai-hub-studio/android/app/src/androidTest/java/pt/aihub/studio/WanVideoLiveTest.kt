package pt.aihub.studio

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real anonymous Wan inference from Android. Only the input artwork is a labelled fixture. */
class WanVideoLiveTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun wanImageToVideoIsReceivedPlayedExportedAndRetainedInProject(){
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveProviders")=="true")
        val vm=compose.activity.studio
        val prompt="A paper boat drifting on a turquoise mountain lake, rippling water, soft golden sunlight, natural cinematic motion, no text"
        val fixture=File(compose.activity.filesDir,"wan-reference-fixture.png")
        val bitmap=Bitmap.createBitmap(480,832,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint().apply{color=Color.rgb(120,188,214)};canvas.drawRect(0f,0f,480f,832f,paint)
        paint.color=Color.rgb(54,140,154);canvas.drawRect(0f,380f,480f,832f,paint)
        paint.color=Color.rgb(80,110,138);canvas.drawPath(android.graphics.Path().apply{moveTo(0f,380f);lineTo(170f,130f);lineTo(350f,380f);close()},paint)
        paint.color=Color.WHITE;canvas.drawPath(android.graphics.Path().apply{moveTo(190f,510f);lineTo(300f,510f);lineTo(250f,550f);close()},paint)
        fixture.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        lateinit var referenceId:String
        lateinit var projectId:String
        compose.runOnIdle {
            vm.consent=true;vm.saveProject("Wan real ${System.currentTimeMillis()}",prompt);projectId=vm.projects.first().getString("id");vm.currentProject=projectId
            referenceId=vm.db.save("asset",JSONObject().put("path",fixture.path).put("kindMedia","image").put("mime","image/png").put("prompt","Referência de teste desenhada, não é uma geração de IA").put("provider","test-input-fixture").put("project",projectId).put("conversation",""))
            vm.refresh();vm.tab="Imagens"
        }
        compose.onNodeWithTag("media-gallery").performScrollToNode(hasTestTag("asset-card-$referenceId"))
        compose.onNode(hasContentDescription("Imagem gerada") and hasAnyAncestor(hasTestTag("asset-card-$referenceId"))).performClick()
        compose.onNodeWithTag("viewer-animate").performClick()
        compose.onNodeWithTag("generation-form").performScrollToNode(hasText("Hugging Face · LTX Distilled"))
        compose.onNodeWithText("Hugging Face · LTX Distilled").performClick()
        compose.onNodeWithText("Hugging Face · Wan 2.2 I2V 14B").performClick()
        compose.onNodeWithTag("generation-form").performScrollToNode(hasText("Duração (0,5–5 s)"))
        compose.onNodeWithText("Duração (0,5–5 s)").performTextReplacement("2.0")
        compose.onNodeWithTag("generation-form").performScrollToNode(hasTestTag("submit-generation"));compose.onNodeWithTag("submit-generation").performClick()
        compose.waitUntil(420000){vm.assets.any{it.optString("provider")=="hf-wan22-i2v"}||vm.tasks.any{it.optString("provider")=="hf-wan22-i2v"&&it.optString("state")=="failed"}}
        val failures=vm.tasks.filter{it.optString("provider")=="hf-wan22-i2v"&&it.optString("state")=="failed"};assertTrue(failures.joinToString{it.optString("message")},failures.isEmpty())
        val video=vm.assets.first{it.optString("provider")=="hf-wan22-i2v"}
        assertEquals(projectId,video.getString("project"));assertEquals(480,video.getInt("width"));assertEquals(832,video.getInt("height"));assertTrue(video.getLong("durationMs") in 1900..2200)
        assertTrue(File(video.getString("path")).length()>1000)
        compose.onNodeWithTag("nav-Vídeos").performClick()
        compose.onNodeWithTag("media-gallery").performScrollToNode(hasTestTag("asset-card-${video.getString("id")}"))
        compose.onNode(hasContentDescription("Reproduzir vídeo") and hasAnyAncestor(hasTestTag("asset-card-${video.getString("id")}"))).performClick()
        compose.waitUntil(20000){compose.onAllNodes(hasStateDescription("Vídeo preparado")).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("integrated-video-player").assertIsDisplayed()
        // Preview is confirmed before the optional gallery export.
        assertTrue(video.optString("galleryUri").isBlank())
        compose.onNodeWithTag("viewer-save").performClick()
        compose.waitUntil(15000){vm.assets.first{it.getString("id")==video.getString("id")}.optString("galleryUri").isNotBlank()}
        val saved=vm.assets.first{it.getString("id")==video.getString("id")}
        compose.activity.contentResolver.openInputStream(Uri.parse(saved.getString("galleryUri")))!!.use{assertTrue(it.read()!=-1)}
        @Suppress("DEPRECATION") val send=shareAssetIntent(compose.activity,saved).getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("video/mp4",send.type);assertEquals(Intent.ACTION_SEND,send.action)
        @Suppress("DEPRECATION") val uri=send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        compose.activity.contentResolver.openInputStream(uri)!!.use{assertTrue(it.read()!=-1)}
        File(compose.activity.filesDir,"wan-v031-test.json").writeText(JSONObject().put("test","REAL_ANDROID_NETWORK").put("provider","hf-wan22-i2v").put("reference","drawn fixture, not an AI output").put("durationMs",video.getLong("durationMs")).put("width",video.getInt("width")).put("height",video.getInt("height")).put("mp4Bytes",File(video.getString("path")).length()).put("firstFrameRendered",true).put("exportRead",true).put("shareUriRead",true).toString(2))
        compose.onNodeWithContentDescription("Fechar pré-visualização").performClick();compose.activityRule.scenario.recreate()
        assertTrue(compose.activity.studio.assets.any{it.getString("id")==video.getString("id")&&it.optString("project")==projectId})
        assertTrue(File(video.getString("path")).isFile)
        println("REAL_WAN_VIDEO_PASS: direct API, portrait MP4, first decoded video frame, optional gallery export, share URI and persistent project")
    }
}
