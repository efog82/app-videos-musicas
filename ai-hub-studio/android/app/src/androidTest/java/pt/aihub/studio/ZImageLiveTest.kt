package pt.aihub.studio

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** A single real generation, no quota retries or substitute image. */
class ZImageLiveTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun actualZImageGenerationPreviewsExportsSharesAndPersists(){
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveZImage")=="true")
        val vm=compose.activity.studio
        compose.runOnIdle{vm.consent=true;vm.tab="Imagens"}
        compose.onNodeWithContentDescription("Gerar imagem").performClick()
        compose.onNodeWithText("Pollinations Classic").performClick();compose.onNodeWithText("Hugging Face · Z-Image Turbo").performClick()
        compose.onNodeWithTag("generation-form").performScrollToNode(hasText("Descrição da geração"))
        compose.onNodeWithText("Descrição da geração").performTextReplacement("A red apple on a wooden table, realistic photograph, natural window light")
        compose.onNodeWithTag("generation-form").performScrollToNode(hasText("Proporção"))
        compose.onNodeWithText("16:9").performClick();compose.onNodeWithText("1:1").performClick()
        compose.onNodeWithTag("generation-form").performScrollToNode(hasTestTag("submit-generation"));compose.onNodeWithTag("submit-generation").performClick()
        compose.waitUntil(360000){vm.assets.any{it.optString("provider")=="hf-zimage"}||vm.tasks.any{it.optString("provider")=="hf-zimage"&&it.optString("state")=="failed"}}
        val failure=vm.tasks.firstOrNull{it.optString("provider")=="hf-zimage"&&it.optString("state")=="failed"};assertNull(failure?.optString("message"),failure)
        val asset=vm.assets.first{it.optString("provider")=="hf-zimage"};val file=File(asset.getString("path"))
        val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(file.path,bounds)
        assertEquals(512,bounds.outWidth);assertEquals(512,bounds.outHeight);assertTrue(file.length()>1000);assertEquals("Tongyi-MAI/Z-Image-Turbo",asset.getString("model"))
        compose.onNodeWithTag("media-gallery").performScrollToNode(hasTestTag("asset-card-${asset.getString("id")}"))
        compose.onNode(hasContentDescription("Imagem gerada") and hasAnyAncestor(hasTestTag("asset-card-${asset.getString("id")}"))).performClick()
        compose.onNodeWithContentDescription("Imagem em ecrã completo com zoom").assertIsDisplayed()
        assertTrue(asset.optString("galleryUri").isBlank())
        compose.onNodeWithTag("viewer-save").performClick()
        compose.waitUntil(15000){vm.assets.first{it.getString("id")==asset.getString("id")}.optString("galleryUri").isNotBlank()}
        val saved=vm.assets.first{it.getString("id")==asset.getString("id")}
        compose.activity.contentResolver.openInputStream(Uri.parse(saved.getString("galleryUri")))!!.use{assertArrayEquals(file.readBytes(),it.readBytes())}
        @Suppress("DEPRECATION") val share=shareAssetIntent(compose.activity,saved).getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION") val uri=share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        compose.activity.contentResolver.openInputStream(uri)!!.use{assertArrayEquals(file.readBytes(),it.readBytes())}
        File(compose.activity.filesDir,"zimage-v033-test.json").writeText(JSONObject().put("type","REAL_ANDROID_GENERATION").put("provider","hf-zimage").put("width",bounds.outWidth).put("height",bounds.outHeight).put("bytes",file.length()).put("sha256",CloudflareImages.hash(file.readBytes())).put("preview",true).put("galleryExactBytes",true).put("shareExactBytes",true).toString(2))
        file.copyTo(File(compose.activity.filesDir,"zimage-v033-original.png"),overwrite=true)
        compose.onNodeWithContentDescription("Fechar pré-visualização").performClick();compose.activityRule.scenario.recreate()
        assertTrue(compose.activity.studio.assets.any{it.getString("id")==asset.getString("id")})
        assertEquals(pt.aihub.core.MediaAvailability.AVAILABLE,compose.activity.studio.mediaStatus.get("hf-zimage","Tongyi-MAI/Z-Image-Turbo").state)
        println("REAL_ZIMAGE_PASS: API inference, original 512×512 PNG, integrated preview, gallery and shared exact bytes, persistent history and quota state")
    }
}
