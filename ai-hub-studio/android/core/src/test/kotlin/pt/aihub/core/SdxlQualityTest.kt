package pt.aihub.core
import kotlin.test.*
class SdxlQualityTest {
    @Test fun repairsTheReportedPortraitAndAllOldSmallPresets(){
        assertEquals(768 to 1344,SdxlQuality.effective(288,512))
        assertEquals(1344 to 768,SdxlQuality.effective(512,288))
        assertEquals(1024 to 1024,SdxlQuality.effective(512,512))
        val request=CloudflareImageRequest.parameters(CloudflareImageRequest.SDXL,"galaxy",288,512,42)
        assertEquals(768,request["width"]);assertEquals(1344,request["height"]);assertEquals(20,request["num_steps"]);assertEquals(7.5,request["guidance"])
    }
    @Test fun sizesAreActualInferenceSizesAndFluxAndAdequateRequestsRemainIntact(){
        for(aspect in listOf("1:1","4:5","9:16","16:9")){
            val (w,h)=SdxlQuality.forAspect(aspect);assertTrue(w>=768&&h>=768);assertEquals(0,w%64);assertEquals(0,h%64);assertTrue(w.toLong()*h in 900000L..1100000L)
        }
        assertEquals(1024 to 1536,SdxlQuality.effective(1024,1536))
        assertFailsWith<IllegalArgumentException>{SdxlQuality.effective(2049,512)}
        assertEquals(setOf("prompt","steps"),CloudflareImageRequest.parameters(CloudflareImageRequest.FLUX,"galaxy",288,512,42,8).keys)
    }
    @Test fun visualPreparationIsAnExplicitSeparateTaskWithoutMediaOrToolInstructions(){
        assertTrue(VisualPromptRules.system("image").contains("ONE concise English visual prompt"))
        assertTrue(VisualPromptRules.system("image").contains("Do not call tools or generate media"))
        assertTrue(VisualPromptRules.system("video").contains("camera motion"))
        assertFalse(VisualPromptRules.system("image").contains("camera motion"))
    }
    @Test fun candidateIsReviewedNotSilentlyTruncatedOrAutoSubmitted(){
        assertEquals("A spiral galaxy",VisualPromptRules.reviewedCandidate("```text\nA spiral galaxy\n```"))
        assertFailsWith<IllegalArgumentException>{VisualPromptRules.reviewedCandidate(" ")}
        assertFailsWith<IllegalArgumentException>{VisualPromptRules.reviewedCandidate("a".repeat(1201))}
    }
}
