package pt.aihub.studio

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Repaints a local clock. Opening details never contacts a provider. */
@Composable fun MediaStatusLabel(vm:StudioModel,provider:String,model:String) {
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    var expanded by remember(provider,model){mutableStateOf(false)}
    LaunchedEffect(provider,model){while(true){now=System.currentTimeMillis();delay(1000)}}
    val value=vm.mediaStatus.get(provider,model)
    val summary=value.summary(now).lineSequence().take(2).joinToString("\n")
    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f)){
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(summary,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("media-availability-$provider"))
            TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp)){Text(if(expanded)"Ocultar limites e diagnóstico" else "Ver limites e diagnóstico",style=MaterialTheme.typography.labelSmall)}
            if(expanded){
                if(value.detail.isNotBlank())Text(value.detail,style=MaterialTheme.typography.bodySmall)
                Text("Fonte: ${value.source}. Gerações concluídas nesta instalação desde a v0.3.3: ${vm.mediaStatus.localSuccesses(provider)}. Esta contagem local não representa o consumo total da conta.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
