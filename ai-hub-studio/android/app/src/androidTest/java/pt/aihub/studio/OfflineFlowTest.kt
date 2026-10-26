package pt.aihub.studio

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Exercises persisted user data through the actual Compose screen, without external services. */
class OfflineFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun localProjectSurvivesActivityRecreationAndCanBeUsedInChat() {
        val project = "Projeto offline ${System.currentTimeMillis()}"
        compose.onNodeWithText("Projetos", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Nome do projeto").performScrollTo().performTextInput(project)
        compose.onNodeWithText("Contexto, personagens e instruções").performScrollTo().performTextInput("A personagem chama-se Maria.")
        compose.onNodeWithText("Guardar neste Android").performScrollTo().performClick()
        compose.onNodeWithText(project).performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Projetos", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText(project).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Usar no Chat").performScrollTo().performClick()
        compose.onNodeWithText("Converse com os seus modelos").assertIsDisplayed()
        compose.onNodeWithText(project).performScrollTo().assertIsDisplayed()
    }
}
