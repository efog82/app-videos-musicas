package pt.aihub.studio
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class OfflineFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun localProjectSurvivesActivityRecreationAndCanBeUsedInChat(){
        val name="Projeto offline ${System.currentTimeMillis()}"
        compose.onNodeWithTag("nav-Projetos").performClick()
        compose.onNodeWithText("Nome do projeto").performTextInput(name)
        compose.onNodeWithText("Contexto e personagens").performTextInput("A personagem chama-se Maria.")
        compose.onNodeWithTag("projects-screen").performScrollToNode(hasText("Criar projeto"))
        compose.onNodeWithText("Criar projeto").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-Projetos").performClick()
        compose.onNodeWithTag("projects-screen").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).assertIsDisplayed()
        val db=LocalData(compose.activity);assertTrue(db.list("project").any{it.optString("name")==name});db.close()
    }
}
