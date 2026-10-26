package pt.aihub.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

class MainActivity:ComponentActivity() {
    val studio:StudioModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{
        val colors=if(studio.dark)darkColorScheme(primary=Color(0xFF75DBCA),secondary=Color(0xFFBBA9F5),background=Color(0xFF0E1319),surface=Color(0xFF171F29),surfaceVariant=Color(0xFF232E3B)) else lightColorScheme(primary=Color(0xFF006B61),secondary=Color(0xFF6651A1),background=Color(0xFFF8FAFC),surface=Color.White,surfaceVariant=Color(0xFFE9EEF5))
        MaterialTheme(colorScheme=colors,typography=studioTypography()){StudioApp(studio)}
    }}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StudioApp(vm:StudioModel) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val drawer=rememberDrawerState(DrawerValue.Closed)
    var draft by remember {mutableStateOf("")};var attachments by remember {mutableStateOf(emptyList<JSONObject>())}
    var historyQuery by remember {mutableStateOf("")};var expanded by remember {mutableStateOf<JSONObject?>(null)}
    var generation by remember {mutableStateOf<GenerationRequest?>(null)}
    var pendingExport by remember {mutableStateOf<JSONObject?>(null)}
    val saveFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null)pendingExport?.let{asset->scope.launch{try{context.contentResolver.openOutputStream(uri)!!.use{out->File(asset.getString("path")).inputStream().use{it.copyTo(out)}};vm.status="Ficheiro exportado"}catch(e:Exception){vm.status=connectionMessage(e)}}}}
    fun save(asset:JSONObject){if(Build.VERSION.SDK_INT>=29)scope.launch{try{vm.export(asset);vm.refresh();vm.status="Guardado na galeria Android"}catch(e:Exception){vm.status=connectionMessage(e)}}else{pendingExport=asset;saveFile.launch("AIHub.${File(asset.getString("path")).extension}")}}
    val attach=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){
        runCatching{
            val mime=context.contentResolver.getType(uri)?:"text/plain"
            val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else null}?:"anexo"
            val dir=File(context.filesDir,"attachments").apply{mkdirs()};val f=File(dir,UUID.randomUUID().toString())
            context.contentResolver.openInputStream(uri)!!.use{input->f.outputStream().use{out->val buffer=ByteArray(65536);var size=0;while(true){val n=input.read(buffer);if(n<0)break;size+=n;require(size<=10*1024*1024){"Anexo superior a 10 MiB"};out.write(buffer,0,n)}}}
            attachments=attachments+JSONObject().put("path",f.path).put("mime",mime).put("name",name)
        }.onFailure{vm.status=connectionMessage(it)}
    }}
    val galleryPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->
        if(uri!=null)scope.launch{vm.importGalleryImage(uri)?.let{expanded=it}}
    }
    fun requestImage(prompt:String,origin:String=""){generation=GenerationRequest("image",prompt,origin,null)}
    fun animate(asset:JSONObject){generation=GenerationRequest("video",if(asset.optString("source")=="gallery")"" else asset.optString("prompt"),asset.optString("conversation"),asset)}
    if(vm.settings)ProviderSettings(vm,onDismiss={vm.settings=false})
    generation?.let{request->GenerationSheet(vm,request,onDismiss={generation=null},onSubmit={generation=null})}
    expanded?.let{asset->MediaViewer(asset,onDismiss={expanded=null},onSave={save(asset)},onShare={shareAsset(context,asset)},onAnimate={expanded=null;animate(asset)},onVariation={expanded=null;requestImage(asset.optString("prompt"),asset.optString("conversation"))},onDelete={vm.deleteAsset(asset);expanded=null})}
    ModalNavigationDrawer(drawerState=drawer,drawerContent={ModalDrawerSheet {
        Text("As tuas conversas",Modifier.padding(24.dp),style=MaterialTheme.typography.titleLarge)
        FilledTonalButton(onClick={vm.newChat();scope.launch{drawer.close()}},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp)){Icon(Icons.Outlined.Add,null);Text("Nova conversa",Modifier.padding(start=8.dp))}
        OutlinedTextField(historyQuery,{historyQuery=it},label={Text("Pesquisar conversas")},leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.padding(16.dp))
        LazyColumn{items(vm.conversations.filter{it.toString().contains(historyQuery,true)},key={it.getString("id")}){conversation->
            ListItem(headlineContent={Text(conversation.optString("title","Conversa"),maxLines=2,overflow=TextOverflow.Ellipsis)},modifier=Modifier.clickable{vm.openConversation(conversation);scope.launch{drawer.close()}},trailingContent={IconButton(onClick={vm.deleteConversation(conversation.getString("id"))}){Icon(Icons.Outlined.Delete,"Eliminar conversa")}})
        }}
    }}) {
        Scaffold(topBar={TopAppBar(title={Column{Text("AI Hub Studio",style=MaterialTheme.typography.titleLarge);Text(if(vm.tab=="Chat")"Um espaço para pensar e criar" else vm.tab,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}},navigationIcon={IconButton(onClick={scope.launch{drawer.open()}}){Icon(Icons.Outlined.Menu,"Histórico")}},actions={
            IconButton(onClick={vm.newChat();vm.tab="Chat"}){Icon(Icons.Outlined.Edit,"Nova conversa")}
            IconButton(onClick={vm.settings=true}){Icon(Icons.Outlined.Tune,"Fornecedores e definições")}
        })},bottomBar={Column(Modifier.imePadding()){
            if(vm.tab=="Chat") {
                AnimatedVisibility(attachments.isNotEmpty()){Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=12.dp)){attachments.forEach{a->InputChip(selected=true,onClick={},label={Text(a.getString("name").take(25))},trailingIcon={Icon(Icons.Outlined.Close,"Remover")},modifier=Modifier.clickable{attachments=attachments-a})}}}
                Surface(tonalElevation=2.dp){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.Bottom){
                    IconButton(enabled=vm.supportsAttachment(),onClick={attach.launch(arrayOf("image/*","application/pdf"))}){Icon(Icons.Outlined.AttachFile,if(vm.supportsAttachment())"Anexar imagem ou documento" else "Anexos indisponíveis neste modelo")}
                    OutlinedTextField(draft,{draft=it},placeholder={Text("Escreve uma mensagem…")},modifier=Modifier.weight(1f).testTag("chat-composer"),shape=RoundedCornerShape(24.dp),maxLines=5)
                    IconButton(enabled=vm.chatBusy||draft.isNotBlank()||attachments.isNotEmpty(),onClick={if(vm.chatBusy)vm.stopChat()else{vm.send(draft,attachments);draft="";attachments=emptyList()}}){Icon(if(vm.chatBusy)Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.Send,if(vm.chatBusy)"Interromper resposta" else "Enviar")}
                }}
            }
            NavigationBar {listOf("Chat" to Icons.Outlined.ChatBubbleOutline,"Imagens" to Icons.Outlined.Image,"Vídeos" to Icons.Outlined.Movie,"Projetos" to Icons.Outlined.FolderOpen).forEach{(label,icon)->NavigationBarItem(modifier=Modifier.testTag("nav-$label"),selected=vm.tab==label,onClick={vm.tab=label},icon={Icon(icon,null)},label={Text(label)})}}
        }}){padding->Column(Modifier.fillMaxSize().padding(padding)){
            if(vm.tab=="Chat") {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){ModelSelector(vm)};IconButton(onClick={vm.settings=true}){Icon(Icons.Outlined.AddCircleOutline,"Adicionar fornecedor")}}
            }
            Text(vm.status,Modifier.padding(horizontal=16.dp,vertical=5.dp).testTag("connection-status"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary,maxLines=4,overflow=TextOverflow.Ellipsis)
            when(vm.tab){
                "Chat"->ChatTimeline(vm,onRetry={generation=it},onImage={text->requestImage(text,vm.conversationId)},onOpen={expanded=it},onAnimate={animate(it)},onSave={save(it)})
                "Imagens","Vídeos"->{
                    val kind=if(vm.tab=="Imagens")"image" else "video"
                    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.testTag("media-gallery")) {
                        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text(if(kind=="image")"Imagina. Cria. Explora." else "Dá movimento às tuas ideias.",style=MaterialTheme.typography.titleLarge);Text("Geração online · sem PC",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)};FilledIconButton(onClick={generation=GenerationRequest(kind,"","",null)}){Icon(Icons.Outlined.Add,if(kind=="image")"Gerar imagem" else "Gerar vídeo")}}}
                        if(kind=="image")item{OutlinedButton(enabled=!vm.importingImage,onClick={galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},modifier=Modifier.fillMaxWidth().testTag("choose-gallery")){Icon(Icons.Outlined.PhotoLibrary,null);Text("Escolher da galeria",Modifier.padding(start=8.dp))};if(vm.importingImage)LinearProgressIndicator(Modifier.fillMaxWidth())}
                        items(vm.tasks.filter{it.optString("kind")==kind&&it.optString("state")!="done"}.take(10),key={it.getString("id")}){TaskCard(it,onCancel={vm.cancelTask(it.getString("id"))},onRetry={generation=requestFromTask(it,vm.assets)})}
                        if(vm.assets.none{it.optString("kindMedia")==kind})item {EmptyCard(if(kind=="image")"A tua galeria começa com uma ideia" else "Cria um clip a partir de texto ou de uma imagem","Os resultados aparecem aqui e ficam guardados no Android.",Icons.Outlined.AutoAwesome)}
                        items(vm.assets.filter{it.optString("kindMedia")==kind},key={it.getString("id")}){asset->AssetCard(asset,onOpen={expanded=asset},onAnimate={animate(asset)},onVariation={requestImage(asset.optString("prompt"),asset.optString("conversation"))},onSave={save(asset)},onShare={shareAsset(context,asset)},onProject={AssetProjectMenu(vm,asset)},onDelete={vm.deleteAsset(asset)})}
                    }
                }
                else->ProjectsScreen(vm,onAsset={expanded=it})
            }
        }}
    }
}

@Composable private fun ModelSelector(vm:StudioModel) {
    var open by remember{mutableStateOf(false)}
    val selected=vm.models.firstOrNull{it.optString("provider")+"|"+it.optString("id")==vm.selection}
    Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){
        Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(18.dp));Text(selected?.let{vm.ai.provider(it.getString("provider")).name.substringBefore(" —")+" · "+it.getString("id")}?:"Escolher IA / automático",Modifier.weight(1f).padding(horizontal=8.dp),maxLines=1,overflow=TextOverflow.Ellipsis);Icon(Icons.Outlined.ExpandMore,null)
    };DropdownMenu(expanded=open,onDismissRequest={open=false},modifier=Modifier.heightIn(max=400.dp)){
        DropdownMenuItem(text={Text("Automático · apenas modelos testados")},onClick={vm.chooseModel("");open=false})
        vm.models.forEach{m->DropdownMenuItem(text={Text(vm.ai.provider(m.getString("provider")).name.substringBefore(" —")+" · "+m.getString("id"))},onClick={vm.chooseModel(m.getString("provider")+"|"+m.getString("id"));open=false})}
        DropdownMenuItem(text={Text("Configurar fornecedor…")},onClick={open=false;vm.settings=true})
    }}
}

@Composable private fun ChatTimeline(vm:StudioModel,onRetry:(GenerationRequest)->Unit,onImage:(String)->Unit,onOpen:(JSONObject)->Unit,onAnimate:(JSONObject)->Unit,onSave:(JSONObject)->Unit) {
    val context=LocalContext.current;val list=rememberLazyListState()
    LaunchedEffect(vm.conversationId,vm.messages.size,vm.assets.size,vm.chatBusy){if(vm.messages.isNotEmpty()){withFrameNanos{};list.animateScrollToItem(vm.messages.size-1)}}
    LazyColumn(state=list,modifier=Modifier.fillMaxSize().testTag("chat-timeline"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        if(vm.messages.isEmpty())item {Column(Modifier.fillMaxWidth().padding(vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally){Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Outlined.AutoAwesome,null,Modifier.padding(20.dp).size(32.dp))};Spacer(Modifier.height(20.dp));Text("O que vamos criar hoje?",style=MaterialTheme.typography.headlineSmall);Text("Conversa, imagens e vídeo no mesmo espaço.",style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.height(20.dp));listOf("Cria um storyboard cinematográfico","Ajuda-me a desenvolver uma ideia","Explica este código passo a passo").forEach{suggestion->OutlinedButton(onClick={vm.send(suggestion)},modifier=Modifier.fillMaxWidth().padding(vertical=3.dp),shape=RoundedCornerShape(16.dp)){Text(suggestion)}}}}
        items(vm.messages.indices.toList()){index->val message=vm.messages[index];val user=message.optString("role")=="user"
            val asset=message.optString("assetId").takeIf{it.isNotBlank()}?.let{id->vm.assets.firstOrNull{it.optString("id")==id}}
            Column(Modifier.fillMaxWidth(),horizontalAlignment=if(user)Alignment.End else Alignment.Start){
                if(asset!=null)AssetCard(asset,onOpen={onOpen(asset)},onAnimate={onAnimate(asset)},onVariation={onImage(asset.optString("prompt"))},onSave={onSave(asset)},onShare={shareAsset(context,asset)},onProject={AssetProjectMenu(vm,asset)},onDelete={vm.deleteAsset(asset)})
                else if(user)Surface(shape=RoundedCornerShape(22.dp,22.dp,6.dp,22.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.widthIn(max=600.dp)){Column(Modifier.padding(15.dp)){Text(message.optString("content"));message.optJSONArray("attachments")?.objects()?.forEach{Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.AttachFile,null,Modifier.size(14.dp));Text(it.optString("name"),style=MaterialTheme.typography.labelSmall)}}}}
                else {
                    Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary);Text("AI Hub",Modifier.padding(start=8.dp),style=MaterialTheme.typography.labelMedium)}
                    MarkdownBody(message.optString("content"),vm.dark)
                    Row(Modifier.horizontalScroll(rememberScrollState())){
                        IconButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Resposta",message.optString("content")))}){Icon(Icons.Outlined.ContentCopy,"Copiar resposta",Modifier.size(18.dp))}
                        IconButton(onClick={shareText(context,message.optString("content"))}){Icon(Icons.AutoMirrored.Outlined.Send,"Partilhar resposta",Modifier.size(18.dp))}
                        if(index==vm.messages.lastIndex)IconButton(enabled=!vm.chatBusy,onClick={vm.send("",regenerate=true)}){Icon(Icons.Outlined.Refresh,"Regenerar resposta",Modifier.size(18.dp))}
                        TextButton(onClick={onImage(message.optString("content"))}){Icon(Icons.Outlined.Image,null,Modifier.size(16.dp));Text("Gerar imagem",Modifier.padding(start=5.dp))}
                    }
                    val scenes=message.optString("content").split(Regex("\\n(?=\\d+[.)] |#{1,3} )")).filter{it.length>40}
                    if(scenes.size>1)SceneActions(scenes,onImage)
                }
            }
        }
        items(vm.tasks.filter{it.optString("conversation")==vm.conversationId&&it.optString("state") in listOf("queued","running","failed")}){task->TaskCard(task,onCancel={vm.cancelTask(task.getString("id"))},onRetry={onRetry(requestFromTask(task,vm.assets))})}
        if(vm.chatBusy)item{Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Text("A pensar…",Modifier.padding(start=12.dp),color=MaterialTheme.colorScheme.secondary)}}
        item{Spacer(Modifier.height(12.dp))}
    }
}

@Composable private fun MarkdownBody(text:String,dark:Boolean){
    val context=LocalContext.current;val renderer=remember{Markwon.create(context)}
    AndroidView(factory={TextView(it).apply{textSize=16f;typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL);setTextIsSelectable(true);setLineSpacing(4f,1.1f);movementMethod=android.text.method.LinkMovementMethod.getInstance()}},update={view->view.setTextColor(if(dark)android.graphics.Color.rgb(231,236,243) else android.graphics.Color.rgb(28,38,50));view.setLinkTextColor(if(dark)android.graphics.Color.rgb(117,219,202) else android.graphics.Color.rgb(0,107,97));renderer.setMarkdown(view,text)},modifier=Modifier.fillMaxWidth().padding(top=10.dp))
}
@Composable private fun SceneActions(scenes:List<String>,onImage:(String)->Unit){var open by remember{mutableStateOf(false)};Box{TextButton(onClick={open=true}){Text("Escolher uma cena para imagem")};DropdownMenu(expanded=open,onDismissRequest={open=false}){scenes.forEach{scene->DropdownMenuItem(text={Text(scene.take(90),maxLines=3)},onClick={open=false;onImage(scene)})}}}}
private fun shareText(context:Context,text:String){context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Partilhar resposta"))}
fun shareAssetIntent(context:Context,asset:JSONObject):Intent{val uri=FileProvider.getUriForFile(context,context.packageName+".files",File(asset.getString("path")));return Intent.createChooser(Intent(Intent.ACTION_SEND).setType(asset.getString("mime")).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Partilhar criação")}
fun shareAsset(context:Context,asset:JSONObject){context.startActivity(shareAssetIntent(context,asset))}
