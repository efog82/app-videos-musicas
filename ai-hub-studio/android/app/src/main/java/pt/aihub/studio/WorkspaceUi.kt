package pt.aihub.studio

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable fun CreativeHeader(kind:String,results:Int,onCreate:()->Unit){
    val image=kind=="image"
    val colors=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(26.dp),color=colors.surface){
        Column(Modifier.background(Brush.linearGradient(listOf(colors.primaryContainer.copy(alpha=.65f),colors.secondaryContainer.copy(alpha=.4f)))).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Surface(shape=RoundedCornerShape(14.dp),color=colors.surface.copy(alpha=.8f)){Icon(if(image)Icons.Outlined.AutoAwesome else Icons.Outlined.MovieFilter,null,Modifier.padding(12.dp).size(25.dp),tint=colors.primary)}
                Column(Modifier.weight(1f).padding(start=12.dp)){Text(if(image)"Estúdio de imagens" else "Estúdio de vídeo",style=MaterialTheme.typography.titleLarge);Text("$results ${if(results==1)"criação" else "criações"} · pré-visualização na app",style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)}
            }
            if(results==0){Text(if(image)"Transforma ideias em imagens." else "Dá vida às tuas imagens.",style=MaterialTheme.typography.headlineSmall)
            Text(if(image)"Descreve o que imaginas, escolhe uma IA e vê o resultado antes de guardar." else "Cria a partir de texto ou anima uma imagem. Duração e qualidade dependem do modelo.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)}
            Button(onClick=onCreate,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(vertical=14.dp),shape=RoundedCornerShape(16.dp)){
                Icon(Icons.Outlined.Add,if(image)"Gerar imagem" else "Gerar vídeo",Modifier.size(21.dp));Text(if(image)"Criar imagem" else "Criar vídeo",Modifier.padding(start=9.dp),style=MaterialTheme.typography.labelLarge)
            }
        }
    }
}
@Composable fun RequestOverview(tasks:List<JSONObject>,onOpen:()->Unit){
    val active=tasks.count{it.optString("state") in listOf("queued","running")}
    val failed=tasks.count{it.optString("state")=="failed"}
    OutlinedCard(onClick=onOpen,modifier=Modifier.fillMaxWidth().testTag("open-request-history"),shape=RoundedCornerShape(18.dp)){
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(if(active>0)Icons.Outlined.Schedule else Icons.Outlined.ReceiptLong,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text("Pedidos e diagnósticos",style=MaterialTheme.typography.titleSmall);Text(if(tasks.isEmpty())"Acompanha aqui os teus pedidos" else "$active em curso · $failed ${if(failed==1)"falhado" else "falhados"} · ${tasks.size} no histórico",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Icon(Icons.Outlined.ChevronRight,"Abrir histórico de pedidos")
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TaskHistorySheet(vm:StudioModel,kind:String?,onDismiss:()->Unit,onRetry:(GenerationRequest)->Unit){
    var filter by remember{mutableStateOf("Todos")}
    val all=vm.tasks.filter{kind==null||it.optString("kind")==kind}
    val shown=all.filter{when(filter){"Em curso"->it.optString("state") in listOf("queued","running");"Falhados"->it.optString("state")=="failed";"Concluídos"->it.optString("state")=="done";else->true}}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).testTag("task-history-sheet")){
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Pedidos e diagnósticos",style=MaterialTheme.typography.headlineSmall);Text("Eliminar um relatório mantém as tuas criações.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"Fechar pedidos")}}
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Todos","Em curso","Falhados","Concluídos").forEach{label->FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)})}}
            LazyColumn(Modifier.weight(1f).testTag("task-history-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(shown.isEmpty())item{EmptyCard("Sem pedidos nesta lista","Os novos pedidos vão aparecer aqui.",Icons.Outlined.TaskAlt)}
                items(shown,key={it.getString("id")}){task->TaskCard(task,onCancel={vm.cancelTask(task.getString("id"))},onRetry={onRetry(requestFromTask(task,vm.assets))},onDelete={vm.deleteTask(task.getString("id"))})}
                item{Spacer(Modifier.height(20.dp))}
            }
        }
    }
}
