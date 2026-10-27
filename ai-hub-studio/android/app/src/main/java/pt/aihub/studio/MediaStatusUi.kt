package pt.aihub.studio

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay

/** Repaints a local clock. This composable never contacts a provider. */
@Composable fun MediaStatusLabel(vm:StudioModel,provider:String,model:String) {
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(provider,model){while(true){now=System.currentTimeMillis();delay(1000)}}
    val value=vm.mediaStatus.get(provider,model)
    Text(value.summary(now),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("media-availability-$provider"))
    Text("Fonte: ${value.source}. Gerações concluídas nesta instalação desde esta atualização: ${vm.mediaStatus.localSuccesses(provider)} (não é o consumo total da conta).",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
}
