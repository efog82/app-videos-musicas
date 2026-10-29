package pt.aihub.studio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pt.aihub.core.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Deterministic validation fixtures: these tests do NOT claim Cloudflare inference. */
class MediaAuditTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun cloudflareJsonAndBinaryPreserveOriginalBytesAndDisplayedPixels(){
        val bitmap=Bitmap.createBitmap(128,96,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED);setPixel(21,17,Color.BLUE)}
        val bytes=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val file=File(compose.activity.cacheDir,"cf-decoder-fixture.png")
        val json=JSONObject().put("success",true).put("result",JSONObject().put("image",Base64.encodeToString(bytes,Base64.NO_WRAP)))
        val evidence=CloudflareImages.read(ByteArrayInputStream(json.toString().toByteArray()),"application/json; charset=utf-8",file)
        assertArrayEquals(bytes,file.readBytes());assertEquals(evidence.getString("imageSha256"),evidence.getString("storedSha256"))
        val shown=BitmapFactory.decodeFile(file.path);assertEquals(Color.RED,shown.getPixel(0,0));assertEquals(Color.BLUE,shown.getPixel(21,17));shown.recycle()
        CloudflareImages.read(ByteArrayInputStream(bytes),"image/png",file);assertArrayEquals(bytes,file.readBytes())
        file.delete()
        for(response in listOf("<html>Service error</html>","{\"success\":false,\"errors\":[{\"code\":3036}]}","{\"success\":true,\"result\":{\"image\":\"%%%\"}}")){
            try{CloudflareImages.read(ByteArrayInputStream(response.toByteArray()),if(response.startsWith("{"))"application/json" else "text/html",file);fail("Invalid response accepted")}catch(expected:IllegalArgumentException){}
            assertFalse(file.exists())
        }
        assertEquals(setOf("prompt","steps"),CloudflareImages.body(CloudflareImageRequest.FLUX,"apple",512,512,42,8).keys().asSequence().toSet())
        println("FIXTURE_ONLY: JSON/base64/binary original bytes and decoded pixels preserved; HTTP 200 error/HTML rejected")
    }
    @Test fun quotaPersistsPerModelAndCountdownUpdatesWithoutNetwork(){
        val vm=compose.activity.studio;val model=CloudflareImageRequest.FLUX
        val reset=System.currentTimeMillis()+65000
        compose.runOnIdle{vm.mediaStatus.put("cloudflare",model,MediaQuota(MediaAvailability.WAITING,System.currentTimeMillis(),reset,source="TEST FIXTURE: Retry-After"));vm.tab="Imagens"}
        compose.onNodeWithContentDescription("Gerar imagem").performClick()
        compose.onNodeWithText("Pollinations Classic").performClick();compose.onNodeWithText("Cloudflare Workers AI").performClick()
        compose.onNodeWithTag("generation-form").performScrollToNode(hasTestTag("media-availability-cloudflare"))
        compose.onNodeWithTag("media-availability-cloudflare").assertTextContains("Em espera",substring=true)
        val before=compose.onNodeWithTag("media-availability-cloudflare").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        compose.waitUntil(5000){compose.onNodeWithTag("media-availability-cloudflare").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()!=before}
        val fresh=LocalData(compose.activity);assertEquals(reset,MediaStatusStore(fresh).get("cloudflare",model).resetAt);fresh.close()
        assertEquals(MediaAvailability.UNKNOWN,vm.mediaStatus.get("hf-zimage","unobserved").state)
        compose.runOnIdle{vm.mediaStatus.credentialsChanged(setOf("cloudflare"))}
        assertEquals(MediaAvailability.UNKNOWN,vm.mediaStatus.get("cloudflare",model).state)
        println("FIXTURE_ONLY: persisted quota, independent model state and locally updating countdown; zero network requests")
    }
}
