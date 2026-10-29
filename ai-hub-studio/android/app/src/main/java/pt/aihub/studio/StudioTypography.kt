package pt.aihub.studio
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
fun studioTypography():Typography {val t=Typography();return Typography(
    displayLarge=t.displayLarge.copy(fontFamily=FontFamily.SansSerif),
    displayMedium=t.displayMedium.copy(fontFamily=FontFamily.SansSerif),
    displaySmall=t.displaySmall.copy(fontFamily=FontFamily.SansSerif),
    headlineLarge=t.headlineLarge.copy(fontFamily=FontFamily.SansSerif),
    headlineMedium=t.headlineMedium.copy(fontFamily=FontFamily.SansSerif),
    headlineSmall=t.headlineSmall.copy(fontFamily=FontFamily.SansSerif),
    titleLarge=t.titleLarge.copy(fontFamily=FontFamily.SansSerif),
    titleMedium=t.titleMedium.copy(fontFamily=FontFamily.SansSerif),
    titleSmall=t.titleSmall.copy(fontFamily=FontFamily.SansSerif),
    bodyLarge=t.bodyLarge.copy(fontFamily=FontFamily.SansSerif),
    bodyMedium=t.bodyMedium.copy(fontFamily=FontFamily.SansSerif),
    bodySmall=t.bodySmall.copy(fontFamily=FontFamily.SansSerif),
    labelLarge=t.labelLarge.copy(fontFamily=FontFamily.SansSerif),
    labelMedium=t.labelMedium.copy(fontFamily=FontFamily.SansSerif),
    labelSmall=t.labelSmall.copy(fontFamily=FontFamily.SansSerif)
)}
