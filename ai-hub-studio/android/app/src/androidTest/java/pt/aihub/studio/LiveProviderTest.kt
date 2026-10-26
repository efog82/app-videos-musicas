package pt.aihub.studio

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual UI -> Android HTTP -> public model -> persisted answer. No mocked provider. */
class LiveProviderTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realAnonymousProviderDiscoversModelAndAnswersQuestion() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveProviders")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val report=StringBuilder("REAL_NETWORK_UI_TEST\n")
        val data=LocalData(context)
        try {
            compose.onNodeWithText("Configuração",useUnmergedTree=true).performScrollTo().performClick()
            compose.onNodeWithTag("studio-content").performScrollToNode(hasText("Autorizar envio de conteúdo ao fornecedor escolhido"))
            compose.onNodeWithText("Autorizar envio de conteúdo ao fornecedor escolhido").performScrollTo().performClick()
            compose.onNodeWithTag("studio-content").performScrollToNode(hasText("Descobrir e testar IA"))
            compose.onNodeWithText("Descobrir e testar IA").performScrollTo().performClick()
            compose.waitUntil(240000) { compose.onAllNodesWithText("resposta real recebida",substring=true).fetchSemanticsNodes().isNotEmpty() }
            report.append("UI: discover and test returned a real model response\n")
            compose.onNodeWithTag("studio-content").performScrollToNode(hasText("Ir para o Chat"))
            compose.onNodeWithText("Ir para o Chat").performScrollTo().performClick()
            compose.onNodeWithTag("studio-content").performScrollToNode(hasText("Escreva uma mensagem"))
            compose.onNodeWithText("Escreva uma mensagem").performScrollTo().performTextInput("Quanto é 2+2? Responde apenas 4.")
            compose.onNodeWithTag("studio-content").performScrollToNode(hasText("Enviar"))
            compose.onNodeWithText("Enviar").performScrollTo().performClick()
            compose.waitUntil(240000) { data.list("conversation").any {it.optString("title").startsWith("Quanto é 2+2?")} }
            val conversation=data.list("conversation").first{it.optString("title").startsWith("Quanto é 2+2?")}
            val answer=conversation.getJSONArray("messages").objects().last{it.optString("role")=="assistant"}.getString("content")
            assertTrue(answer.contains("4"))
            report.append("Question sent through UI: Quanto é 2+2?\nActual provider response: "+answer+"\nPASS\n")
        } catch(e:Throwable) {report.append("FAIL: "+connectionMessage(e));throw e}
        finally {data.close();java.io.File(context.getExternalFilesDir(null),"live-provider.txt").writeText(report.toString())}
    }
}
