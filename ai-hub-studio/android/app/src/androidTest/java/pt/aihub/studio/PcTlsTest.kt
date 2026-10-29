package pt.aihub.studio
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PcTlsTest {
    @Test fun actualAndroidToCompanionTlsAndAuthentication():Unit=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        val url=args.getString("pcUrl").orEmpty()
        org.junit.Assume.assumeTrue("Companion real não configurado",url.isNotBlank())
        val token=args.getString("pcToken").orEmpty();val pin=args.getString("pcPin").orEmpty()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val report=StringBuilder("REAL_ANDROID_PC_TLS_TEST\n")
        try {
            val api=Api({url},{token},fingerprint={pin},isPc=true)
            assertEquals("AI Hub Studio",api.obj("/health").getString("service"))
            assertTrue(api.obj("/v1/diagnostics").getBoolean("authenticated"))
            val badToken=Api({url},{"invalid-token"},fingerprint={pin},isPc=true)
            assertTrue(runCatching{badToken.obj("/v1/diagnostics")}.isFailure)
            val wrong=if(pin.startsWith("0"))"1"+pin.drop(1) else "0"+pin.drop(1)
            val badPin=Api({url},{token},fingerprint={wrong},isPc=true)
            assertTrue(runCatching{badPin.obj("/health")}.isFailure)
            report.append("PASS: real TLS host alias 10.0.2.2; valid certificate pin and token accepted; wrong pin and token rejected.\nNo model inference claimed by this transport test.\n")
        } catch(e:Throwable){report.append("FAIL: "+connectionMessage(e));throw e}
        finally{java.io.File(context.getExternalFilesDir(null),"pc-tls.txt").writeText(report.toString())}
    }
}
