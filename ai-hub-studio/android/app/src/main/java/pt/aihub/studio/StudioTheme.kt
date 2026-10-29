package pt.aihub.studio

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable fun StudioTheme(dark:Boolean,content:@Composable ()->Unit){
    val colors=if(dark)darkColorScheme(
        primary=Color(0xFFAAC5FF),onPrimary=Color(0xFF0A2E65),primaryContainer=Color(0xFF203A65),onPrimaryContainer=Color(0xFFD9E6FF),
        secondary=Color(0xFFCBB9FF),onSecondary=Color(0xFF35265C),secondaryContainer=Color(0xFF3D315B),onSecondaryContainer=Color(0xFFEADDFF),
        background=Color(0xFF0C111D),onBackground=Color(0xFFE7EBF5),surface=Color(0xFF121A29),onSurface=Color(0xFFE7EBF5),
        surfaceVariant=Color(0xFF222D41),onSurfaceVariant=Color(0xFFBCC6D9),outline=Color(0xFF8490A5),outlineVariant=Color(0xFF354057)
    )else lightColorScheme(
        primary=Color(0xFF285CA6),onPrimary=Color.White,primaryContainer=Color(0xFFDCE7FF),onPrimaryContainer=Color(0xFF102E5C),
        secondary=Color(0xFF695199),onSecondary=Color.White,secondaryContainer=Color(0xFFEBDCFF),onSecondaryContainer=Color(0xFF2E1C50),
        background=Color(0xFFF6F8FD),onBackground=Color(0xFF182235),surface=Color.White,onSurface=Color(0xFF182235),
        surfaceVariant=Color(0xFFE8EDF7),onSurfaceVariant=Color(0xFF46536A),outline=Color(0xFF6F7D94),outlineVariant=Color(0xFFCBD4E5)
    )
    MaterialTheme(colorScheme=colors,typography=studioTypography(),content=content)
}
