package pt.aihub.core
import kotlin.test.*
class CloudflareImageRequestTest {
    @Test fun fluxMinimalSchemaAndExplicitFourEightSteps(){
        for(steps in listOf(4,8)){
            val body=CloudflareImageRequest.parameters(CloudflareImageRequest.FLUX,"A red apple",512,512,42,steps)
            assertEquals(setOf("prompt","steps"),body.keys);assertEquals(steps,body["steps"])
        }
        assertFailsWith<IllegalArgumentException>{CloudflareImageRequest.parameters(CloudflareImageRequest.FLUX,"apple",512,512,42,9)}
    }
    @Test fun sdxlHasItsOwnSupportedParametersAndModelMustBeExact(){
        val body=CloudflareImageRequest.parameters(CloudflareImageRequest.SDXL,"apple",576,1024,42)
        assertEquals(20,body["num_steps"]);assertEquals(7.5,body["guidance"]);assertEquals(42L,body["seed"]);assertFalse(body.containsKey("steps"))
        assertFailsWith<IllegalStateException>{CloudflareImageRequest.parameters("fake-flux", "apple",512,512,42)}
    }
}
