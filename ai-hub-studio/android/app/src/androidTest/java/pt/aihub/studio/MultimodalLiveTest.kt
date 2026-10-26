package pt.aihub.studio

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Live HTTP from Android and actual media decoding, gallery export and persistent associations. */
class MultimodalLiveTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realImagesAndImageToVideoAppearWithoutExportThenPersist(){
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveProviders")=="true")
        val vm=compose.activity.studio
        val prompt="A cinematic paper boat on a turquoise lake, mountains, sunlight, no text"
        val project="Multimédia real ${System.currentTimeMillis()}"
        val seed=System.currentTimeMillis()%1000000000
        compose.runOnIdle{
            vm.consent=true;vm.saveProject(project,prompt);vm.currentProject=vm.projects.first{it.optString("name")==project}.getString("id")
            // Explicit test fixture for the source scene; the separate chat test exercises real inference.
            val id=vm.db.save("conversation",org.json.JSONObject().put("title","Cena de teste multimédia").put("messages",org.json.JSONArray().put(org.json.JSONObject().put("role","assistant").put("content",prompt))).put("project",vm.currentProject))
            vm.refresh();vm.openConversation(vm.conversations.first{it.getString("id")==id})
        }
        compose.onNodeWithText("Gerar imagem").performClick()
        compose.onNodeWithTag("generation-form").performScrollToNode(hasTestTag("submit-generation"))
        compose.onNodeWithTag("submit-generation").performClick()
        fun waitAsset(provider:String,kind:String){compose.waitUntil(360000){vm.assets.any{it.optString("provider")==provider&&it.optString("kindMedia")==kind}||vm.tasks.any{it.optString("provider")==provider&&it.optString("state")=="failed"}};assertFalse(vm.tasks.filter{it.optString("provider")==provider}.joinToString{it.optString("message")},vm.tasks.any{it.optString("provider")==provider&&it.optString("state")=="failed"})}
        waitAsset("pollinations-image","image")
        val image=vm.assets.first{it.optString("provider")=="pollinations-image"}
        assertTrue(File(image.getString("path")).length()>1000)
        assertTrue(vm.messages.any{it.optString("assetId")==image.getString("id")})
        compose.onNodeWithTag("chat-timeline").performScrollToNode(hasTestTag("asset-image"))
        compose.onAllNodesWithTag("asset-image").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Imagem gerada").performClick()
        compose.onNodeWithContentDescription("Imagem em ecrã completo com zoom").assertIsDisplayed()
        compose.onNodeWithTag("viewer-save").performClick()
        compose.waitUntil(15000){vm.assets.first{it.optString("id")==image.getString("id")}.optString("galleryUri").isNotBlank()}
        val saved=vm.assets.first{it.optString("id")==image.getString("id")}
        compose.activity.contentResolver.openInputStream(Uri.parse(saved.getString("galleryUri")))!!.use{assertTrue(it.read()!=-1)}
        @Suppress("DEPRECATION") val send=shareAssetIntent(compose.activity,image).getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND,send.action);assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
        @Suppress("DEPRECATION") val uri=send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        compose.activity.contentResolver.openInputStream(uri)!!.use{assertTrue(it.read()!=-1)}
        compose.onNodeWithTag("viewer-animate").performClick()
        compose.onNodeWithTag("generation-form").performScrollToNode(hasText("Duração (0,3–8,5 s)"))
        compose.onNodeWithText("Duração (0,3–8,5 s)").performTextReplacement("1.1")
        compose.onNodeWithTag("generation-form").performScrollToNode(hasTestTag("submit-generation"));compose.onNodeWithTag("submit-generation").performClick()
        waitAsset("hf-ltx","video")
        val video=vm.assets.first{it.optString("kindMedia")=="video"}
        assertTrue(vm.messages.any{it.optString("assetId")==video.getString("id")})
        compose.onNodeWithTag("chat-timeline").performScrollToNode(hasContentDescription("Reproduzir vídeo"))
        compose.onNodeWithContentDescription("Reproduzir vídeo").performClick()
        compose.waitUntil(20000){compose.onAllNodes(hasStateDescription("Vídeo preparado")).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("integrated-video-player").assertIsDisplayed()
        compose.activity.window.decorView.post{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->File(compose.activity.filesDir,"v3-video-preview.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}}
        val videoUri=runBlocking{vm.export(video)};compose.activity.contentResolver.openInputStream(videoUri)!!.use{assertTrue(it.read()!=-1)}
        compose.onNodeWithContentDescription("Fechar pré-visualização").performClick()
        compose.runOnIdle{vm.generate(mediaProviders.first{it.id=="hf-flux"},prompt,512,512,seed,1.1,"black-forest-labs/FLUX.1-schnell",null,1)}
        waitAsset("hf-flux","image")
        val persisted=LocalData(compose.activity);assertTrue(persisted.list("asset").count{it.optString("project")==vm.currentProject}>=3);persisted.close()
        val old=vm.assets.map{it.getString("path")};compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-Imagens").performClick()
        assertTrue(compose.activity.studio.assets.size>=3);old.forEach{assertTrue(File(it).exists())}
        println("REAL_MULTIMODAL_PASS: Android Pollinations image, HF Flux image, HF LTX image-to-video, inline image, first video frame, gallery image/video, FileProvider share URI and persistent project association")
    }
}
