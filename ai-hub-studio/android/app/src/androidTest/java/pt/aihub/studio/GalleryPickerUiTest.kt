package pt.aihub.studio

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.regex.Pattern

/** Runs separately on API 35: actual system Photo Picker, not a mocked result callback. */
@androidx.annotation.RequiresApi(33)
class GalleryPickerUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private var source:Uri?=null
    @After fun cleanupTestPhoto(){source?.let{compose.activity.contentResolver.delete(it,null,null)}}
    @Test fun systemPhotoPickerImportsOriginalAndShowsPreviewAndReference(){
        assertTrue(Build.VERSION.SDK_INT>=33)
        val context=compose.activity;val vm=context.studio;val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val bitmap=Bitmap.createBitmap(361,619,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(78,60,170));setPixel(11,21,Color.CYAN)}
        val bytes=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"AIHub-PhotoPicker-test.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AIHub-Test-Fixtures");put(MediaStore.Images.Media.IS_PENDING,1);put(MediaStore.Images.Media.DATE_TAKEN,System.currentTimeMillis())}
        source=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(source!!)!!.use{it.write(bytes)}
        context.contentResolver.update(source!!,ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)},null,null)
        assertTrue(ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context))
        val intent=ActivityResultContracts.PickVisualMedia().createIntent(context,PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        assertEquals(MediaStore.ACTION_PICK_IMAGES,intent.action)
        compose.onNodeWithTag("nav-Imagens").performClick();compose.onNodeWithTag("choose-gallery").performClick()
        try {
            val thumbnailPattern=Pattern.compile(".*:id/(icon_thumbnail|thumbnail)")
            device.wait(Until.hasObject(By.res(thumbnailPattern)),15000)
            var thumbnail=device.findObject(By.res(thumbnailPattern))
            if(thumbnail==null){
                val next=device.findObject(By.text(Pattern.compile("(?i)^(get started|continue|got it)$")))
                next?.click();device.wait(Until.hasObject(By.res(thumbnailPattern)),5000);thumbnail=device.findObject(By.res(thumbnailPattern))
            }
            if(thumbnail==null)thumbnail=device.findObjects(By.clazz("android.widget.ImageView")).filter{it.visibleBounds.width()*it.visibleBounds.height()>6000}.maxByOrNull{it.visibleBounds.width()*it.visibleBounds.height()}
            assertNotNull("O Photo Picker não apresentou a fotografia de teste",thumbnail);thumbnail!!.click()
            if(!device.wait(Until.hasObject(By.pkg(context.packageName)),5000))device.findObject(By.text(Pattern.compile("(?i)^(add|select|done|use photo)$")))?.click()
            compose.waitUntil(20000){vm.assets.any{it.optString("source")=="gallery"}}
            val asset=vm.assets.first{it.optString("source")=="gallery"}
            assertArrayEquals(bytes,File(asset.getString("path")).readBytes());assertEquals(361,asset.getInt("width"));assertEquals(619,asset.getInt("height"))
            compose.onNodeWithContentDescription("Imagem em ecrã completo com zoom").assertIsDisplayed()
            compose.onNodeWithTag("viewer-animate").performClick()
            compose.onNodeWithContentDescription("Imagem de referência").assertIsDisplayed()
            assertTrue(vm.tasks.none{it.optString("state")=="running"}) // Choosing a photo never sends it remotely.
            val icon=context.packageManager.getApplicationIcon(context.packageName)
            assertTrue(icon is AdaptiveIconDrawable)
            assertNotNull((icon as AdaptiveIconDrawable).monochrome)
            val iconBitmap=Bitmap.createBitmap(216,216,Bitmap.Config.ARGB_8888)
            icon.setBounds(0,0,216,216);icon.draw(android.graphics.Canvas(iconBitmap))
            File(context.filesDir,"icon-v032.png").outputStream().use{iconBitmap.compress(Bitmap.CompressFormat.PNG,100,it)};iconBitmap.recycle()
            File(context.filesDir,"picker-v032-test.json").writeText(org.json.JSONObject().put("test","REAL_SYSTEM_PHOTO_PICKER").put("action",intent.action).put("originalBytesPreserved",true).put("width",361).put("height",619).put("previewDisplayed",true).put("referenceDisplayed",true).put("adaptiveIcon",true).put("monochromeIcon",true).toString(2))
        }finally{
            device.dumpWindowHierarchy(File(context.filesDir,"picker-v032-hierarchy.xml"));device.takeScreenshot(File(context.filesDir,"picker-v032-screen.png"))
        }
    }
}
