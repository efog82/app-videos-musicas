package pt.aihub.studio

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class GenerationRequest(val kind:String,val prompt:String,val origin:String,val reference:JSONObject?)

@Composable fun EmptyCard(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector) {OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(icon,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodyMedium)}}}
@Composable fun TaskCard(task:JSONObject,onCancel:()->Unit){
    val state=task.optString("state");val labels=mapOf("queued" to "Em espera","running" to "A gerar","failed" to "Não foi possível gerar","cancelled" to "Cancelado","interrupted" to "Interrompido")
    OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(labels[state]?:state,style=MaterialTheme.typography.titleSmall);Text(task.optString("prompt"),maxLines=2,overflow=TextOverflow.Ellipsis);Text(task.optString("message"),style=MaterialTheme.typography.bodySmall,color=if(state=="failed")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
        if(state=="running")LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state in listOf("queued","running"))TextButton(onClick=onCancel){Text("Cancelar pedido")}
    }}
}
@Composable fun AssetCard(asset:JSONObject,onOpen:()->Unit,onAnimate:()->Unit,onSave:()->Unit,onShare:()->Unit,onProject:@Composable()->Unit,onDelete:()->Unit) {
    val image=asset.optString("kindMedia")=="image"
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){
        Box(Modifier.fillMaxWidth().height(240.dp).clickable(onClick=onOpen),contentAlignment=Alignment.Center){
            if(image)AsyncImage(model=File(asset.getString("path")),contentDescription="Imagem gerada",modifier=Modifier.fillMaxSize().testTag("asset-image"))else VideoThumbnail(asset)
            if(!image)FilledIconButton(onClick=onOpen){Icon(Icons.Outlined.PlayArrow,"Reproduzir vídeo")}
        }
        Column(Modifier.padding(horizontal=14.dp,vertical=8.dp)){
            Text(asset.optString("prompt"),maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
            Text(mediaProviders.firstOrNull{it.id==asset.optString("provider")}?.title?:asset.optString("provider"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
            Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
                if(image)TextButton(onClick=onAnimate){Icon(Icons.Outlined.MotionPhotosOn,null,Modifier.size(17.dp));Text("Animar imagem",Modifier.padding(start=5.dp))}
                IconButton(onClick=onSave){Icon(Icons.Outlined.Download,"Guardar na galeria")}
                IconButton(onClick=onShare){Icon(Icons.Outlined.Share,"Partilhar ficheiro")}
                onProject()
                IconButton(onClick=onDelete){Icon(Icons.Outlined.DeleteOutline,"Eliminar ficheiro")}
            }
        }
    }
}
@Composable private fun VideoThumbnail(asset:JSONObject){
    val frame by produceState<android.graphics.Bitmap?>(null,asset.optString("path")){value=withContext(Dispatchers.IO){runCatching{val r=android.media.MediaMetadataRetriever();try{r.setDataSource(asset.getString("path"));r.getFrameAtTime(0)}finally{r.release()}}.getOrNull()}}
    frame?.let{Image(bitmap=it.asImageBitmap(),contentDescription="Pré-visualização do vídeo gerado",modifier=Modifier.fillMaxSize().testTag("asset-video-thumbnail"))}?:CircularProgressIndicator(Modifier.size(24.dp))
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MediaViewer(asset:JSONObject,onDismiss:()->Unit,onSave:()->Unit,onShare:()->Unit,onAnimate:()->Unit){
    var scale by remember{mutableFloatStateOf(1f)};var x by remember{mutableFloatStateOf(0f)};var y by remember{mutableFloatStateOf(0f)}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize(),color=Color.Black){Column{
            TopAppBar(title={Text(if(asset.optString("kindMedia")=="video")"Vídeo gerado" else "Imagem gerada",color=Color.White)},navigationIcon={IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"Fechar pré-visualização",tint=Color.White)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Black),actions={IconButton(onClick=onSave,modifier=Modifier.testTag("viewer-save")){Icon(Icons.Outlined.Download,"Guardar na galeria",tint=Color.White)};IconButton(onClick=onShare){Icon(Icons.Outlined.Share,"Partilhar ficheiro",tint=Color.White)}})
            if(asset.optString("kindMedia")=="video")MediaPlayer(asset.getString("path"),Modifier.weight(1f).fillMaxWidth())
            else Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit){detectTransformGestures{_,pan,zoom,_->scale=(scale*zoom).coerceIn(1f,8f);x=if(scale>1)x+pan.x else 0f;y=if(scale>1)y+pan.y else 0f}},contentAlignment=Alignment.Center){AsyncImage(model=File(asset.getString("path")),contentDescription="Imagem em ecrã completo com zoom",modifier=Modifier.fillMaxSize().graphicsLayer(scaleX=scale,scaleY=scale,translationX=x,translationY=y))}
            if(asset.optString("kindMedia")=="image")Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onClick={scale=1f;x=0f;y=0f}){Text("Repor zoom")};Button(onClick=onAnimate,modifier=Modifier.testTag("viewer-animate")){Icon(Icons.Outlined.MotionPhotosOn,null);Text("Animar imagem",Modifier.padding(start=8.dp))}}
        }}
    }
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun MediaPlayer(path:String,modifier:Modifier=Modifier){
    val context=LocalContext.current
    val player=remember(path){ExoPlayer.Builder(context).build().apply{setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))));prepare();playWhenReady=true}}
    var ready by remember(path){mutableStateOf(false)}
    DisposableEffect(player){val listener=object:androidx.media3.common.Player.Listener{override fun onRenderedFirstFrame(){ready=true}};player.addListener(listener);onDispose{player.removeListener(listener);player.release()}}
    AndroidView(factory={PlayerView(it).apply{this.player=player;useController=true}},update={it.player=player},modifier=modifier.testTag("integrated-video-player").semantics{stateDescription=if(ready)"Vídeo preparado" else "A preparar vídeo"})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GenerationSheet(vm:StudioModel,request:GenerationRequest,onDismiss:()->Unit,onSubmit:()->Unit) {
    val choices=mediaProviders.filter{it.kind==request.kind&&(request.reference==null||it.reference)}
    var provider by remember(request){mutableStateOf(choices.first())}
    var prompt by remember(request){mutableStateOf(request.prompt)}
    var aspect by remember{mutableStateOf("16:9")};var resolution by remember{mutableStateOf(if(request.kind=="video")"448" else "512")}
    var model by remember(provider){mutableStateOf(provider.models.first())}
    var count by remember{mutableStateOf("1")};var duration by remember{mutableStateOf("2")};var seed by remember{mutableStateOf(kotlin.random.Random.nextInt(1,Int.MAX_VALUE-10).toString())}
    var experimental by remember(provider){mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest=onDismiss){
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=700.dp).testTag("generation-form"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{Text(if(request.kind=="image")"Gerar imagem" else if(request.reference==null)"Gerar vídeo" else "Animar imagem",style=MaterialTheme.typography.headlineSmall)}
            request.reference?.let{asset->item{AsyncImage(model=File(asset.getString("path")),contentDescription="Imagem de referência",modifier=Modifier.fillMaxWidth().height(130.dp))}}
            item{SelectField("Fornecedor",provider.id,choices.map{it.id to it.title}){id->choices.firstOrNull{it.id==id}?.let{provider=it}};Text(provider.note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)}
            item{SelectField("Modelo",model,provider.models.map{it to it}){model=it};Text(if(provider.tested||vm.vault.get("media_test_${provider.id}").isNotBlank())"Geração real previamente confirmada; disponibilidade atual sujeita a quota" else "Experimental — ainda não testado com geração real nesta conta",style=MaterialTheme.typography.labelSmall)}
            item{OutlinedTextField(prompt,{prompt=it},label={Text("Descrição da geração")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8)}
            item{SelectField("Proporção",aspect,listOf("1:1","4:5","9:16","16:9").map{it to it}){aspect=it};SelectField("Lado maior / resolução solicitada",resolution,(if(request.kind=="video")listOf("448","704","1024") else listOf("512","768","1024")).map{it to "$it px"}){resolution=it}}
            item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(seed,{seed=it},label={Text("Seed")},modifier=Modifier.weight(1f));if(request.kind=="image")OutlinedTextField(count,{count=it},label={Text("N.º imagens (1–10)")},modifier=Modifier.weight(1f))else OutlinedTextField(duration,{duration=it},label={Text("Duração (0,3–8,5 s)")},modifier=Modifier.weight(1f))}}
            if(!provider.tested&&vm.vault.get("media_test_${provider.id}").isBlank())item{LabeledSwitch("Autorizar teste desta integração experimental",experimental){experimental=it}}
            item{LabeledSwitch("Autorizar envio ao fornecedor escolhido",vm.consent){vm.consent=it;vm.vault.set("remote_consent",it.toString())}}
            item{Button(enabled=prompt.isNotBlank()&&vm.consent&&(provider.tested||experimental||vm.vault.get("media_test_${provider.id}").isNotBlank()),onClick={runCatching{
                val size=resolution.toInt();val ratio=when(aspect){"1:1"->1.0;"4:5"->.8;"9:16"->9.0/16;else->16.0/9}
                var width=if(ratio>=1)size else (size*ratio).toInt();var height=if(ratio>=1)(size/ratio).toInt() else size
                if(request.kind=="video"){width=(width/32*32).coerceAtLeast(256);height=(height/32*32).coerceAtLeast(256)}
                require(seed.toLong() in 0..Int.MAX_VALUE.toLong()-10){"Seed inválido"};val seconds=duration.toDouble();require(seconds in .3..8.5){"Duração fora da capacidade do modelo"}
                vm.generate(provider,prompt,width,height,seed.toLong(),seconds,model,request.reference,if(request.kind=="image")count.toInt() else 1,request.origin);onSubmit()
            }.onFailure{vm.status=connectionMessage(it)}},modifier=Modifier.fillMaxWidth().testTag("submit-generation")){Icon(Icons.Outlined.AutoAwesome,null);Text(if(request.kind=="image")"Criar imagem" else "Criar vídeo",Modifier.padding(start=8.dp))};Text("Não ativa subscrições, compras de créditos ou faturação. As filas e quotas são do fornecedor. Em LTX as dimensões são ajustadas a múltiplos de 32; a proporção pode aproximar-se.",style=MaterialTheme.typography.labelSmall)}
            item{Spacer(Modifier.height(30.dp))}
        }
    }
}
@Composable fun SelectField(label:String,value:String,options:List<Pair<String,String>>,onChange:(String)->Unit){var open by remember{mutableStateOf(false)};Column{Text(label,style=MaterialTheme.typography.labelMedium);Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text(options.firstOrNull{it.first==value}?.second?:"Selecionar",Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis);Icon(Icons.Outlined.ExpandMore,null)};DropdownMenu(expanded=open,onDismissRequest={open=false},modifier=Modifier.heightIn(max=360.dp)){options.forEach{(id,title)->DropdownMenuItem(text={Text(title)},onClick={onChange(id);open=false})}}}}}
@Composable fun LabeledSwitch(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().clickable{onChange(!value)},verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Switch(value,onCheckedChange=onChange)}}
@Composable fun AssetProjectMenu(vm:StudioModel,asset:JSONObject){var open by remember{mutableStateOf(false)};Box{IconButton(onClick={open=true}){Icon(Icons.Outlined.FolderOpen,"Associar a projeto")};DropdownMenu(expanded=open,onDismissRequest={open=false}){DropdownMenuItem(text={Text("Sem projeto")},onClick={vm.assignAsset(asset,"");open=false});vm.projects.forEach{p->DropdownMenuItem(text={Text(p.getString("name"))},onClick={vm.assignAsset(asset,p.getString("id"));open=false})}}}}
@Composable fun ProjectsScreen(vm:StudioModel,onAsset:(JSONObject)->Unit){
    var name by remember{mutableStateOf("")};var context by remember{mutableStateOf("")};var selected by remember{mutableStateOf("")}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.testTag("projects-screen")){
        item{Text("Os teus projetos criativos",style=MaterialTheme.typography.headlineSmall);Text("Contexto, conversas e resultados no mesmo lugar.",style=MaterialTheme.typography.bodyMedium)}
        item{OutlinedTextField(name,{name=it},label={Text("Nome do projeto")},modifier=Modifier.fillMaxWidth());OutlinedTextField(context,{context=it},label={Text("Contexto e personagens")},modifier=Modifier.fillMaxWidth(),minLines=2);Button(enabled=name.isNotBlank(),onClick={vm.saveProject(name,context);name="";context=""}){Text("Criar projeto")}}
        items(vm.projects,key={it.getString("id")}){project->Card(Modifier.fillMaxWidth().clickable{selected=project.getString("id")}){Column(Modifier.padding(16.dp)){Text(project.getString("name"),style=MaterialTheme.typography.titleMedium);Text(project.optString("context"),maxLines=3,overflow=TextOverflow.Ellipsis);Row{TextButton(onClick={vm.currentProject=project.getString("id");vm.tab="Chat";vm.status="Contexto do projeto: ${project.getString("name")}"}){Text("Usar no Chat")};TextButton(onClick={selected=project.getString("id")}){Text("Ver criações")}}}}}
        if(selected.isNotBlank()){
            item{Text("Criações do projeto",style=MaterialTheme.typography.titleLarge)}
            items(vm.assets.filter{it.optString("project")==selected}){asset->Card(Modifier.fillMaxWidth().clickable{onAsset(asset)}){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){if(asset.optString("kindMedia")=="image")AsyncImage(File(asset.getString("path")),"Imagem do projeto",Modifier.size(80.dp))else Icon(Icons.Outlined.Movie,null,Modifier.size(48.dp));Text(asset.optString("prompt"),Modifier.weight(1f).padding(start=12.dp),maxLines=3,overflow=TextOverflow.Ellipsis)}}}
            items(vm.conversations.filter{it.optString("project")==selected}){c->ListItem(headlineContent={Text(c.optString("title"))},leadingContent={Icon(Icons.Outlined.ChatBubbleOutline,null)},modifier=Modifier.clickable{vm.openConversation(c)})}
        }
    }
}
