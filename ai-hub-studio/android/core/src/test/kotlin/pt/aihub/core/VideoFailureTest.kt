package pt.aihub.core
import kotlin.test.*
class VideoFailureTest {
    // Regression fixtures from actual provider failures; these are not live inference tests.
    @Test fun quotaAndGpuAvailabilityRemainDistinctWithoutRenderingProviderHtml() {
        val quota=videoFailure("You have exceeded your ZeroGPU quota (105s requested vs. 30s left). Try again in 23:59:59.")
        assertTrue(quota.startsWith("Quota gratuita"));assertTrue(quota.contains("105s requested vs. 30s left"))
        val unavailable=videoFailure("No GPU was available after 60s. <a href='https://example.invalid'>Create a free account</a>")
        assertTrue(unavailable.startsWith("Não há GPU"));assertFalse(unavailable.contains("<a"));assertFalse(unavailable.contains("href="))
        assertTrue(videoFailure("The requested GPU duration (270s) is larger than the maximum allowed").startsWith("Este Space exige"))
    }
}
