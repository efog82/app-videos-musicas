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
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

data class GenerationRequest(val kind:String,val prompt:String,val origin:String,val reference:JSONObject?,val providerId:String="",val seconds:Double?=null)

@Composable fun EmptyCard(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector) {OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(icon,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodyMedium)}}}
@Composable fun TaskCard(task:JSONObject,onCancel:()->Unit,onRetry:(()->Unit)?=null,onDelete:(()->Unit)?=null){
    val state=task.optString("state");val active=state in listOf("queued","running")
    var confirm by remember(task.optString("id")){mutableStateOf(false)}
    var details by remember(task.optString("id")){mutableStateOf(false)}
    val labels=mapOf("queued" to "Em espera","running" to "A gerar","failed" to "Não foi possível gerar","cancelled" to "Cancelado","interrupted" to "Interrompido","done" to "Concluído")
    val tint=if(state=="failed")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    if(confirm)AlertDialog(onDismissRequest={confirm=false},icon={Icon(Icons.Outlined.DeleteOutline,null)},title={Text("Eliminar relatório do pedido?")},text={Text("Remove apenas este registo da lista de pedidos. As imagens, vídeos, conversas e projetos serão mantidos.")},confirmButton={TextButton(onClick={confirm=false;onDelete?.invoke()},modifier=Modifier.testTag("confirm-delete-task")){Text("Eliminar relatório")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}})
    OutlinedCard(Modifier.fillMaxWidth().testTag("task-${task.optString("id")}"),shape=RoundedCornerShape(20.dp)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Icon(if(state=="failed")Icons.Outlined.ErrorOutline else if(active)Icons.Outlined.Schedule else Icons.Outlined.CheckCircleOutline,null,tint=tint,modifier=Modifier.size(21.dp))
                Text(labels[state]?:state,Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleSmall,color=tint)
                if(!active&&onDelete!=null)IconButton(onClick={confirm=true},modifier=Modifier.testTag("delete-task-${task.optString("id")}")){Icon(Icons.Outlined.DeleteOutline,"Eliminar relatório do pedido")}
            }
            Text((if(task.optString("kind")=="video")"Vídeo" else "Imagem")+" · "+(mediaProviders.firstOrNull{it.id==task.optString("provider")}?.title?:task.optString("provider")),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
            Text(task.optString("prompt"),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
            Text(task.optString("message"),maxLines=if(details)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(task.optString("message").length>120)TextButton(onClick={details=!details}){Text(if(details)"Ocultar detalhes" else "Ver diagnóstico completo")}
            if(state=="running")LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state in listOf("failed","interrupted","cancelled")&&onRetry!=null)OutlinedButton(onClick=onRetry){Icon(Icons.Outlined.Refresh,null,Modifier.size(18.dp));Text("Novo pedido / outro fornecedor",Modifier.padding(start=6.dp))}
            if(active)TextButton(onClick=onCancel){Text("Cancelar pedido")}
        }
    }
}
@Composable fun AssetDeletionConfirmation(asset:JSONObject,onConfirm:()->Unit,onDismiss:()->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(asset.optString("kindMedia")=="image")"Eliminar imagem?" else "Eliminar vídeo?")},text={Text("A cópia privada e as referências no histórico da app serão removidas. Os ficheiros da galeria Android serão mantidos.")},confirmButton={TextButton(onClick=onConfirm,modifier=Modifier.testTag("confirm-delete-asset")){Text("Eliminar")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}

@Composable fun AssetCard(asset:JSONObject,onOpen:()->Unit,onAnimate:()->Unit,onVariation:()->Unit,onSave:()->Unit,onShare:()->Unit,onProject: @Composable () -> Unit,onDelete:()->Unit) {
    val image=asset.optString("kindMedia")=="image"
    var diagnostic by remember(asset.optString("id")){mutableStateOf(false)}
    if(diagnostic)CloudflareResultDialog(asset,onDismiss={diagnostic=false})
    var confirmDelete by remember(asset.optString("id")){mutableStateOf(false)}
    if(confirmDelete)AssetDeletionConfirmation(asset,onConfirm={confirmDelete=false;onDelete()},onDismiss={confirmDelete=false})
    var imageReady by remember(asset.optString("path")){mutableStateOf(false)}
    Card(Modifier.fillMaxWidth().testTag("asset-card-${asset.optString("id")}"),shape=RoundedCornerShape(22.dp)){
        Box(Modifier.fillMaxWidth().height(240.dp).clickable(onClick=onOpen).testTag(if(image)"asset-image" else "asset-video").semantics{if(image)stateDescription=if(imageReady)"Imagem preparada" else "A carregar imagem"},contentAlignment=Alignment.Center){
            if(image)AsyncImage(model=File(asset.getString("path")),contentDescription=if(asset.optString("source")=="gallery")"Imagem selecionada da galeria" else "Imagem gerada",modifier=Modifier.fillMaxSize(),onSuccess={imageReady=true})else VideoThumbnail(asset)
            if(image)FilledTonalIconButton(onClick={confirmDelete=true},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("delete-asset-${asset.optString("id")}")){Icon(Icons.Outlined.DeleteOutline,"Eliminar imagem")}
            if(!image)FilledIconButton(onClick=onOpen){Icon(Icons.Outlined.PlayArrow,"Reproduzir vídeo")}
        }
        Column(Modifier.padding(horizontal=14.dp,vertical=8.dp)){
            Text(asset.optString("prompt"),maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
            if(!image&&asset.optLong("durationMs")>0)Text("${asset.optInt("width")}×${asset.optInt("height")} · ${String.format(java.util.Locale.forLanguageTag("pt-PT"),"%.2f",asset.optLong("durationMs")/1000.0)} s · MP4",style=MaterialTheme.typography.labelSmall)
            Text(mediaProviders.firstOrNull{it.id==asset.optString("provider")}?.title?:asset.optString("provider"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
            Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
                if(image)TextButton(onClick=onVariation){Icon(Icons.Outlined.Refresh,null,Modifier.size(17.dp));Text("Variar descrição",Modifier.padding(start=5.dp))}
                if(image)TextButton(onClick=onAnimate){Icon(Icons.Outlined.MotionPhotosOn,null,Modifier.size(17.dp));Text("Animar imagem",Modifier.padding(start=5.dp))}
                IconButton(onClick=onSave){Icon(Icons.Outlined.Download,"Guardar na galeria")}
                IconButton(onClick=onShare){Icon(Icons.Outlined.Share,"Partilhar ficheiro")}
                if(image&&asset.optString("provider")=="cloudflare")IconButton(onClick={diagnostic=true}){Icon(Icons.Outlined.Info,"Diagnóstico desta imagem")}
                onProject()
                IconButton(onClick={confirmDelete=true},modifier=Modifier.testTag("delete-asset-row-${asset.optString("id")}")){Icon(Icons.Outlined.DeleteOutline,if(image)"Eliminar imagem" else "Eliminar ficheiro")}
            }
        }
    }
}
@Composable private fun VideoThumbnail(asset:JSONObject){
    var frame by remember(asset.optString("path")){mutableStateOf<android.graphics.Bitmap?>(null)}
    LaunchedEffect(asset.optString("path")){frame=withContext(Dispatchers.IO){runCatching{val r=android.media.MediaMetadataRetriever();try{r.setDataSource(asset.getString("path"));r.getFrameAtTime(0)}finally{r.release()}}.getOrNull()}}
    frame?.let{Image(bitmap=it.asImageBitmap(),contentDescription="Pré-visualização do vídeo gerado",modifier=Modifier.fillMaxSize().testTag("asset-video-thumbnail"))}?:CircularProgressIndicator(Modifier.size(24.dp))
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MediaViewer(asset:JSONObject,onDismiss:()->Unit,onSave:()->Unit,onShare:()->Unit,onAnimate:()->Unit,onVariation:()->Unit,onDelete:(()->Unit)?=null){
    var diagnostic by remember(asset.optString("id")){mutableStateOf(false)}
    if(diagnostic)CloudflareResultDialog(asset,onDismiss={diagnostic=false})
    var confirmDelete by remember(asset.optString("id")){mutableStateOf(false)}
    var scale by remember{mutableFloatStateOf(1f)};var x by remember{mutableFloatStateOf(0f)};var y by remember{mutableFloatStateOf(0f)}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize(),color=Color.Black){Column{
            TopAppBar(title={Text(if(asset.optString("kindMedia")=="video")"Vídeo gerado" else if(asset.optString("source")=="gallery")"Imagem selecionada" else "Imagem gerada",color=Color.White)},navigationIcon={IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"Fechar pré-visualização",tint=Color.White)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Black),actions={if(asset.optString("provider")=="cloudflare")IconButton(onClick={diagnostic=true}){Icon(Icons.Outlined.Info,"Diagnóstico desta imagem",tint=Color.White)};IconButton(onClick=onSave,modifier=Modifier.testTag("viewer-save")){Icon(Icons.Outlined.Download,"Guardar na galeria",tint=Color.White)};IconButton(onClick=onShare){Icon(Icons.Outlined.Share,"Partilhar ficheiro",tint=Color.White)};if(asset.optString("kindMedia")=="image"&&onDelete!=null)IconButton(onClick={confirmDelete=true},modifier=Modifier.testTag("viewer-delete")){Icon(Icons.Outlined.DeleteOutline,"Eliminar imagem",tint=Color.White)}})
            if(asset.optString("kindMedia")=="video")MediaPlayer(asset.getString("path"),Modifier.weight(1f).fillMaxWidth())
            else Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit){detectTransformGestures{_,pan,zoom,_->scale=(scale*zoom).coerceIn(1f,8f);x=if(scale>1)x+pan.x else 0f;y=if(scale>1)y+pan.y else 0f}},contentAlignment=Alignment.Center){AsyncImage(model=File(asset.getString("path")),contentDescription="Imagem em ecrã completo com zoom",modifier=Modifier.fillMaxSize().graphicsLayer(scaleX=scale,scaleY=scale,translationX=x,translationY=y))}
            if(asset.optString("kindMedia")=="image")Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onClick=onVariation){Text("Nova variação")};TextButton(onClick={scale=1f;x=0f;y=0f}){Text("Repor zoom")};Button(onClick=onAnimate,modifier=Modifier.testTag("viewer-animate")){Icon(Icons.Outlined.MotionPhotosOn,null);Text("Animar imagem",Modifier.padding(start=8.dp))}}
        }}
    }
    if(confirmDelete)AssetDeletionConfirmation(asset,onConfirm={confirmDelete=false;onDelete?.invoke()},onDismiss={confirmDelete=false})
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
    var reference by remember(request){mutableStateOf(request.reference)}
    val choices=mediaProviders.filter{it.kind==request.kind&&(reference==null||it.reference)&&(request.kind!="video"||reference!=null||it.video.textToVideo)}
    var provider by remember(request,reference?.optString("id")){mutableStateOf(choices.firstOrNull{it.id==request.providerId}?:choices.first())}
    var prompt by remember(request){mutableStateOf(request.prompt)}
    var aspect by remember{mutableStateOf("16:9")};var resolution by remember{mutableStateOf(if(request.kind=="video")"512" else "512")}
    var model by remember(provider){mutableStateOf(provider.models.first())}
    var count by remember{mutableStateOf("1")};var duration by remember(provider){mutableStateOf((request.seconds?.takeIf{provider.id==request.providerId&&it in provider.video.minSeconds..provider.video.maxSeconds}?:provider.video.defaultSeconds).toString())};var seed by remember{mutableStateOf(kotlin.random.Random.nextInt(1,Int.MAX_VALUE-10).toString())}
    var experimental by remember(provider){mutableStateOf(false)}
    var cloudflareSteps by remember{mutableStateOf("8")}
    val scope=rememberCoroutineScope()
    var preparing by remember{mutableStateOf(false)}
    var formError by remember{mutableStateOf("")}
    val visualChatConfigured=vm.selection.isNotBlank()||vm.ai.automaticModels().isNotEmpty()
    var candidate by remember{mutableStateOf<String?>(null)}
    var beforePreparation by remember{mutableStateOf<String?>(null)}
    candidate?.let{suggestion->AlertDialog(onDismissRequest={candidate=null},title={Text("Rever descrição visual")},text={Column{Text("Foi preparada pela IA de chat configurada. Só será usada se confirmares; não foi gerada nenhuma imagem ou vídeo.",style=MaterialTheme.typography.bodySmall);OutlinedTextField(suggestion,{candidate=it},modifier=Modifier.fillMaxWidth().heightIn(max=260.dp),label={Text("Descrição proposta")},minLines=4)}},confirmButton={TextButton(onClick={beforePreparation=prompt;prompt=suggestion;candidate=null}){Text("Usar descrição")}},dismissButton={TextButton(onClick={candidate=null}){Text("Manter original")}})}
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=sheetState){
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=700.dp).imePadding().testTag("generation-form"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{Text(if(request.kind=="image")"Gerar imagem" else if(reference==null)"Gerar vídeo" else "Animar imagem",style=MaterialTheme.typography.headlineSmall)}
            if(request.kind=="video")item{SelectField("Imagem de referência",reference?.optString("id").orEmpty(),listOf("" to "Sem imagem · texto para vídeo")+vm.assets.filter{it.optString("kindMedia")=="image"}.map{it.getString("id") to it.optString("prompt").take(75)}){id->reference=vm.assets.firstOrNull{it.optString("id")==id}}}
            reference?.let{asset->item{AsyncImage(model=File(asset.getString("path")),contentDescription="Imagem de referência",modifier=Modifier.fillMaxWidth().height(130.dp))}}
            item{Text("1. Escolhe a inteligência artificial",style=MaterialTheme.typography.titleMedium)}
            item{SelectField("Fornecedor",provider.id,choices.map{it.id to it.title}){id->choices.firstOrNull{it.id==id}?.let{provider=it}};Text(provider.note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)}
            item{SelectField("Modelo",model,provider.models.map{it to it}){model=it};Text(if(provider.tested||vm.vault.get("media_test_${provider.id}").isNotBlank())"Geração real previamente confirmada; disponibilidade atual sujeita a quota" else "Experimental — ainda não testado com geração real nesta conta",style=MaterialTheme.typography.labelSmall)}
            item{MediaStatusLabel(vm,provider.id,model)}
            if(provider.id=="cloudflare"&&model==pt.aihub.core.CloudflareImageRequest.FLUX)item{SelectField("Passos FLUX",cloudflareSteps,listOf("4" to "4 · menor consumo","8" to "8 · mais passos, sem garantia de qualidade")){cloudflareSteps=it};Text("Seed não enviado: não consta da tabela de parâmetros deste endpoint. O pedido e a integridade dos bytes ficam no diagnóstico privado.",style=MaterialTheme.typography.labelSmall)}
            item{Text("2. Descreve a tua ideia",style=MaterialTheme.typography.titleMedium)}
            item{OutlinedTextField(prompt,{prompt=it},label={Text("Descrição da geração")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8)}
            item{OutlinedButton(enabled=prompt.isNotBlank()&&!preparing&&vm.consent&&visualChatConfigured,onClick={formError="";preparing=true;scope.launch{try{candidate=vm.prepareVisualDescription(prompt,request.kind)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){formError=connectionMessage(e);vm.status=formError}finally{preparing=false}}},modifier=Modifier.fillMaxWidth().testTag("prepare-visual-description")){Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(18.dp));Text(if(preparing)"A preparar descrição…" else "Preparar descrição visual com IA",Modifier.padding(start=8.dp))};Text(if(visualChatConfigured)"Opcional: envia o texto à IA de chat configurada e apresenta uma proposta para rever. Não altera a conversa." else "Para usar esta ajuda, configure primeiro uma IA na área Chat. Pode continuar a escrever a descrição manualmente.",style=MaterialTheme.typography.labelSmall);if(prompt.length>600)Text("Este texto é longo. Escolhe uma cena ou prepara uma descrição visual curta; enviar a resposta completa pode perder detalhes importantes.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall);beforePreparation?.let{original->TextButton(onClick={prompt=original;beforePreparation=null}){Text("Repor texto original")}}}
            item{Text("3. Ajusta o resultado",style=MaterialTheme.typography.titleMedium)}
            if(provider.video.referenceDeterminesSize&&request.kind=="video")item{Text("Formato e resolução seguem a imagem de referência. O fornecedor ajusta as dimensões para 480–832 px, podendo recortar. Use uma imagem vertical para vídeo vertical.",style=MaterialTheme.typography.bodySmall)}
            if(!(provider.id=="cloudflare"&&model.contains("flux"))&&!(request.kind=="video"&&provider.video.referenceDeterminesSize))item{SelectField("Proporção",aspect,listOf("1:1","4:5","9:16","16:9").map{it to it}){aspect=it};if(provider.id=="cloudflare"&&model==pt.aihub.core.CloudflareImageRequest.SDXL){val size=pt.aihub.core.SdxlQuality.forAspect(aspect);Text("Resolução de geração SDXL: ${size.first}×${size.second} px · formato aproximado ao modelo. 20 passos · guidance 7,5. Mais píxeis podem consumir mais quota.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("sdxl-generation-size"))}else SelectField("Lado maior / resolução solicitada",resolution,listOf("512","768","1024").map{it to "$it px"}){resolution=it}}
            if(provider.id=="hf-zimage")item{Text("Z-Image: dimensões ajustadas a múltiplos de 64, com mínimo 512 e máximo 1024 por lado. A proporção pode aproximar-se; 9 passos fixos, guidance 0 no fornecedor.",style=MaterialTheme.typography.bodySmall)}
            if(provider.id=="cloudflare"&&model.contains("flux"))item{Text("Este endpoint não publica controlos de resolução/proporção. É usada a saída padrão do modelo.",style=MaterialTheme.typography.bodySmall)}
            item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){if(!(provider.id=="cloudflare"&&model==pt.aihub.core.CloudflareImageRequest.FLUX))OutlinedTextField(seed,{seed=it},label={Text("Seed")},modifier=Modifier.weight(1f));if(request.kind=="image")OutlinedTextField(count,{count=it},label={Text("N.º imagens (1–10)")},modifier=Modifier.weight(1f))else OutlinedTextField(duration,{duration=it},label={Text(if(provider.id=="hf-wan22-i2v")"Duração (0,5–5 s)" else "Duração (0,3–8,5 s)")},modifier=Modifier.weight(1f))}}
            if(!provider.tested&&vm.vault.get("media_test_${provider.id}").isBlank())item{LabeledSwitch("Autorizar teste desta integração experimental",experimental){experimental=it}}
            item{LabeledSwitch("Autorizar envio ao fornecedor escolhido",vm.consent){vm.consent=it;vm.vault.set("remote_consent",it.toString())}}
            if(formError.isNotBlank())item{Text(formError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("generation-form-error"))}
            item{Button(enabled=prompt.isNotBlank()&&vm.consent&&(provider.tested||experimental||vm.vault.get("media_test_${provider.id}").isNotBlank()),onClick={formError="";runCatching{
                val size=resolution.toInt();val (a,b)=when(aspect){"1:1"->1 to 1;"4:5"->4 to 5;"9:16"->9 to 16;else->16 to 9}
                val multiple=if(provider.id=="hf-zimage")64 else if(request.kind=="video")32 else if(provider.id=="pollinations-image")1 else 8
                val factor=(size/maxOf(a,b)/multiple*multiple).coerceAtLeast(multiple)
                val baseWidth=if(provider.id=="hf-zimage")(kotlin.math.round(size.toDouble()*a/maxOf(a,b)/64)*64).toInt().coerceAtLeast(64) else a*factor;val baseHeight=if(provider.id=="hf-zimage")(kotlin.math.round(size.toDouble()*b/maxOf(a,b)/64)*64).toInt().coerceAtLeast(64) else b*factor
                val scale=if(provider.id=="hf-zimage")maxOf(1.0,512.0/minOf(baseWidth,baseHeight)) else 1.0
                val computedWidth=if(provider.id=="hf-zimage")(kotlin.math.round(baseWidth*scale/64)*64).toInt().coerceIn(512,1024) else baseWidth
                val computedHeight=if(provider.id=="hf-zimage")(kotlin.math.round(baseHeight*scale/64)*64).toInt().coerceIn(512,1024) else baseHeight
                val sdxl=if(provider.id=="cloudflare"&&model==pt.aihub.core.CloudflareImageRequest.SDXL)pt.aihub.core.SdxlQuality.forAspect(aspect) else null
                val width=sdxl?.first?:computedWidth;val height=sdxl?.second?:computedHeight
                require(seed.toLong() in 0..Int.MAX_VALUE.toLong()-10){"Seed inválido"};val seconds=duration.toDouble();if(request.kind=="video")provider.video.validate(seconds,reference!=null)
                vm.generate(provider,prompt,width,height,seed.toLong(),seconds,model,reference,if(request.kind=="image")count.toInt() else 1,request.origin.ifBlank{reference?.optString("conversation").orEmpty()},cloudflareSteps.toInt());onSubmit()
            }.onFailure{formError=connectionMessage(it);vm.status=formError}},modifier=Modifier.fillMaxWidth().testTag("submit-generation")){Icon(Icons.Outlined.AutoAwesome,null);Text(if(request.kind=="image")"Criar imagem" else "Criar vídeo",Modifier.padding(start=8.dp))};Text("Não ativa subscrições, compras de créditos ou faturação. As filas e quotas são do fornecedor. Em LTX as dimensões são ajustadas a múltiplos de 32; a proporção pode aproximar-se.",style=MaterialTheme.typography.labelSmall)}
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

fun requestFromTask(task:JSONObject,assets:List<JSONObject>)=GenerationRequest(task.optString("kind"),task.optString("prompt"),task.optString("conversation"),assets.firstOrNull{it.optString("id")==task.optString("reference")},task.optString("provider"),task.optDouble("durationRequested").takeIf{it.isFinite()})
