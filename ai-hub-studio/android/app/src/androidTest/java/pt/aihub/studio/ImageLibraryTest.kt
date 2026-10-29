package pt.aihub.studio

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class ImageLibraryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val fixtureUris=mutableListOf<Uri>()
    @After fun removeOnlyTestFixtures(){fixtureUris.forEach{compose.activity.contentResolver.delete(it,null,null)}}
    private fun original():Pair<Uri,ByteArray> {
        val bitmap=Bitmap.createBitmap(321,517,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(45,80,170));setPixel(17,21,Color.rgb((System.nanoTime() and 255).toInt(),((System.nanoTime() shr 8) and 255).toInt(),91))}
        val bytes=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"AIHub-original-test-${System.nanoTime()}.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AIHub-Test-Fixtures")}
        val uri=compose.activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;fixtureUris+=uri
        compose.activity.contentResolver.openOutputStream(uri)!!.use{it.write(bytes)}
        return uri to bytes
    }
    private fun importOriginal(uri:Uri)=runBlocking{withContext(Dispatchers.Main){compose.activity.studio.importGalleryImage(uri)}}!!
    @Test fun originalBytesAndReferenceUploadArePreservedAcrossRecreation(){
        val (uri,bytes)=original();val asset=importOriginal(uri);val file=File(asset.getString("path"))
        assertArrayEquals(bytes,file.readBytes());assertEquals(321,asset.getInt("width"));assertEquals(517,asset.getInt("height"));assertEquals("image/png",asset.getString("mime"))
        val upload=ReferenceUpload(file);val body=ByteArrayOutputStream().also{upload.write(it)}.toByteArray()
        val prefix=body.toString(Charsets.ISO_8859_1).indexOf("\r\n\r\n")+4
        assertTrue(prefix>=4);assertArrayEquals(bytes,body.copyOfRange(prefix,prefix+bytes.size));assertEquals(upload.length,body.size.toLong());assertEquals("reference.png",upload.name)
        compose.activityRule.scenario.recreate();assertTrue(compose.activity.studio.assets.any{it.optString("id")==asset.getString("id")});assertArrayEquals(bytes,file.readBytes())
        compose.activity.contentResolver.openInputStream(uri)!!.use{assertArrayEquals(bytes,it.readBytes())}
    }
    @Test fun confirmationCancelsThenDeletesPrivateImageAndAllHistoryReferencesOnly(){
        val (uri,bytes)=original();val asset=importOriginal(uri);val vm=compose.activity.studio;val id=asset.getString("id");val file=File(asset.getString("path"))
        compose.runOnIdle{
            // Explicit local fixture; no AI generation is claimed by this storage/UI test.
            vm.db.save("conversation",JSONObject().put("title","Deletion fixture").put("messages",JSONArray().put(JSONObject().put("role","user").put("content","Texto a preservar")).put(JSONObject().put("role","assistant").put("assetId",id).put("content",""))))
            vm.db.save("conversation",JSONObject().put("title","Second deletion fixture").put("messages",JSONArray().put(JSONObject().put("role","assistant").put("assetId",id).put("content","Legenda a preservar"))))
            vm.vault.set("test_preference_032","retained");vm.refresh();vm.tab="Imagens"
        }
        compose.onNodeWithTag("media-gallery").performScrollToNode(hasTestTag("asset-card-$id"))
        compose.onNodeWithTag("delete-asset-$id").performClick();compose.onNodeWithText("Eliminar imagem?").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick();assertTrue(file.isFile);assertTrue(vm.assets.any{it.getString("id")==id})
        compose.onNode(hasContentDescription("Imagem selecionada da galeria") and hasAnyAncestor(hasTestTag("asset-card-$id"))).performClick()
        compose.onNodeWithTag("viewer-delete").performClick();compose.onNodeWithTag("confirm-delete-asset").performClick()
        compose.waitUntil(10000){vm.assets.none{it.getString("id")==id}}
        assertFalse(file.exists());assertTrue(vm.db.list("conversation").flatMap{it.optJSONArray("messages")?.objects().orEmpty()}.none{it.optString("assetId")==id})
        assertTrue(vm.db.list("conversation").any{it.toString().contains("Texto a preservar")});assertTrue(vm.db.list("conversation").any{it.toString().contains("Legenda a preservar")})
        compose.activity.contentResolver.openInputStream(uri)!!.use{assertArrayEquals(bytes,it.readBytes())};assertEquals("retained",vm.vault.get("test_preference_032"))
    }
    @Test fun sharedCopiesAndPathsOutsideManagedMediaAreNotDeletedPrematurely(){
        val (uri,bytes)=original();val first=importOriginal(uri);val second=importOriginal(uri);val vm=compose.activity.studio;val file=File(first.getString("path"))
        assertEquals(first.getString("path"),second.getString("path"))
        ImageLibrary(compose.activity,vm.db).delete(first.getString("id"));assertTrue(file.isFile)
        ImageLibrary(compose.activity,vm.db).delete(second.getString("id"));assertFalse(file.exists())
        val outside=File(compose.activity.getExternalFilesDir(null),"untouched-original-${System.nanoTime()}.png").apply{writeBytes(bytes)}
        try {
            val id=vm.db.save("asset",JSONObject().put("kindMedia","image").put("path",outside.path).put("galleryUri",uri.toString()))
            ImageLibrary(compose.activity,vm.db).delete(id);assertTrue(outside.isFile)
            compose.activity.contentResolver.openInputStream(uri)!!.use{assertArrayEquals(bytes,it.readBytes())}
        }finally{outside.delete()}
    }
}
