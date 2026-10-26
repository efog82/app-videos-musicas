package pt.aihub.studio
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveProviderTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realAnonymousProviderDiscoversModelAndAnswersQuestion(){
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveProviders")=="true")
        val vm=compose.activity.studio
        compose.onNodeWithContentDescription("Fornecedores e definições").performClick()
        if(!vm.consent){compose.onNodeWithTag("providers-settings").performScrollToNode(hasText("Autorizar envio de conteúdo aos fornecedores escolhidos"));compose.onNodeWithText("Autorizar envio de conteúdo aos fornecedores escolhidos").performClick()}
        compose.onNodeWithTag("providers-settings").performScrollToNode(hasText("Descobrir e testar IA"))
        compose.onNodeWithText("Descobrir e testar IA").performClick()
        compose.waitUntil(240000){!vm.configurationBusy&&vm.status.contains("resposta real recebida")}
        compose.onNodeWithContentDescription("Fechar definições").performClick()
        compose.onNodeWithTag("chat-composer").performTextInput("Quanto é 2+2? Responde apenas 4.")
        compose.onNodeWithContentDescription("Enviar").performClick()
        compose.waitUntil(240000){!vm.chatBusy&&vm.messages.lastOrNull()?.optString("role")=="assistant"}
        val answer=vm.messages.last().getString("content")
        assertTrue(answer.contains("4"))
        println("REAL_NETWORK_UI_TEST v0.3: actual provider response: $answer — PASS")
        assertTrue(vm.db.list("conversation").isNotEmpty())
    }
}
