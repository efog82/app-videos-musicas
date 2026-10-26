package pt.aihub.studio
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class LiveProviderTest {
    @Test fun realAnonymousProviderDiscoversModelAndAnswersQuestion() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveProviders")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val vault=Vault(context)
        val ai=DirectAi(vault)
        val report=StringBuilder("REAL_NETWORK_TEST\n")
        try {
            val catalog=ai.discover("pollinations")
            assertTrue(catalog.any{it.optString("provider")=="pollinations"})
            report.append("Discovery: "+catalog.toString()+"\n")
            report.append(ai.test("pollinations",consent=true,freePlan=false)+"\n")
            val answer=ai.chat("",listOf(JSONObject().put("role","user").put("content","Quanto é 2+2? Responde apenas 4.")),true,false)
            assertTrue(answer.contains("4"))
            report.append("Question: Quanto é 2+2?\nActual response: "+answer+"\nPASS\n")
        } catch(e:Exception){report.append("FAIL: "+connectionMessage(e));throw e}
        finally{java.io.File(context.getExternalFilesDir(null),"live-provider.txt").writeText(report.toString())}
    }
}
