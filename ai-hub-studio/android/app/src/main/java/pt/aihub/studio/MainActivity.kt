package pt.aihub.studio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import pt.aihub.core.Clip
import pt.aihub.core.Srt
import java.io.File
import java.util.Locale

private val tabs = listOf("Configuração", "Chat", "Imagens", "Vídeo", "Música e Áudio", "Editor", "Programação", "Agentes", "Modelos", "Projetos", "Definições")
private val violet = Color(0xFFBBA3FF)

class MainActivity: ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var speechReady = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val available = tts?.setLanguage(Locale.forLanguageTag("pt-PT"))
                speechReady = available != TextToSpeech.LANG_MISSING_DATA && available != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("tasks", "Conclusão de tarefas", NotificationManager.IMPORTANCE_DEFAULT))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary=violet, background=Color(0xFF0C101D), surface=Color(0xFF151C2E), secondary=Color(0xFF78DAC4))) {
                Surface(Modifier.fillMaxSize()) { Hub() }
            }
        }
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }

    @Composable private fun Hub() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val vault = remember { Vault(context) }
        val db = remember { LocalData(context) }
        var base by remember { mutableStateOf(vault.get("base")) }
        var token by remember { mutableStateOf(vault.get("token")) }
        var direct by remember { mutableStateOf(vault.get("mode_v2") != "pc") }
        var ollama by remember { mutableStateOf(vault.get("ollama").ifBlank { "http://127.0.0.1:11434" }) }
        val api = remember { Api({ vault.get("base") }, { vault.get("token") }, fingerprint={vault.get("fingerprint")},isPc=true) }
        val ai = remember { DirectAi(vault) }
        var aiProvider by remember { mutableStateOf(vault.get("ai_provider").ifBlank {"pollinations"}) }
        var aiKey by remember { mutableStateOf(vault.get("key_$aiProvider")) }
        var freePlan by remember { mutableStateOf(vault.get("free_$aiProvider")=="true") }
        var fingerprint by remember { mutableStateOf(vault.get("fingerprint")) }
        var fingerprintConfirmed by remember {mutableStateOf(false)}
        var pairingJson by remember { mutableStateOf("") }
        var lanServers by remember {mutableStateOf(emptyList<LanServer>())}
        var diagnostic by remember {mutableStateOf("")}
        var localResults by remember {mutableStateOf(db.list("local_file"))}
        var tab by remember { mutableStateOf(if(vault.get("setup_v2")=="done") "Chat" else "Configuração") }
        val contentState = rememberLazyListState()
        val tabScroll = rememberScrollState()
        DisposableEffect(db) { onDispose { db.close() } }
        LaunchedEffect(tab) {
            contentState.scrollToItem(0)
            if (tab == "Chat") tabScroll.scrollTo(0)
        }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Escolha uma IA em Configuração: sem chave, chave gratuita ou PC local. Nenhum serviço é ligado sem autorização.") }
        var providers by remember { mutableStateOf(emptyList<JSONObject>()) }
        var models by remember { mutableStateOf(ai.cachedModels()) }
        var files by remember { mutableStateOf(emptyList<JSONObject>()) }
        var workflows by remember { mutableStateOf(emptyList<JSONObject>()) }
        var jobs by remember { mutableStateOf(emptyList<JSONObject>()) }
        var model by remember { mutableStateOf("") }
        var selectedProject by remember { mutableStateOf("") }
        var projects by remember { mutableStateOf(db.list("project")) }
        var remote by remember { mutableStateOf(vault.get("remote_consent")=="true") }
        var metered by remember { mutableStateOf(false) }
        var prompt by remember { mutableStateOf("") }
        var messages by remember { mutableStateOf(emptyList<JSONObject>()) }
        var historyQuery by remember { mutableStateOf("") }
        var history by remember { mutableStateOf(db.list("conversation")) }
        var providerPreset by remember { mutableStateOf("") }
        var presetRows by remember { mutableStateOf(emptyList<JSONObject>()) }
        var providerName by remember { mutableStateOf("Ollama") }
        var providerType by remember { mutableStateOf("ollama") }
        var providerUrl by remember { mutableStateOf("http://127.0.0.1:11434") }
        var providerKey by remember { mutableStateOf("") }
        var providerLocal by remember { mutableStateOf(true) }
        var template by remember { mutableStateOf("") }
        var comfyProvider by remember { mutableStateOf("") }
        var generationPrompt by remember { mutableStateOf("") }
        var image by remember { mutableStateOf("") }
        var endImage by remember { mutableStateOf("") }
        var inputVideo by remember { mutableStateOf("") }
        var audio by remember { mutableStateOf("") }
        var srtFile by remember { mutableStateOf("") }
        var aspect by remember { mutableStateOf("16:9") }
        var duration by remember { mutableStateOf("5") }
        var seed by remember { mutableStateOf("42") }
        var width by remember { mutableStateOf("1280") }
        var height by remember { mutableStateOf("720") }
        var fps by remember { mutableStateOf("24") }
        var importKind by remember { mutableStateOf("file") }
        var pendingDownload by remember { mutableStateOf("") }
        var pendingText by remember { mutableStateOf("") }
        var pendingLocal by remember { mutableStateOf("") }
        var localSrt by remember { mutableStateOf("1\n00:00:00,000 --> 00:00:03,000\nEscreva a sua legenda.\n") }
        var offset by remember { mutableStateOf("0") }
        var timeline by remember { mutableStateOf(emptyList<Clip>()) }
        var clipFile by remember { mutableStateOf("") }
        var clipStart by remember { mutableStateOf("0") }
        var clipDuration by remember { mutableStateOf("5") }
        var effect by remember { mutableStateOf("none") }
        var projectName by remember { mutableStateOf("") }
        var projectContext by remember { mutableStateOf("") }
        var taskDetail by remember { mutableStateOf("") }
        var hardware by remember { mutableStateOf("") }
        var planPrompt by remember { mutableStateOf("") }
        var planDuration by remember { mutableStateOf("120") }
        var voiceText by remember { mutableStateOf("") }
        var transcriptionProvider by remember { mutableStateOf("") }
        var transcriptionModel by remember { mutableStateOf("whisper-large-v3") }
        var serverProjects by remember { mutableStateOf(emptyList<JSONObject>()) }
        var seenJobs by remember { mutableStateOf<Set<String>?>(null) }
        var confirmClear by remember { mutableStateOf(false) }

        fun action(label: String, work: suspend () -> Unit) {
            if (busy) return
            busy = true; status = label
            scope.launch {
                try { work(); status = "$label — concluído" }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = connectionMessage(e) }
                finally { busy = false }
            }
        }
        suspend fun refresh() {
            if (direct) {
                models = ai.discover(aiProvider)
            } else {
                providers = api.list("/v1/providers"); models = api.list("/v1/models")
                files = api.list("/v1/files"); workflows = api.list("/v1/workflows"); jobs = api.list("/v1/jobs")
                serverProjects = api.list("/v1/projects")
            }
        }
        fun queueSummary(j: JSONObject) { status = "Tarefa ${j.getString("id").take(8)} adicionada à fila. Consulte o progresso abaixo." }
        val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) action("Guardar ficheiro") {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { out ->
                    if(pendingLocal.isNotBlank())File(pendingLocal).inputStream().use{it.copyTo(out)} else if (pendingDownload.isNotBlank()) api.download(pendingDownload,out) else out.write(pendingText.toByteArray(Charsets.UTF_8))
                } ?: error("Não foi possível abrir o destino") }
            }
        }
        val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) action("Importar ficheiro") {
                if (importKind == "srt") {
                    localSrt = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
                    Srt.parse(localSrt)
                } else if (importKind == "workflow") {
                    val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
                    api.obj("/v1/workflows","POST",JSONObject(text)); workflows = api.list("/v1/workflows")
                } else {
                    val name = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: "ficheiro.txt"
                    withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { api.upload(name,it) } }
                    files = api.list("/v1/files")
                }
            }
        }
        fun download(file: JSONObject) { pendingLocal=""; pendingDownload = file.getString("id"); pendingText = ""; saveFile.launch(file.getString("name")) }
        fun preview(file: JSONObject) = action("Abrir resultado") {
            val ext = file.optString("disk").substringAfterLast('.',"bin")
            val dest = File(context.cacheDir,"results/${file.getString("id")}.$ext").apply { parentFile?.mkdirs() }
            withContext(Dispatchers.IO) { dest.outputStream().use { api.download(file.getString("id"),it) } }
            val uri = FileProvider.getUriForFile(context,"${context.packageName}.files",dest)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
        LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        LaunchedEffect(token, direct) {
            if (token.isBlank() || vault.get("base").isBlank()) return@LaunchedEffect
            while (isActive) {
                try {
                    val updated = api.list("/v1/jobs")
                    val finished = updated.filter { it.optString("status") in listOf("done","failed") }.map { it.getString("id") }.toSet()
                    seenJobs?.let { seen ->
                        for (id in finished - seen) {
                            val j = updated.first { it.getString("id") == id }
                            runCatching { if (Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) NotificationManagerCompat.from(context).notify(id.hashCode(), NotificationCompat.Builder(context,"tasks")
                                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim).setContentTitle("AI Hub Studio")
                                .setContentText(if(j.optString("status")=="done") "Tarefa concluída: ${j.optString("task")}" else "Tarefa falhou: consulte os detalhes")
                                .setAutoCancel(true).build()) }
                        }
                        if ((finished - seen).isNotEmpty()) files = api.list("/v1/files")
                    }
                    seenJobs = finished; jobs = updated
                } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                delay(5000)
            }
        }
        if (confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Eliminar dados locais?")},text={Text("Apaga projetos, conversas e timelines guardados neste Android. Os ficheiros do servidor permanecem disponíveis.")},
            confirmButton={TextButton(onClick={db.clear(); history=emptyList(); projects=emptyList(); messages=emptyList(); timeline=emptyList(); confirmClear=false}) {Text("Eliminar")}},
            dismissButton={TextButton(onClick={confirmClear=false}) {Text("Cancelar")}})

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("AI HUB STUDIO",style=MaterialTheme.typography.headlineSmall,color=violet)
                Text("O seu espaço de criação com IA",style=MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.horizontalScroll(tabScroll).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                tabs.forEach { label -> FilterChip(selected=tab==label,onClick={tab=label},label={Text(label)}) }
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status,Modifier.testTag("connection-status").padding(horizontal=18.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
            LazyColumn(Modifier.weight(1f).testTag("studio-content"),state=contentState,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(tab in listOf("Configuração","Modelos")) {
                    item { Title("Ligar uma inteligência artificial", "1. Sem API: Pollinations público ou modelos locais. 2. Com API: chave gratuita própria, ligação direta sem PC. 3. Servidor PC: Ollama/ComfyUI/FFmpeg por Wi-Fi com TLS ou USB. As interfaces oficiais no navegador são um modo separado.") }
                    item { Toggle("Ligar diretamente às IAs (sem servidor PC)",direct,{direct=it;model="";models=if(it)ai.cachedModels() else emptyList();vault.set("mode_v2",if(it)"direct" else "pc")}) }
                    if(direct) {
                        item { Choice("Fornecedor de IA",aiProvider,aiProviders.map{it.id to it.name},{id->if(id.isNotBlank()){aiProvider=id;model="";aiKey=vault.get("key_$id");freePlan=vault.get("free_$id")=="true";vault.set("ai_provider",id)}}) }
                        item { Text(ai.provider(aiProvider).info); if(!ai.provider(aiProvider).local&&aiProvider!="pollinations") {Field(aiKey,{aiKey=it},"Chave API gratuita",secret=true);Toggle("Confirmo plano gratuito / créditos limitados, sem faturação automática",freePlan,{freePlan=it})} }
                        if(aiProvider=="ollama") item {Field(ollama,{ollama=it},"Endpoint Ollama (USB ou HTTPS com certificado válido)")}
                        item {Choice("Modelo a testar (opcional; descoberto na API)",model,models.filter{it.optString("provider")==aiProvider}.map{aiProvider+"|"+it.getString("id") to it.getString("id")},{model=it})}
                        item { Toggle("Autorizar envio de conteúdo ao fornecedor escolhido",remote,{remote=it;vault.set("remote_consent",it.toString())}) }
                        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(enabled=!busy&&(remote||ai.provider(aiProvider).local),onClick={action("Descobrir e testar IA") {
                                vault.set("key_$aiProvider",aiKey.trim());vault.set("free_$aiProvider",freePlan.toString());vault.set("ollama",ollama.trim());vault.set("ai_provider",aiProvider)
                                vault.set("mode_v2","direct");direct=true
                                diagnostic=ai.test(aiProvider,selection=if(model.substringBefore('|')==aiProvider)model.substringAfter('|') else "",consent=remote,freePlan=freePlan); models=ai.cachedModels()
                                val m=ai.automaticModels().firstOrNull{it.optString("provider")==aiProvider}
                                if(m!=null)model=aiProvider+"|"+m.getString("id")
                                vault.set("setup_v2","done")
                            }}) {Text("Descobrir e testar IA")}
                        };Text(diagnostic) }
                        item {OutlinedButton(enabled=!busy&&(remote||ai.provider(aiProvider).local),onClick={action("Descobrir modelos sem enviar pergunta") {
                            vault.set("key_$aiProvider",aiKey.trim());vault.set("free_$aiProvider",freePlan.toString());vault.set("ollama",ollama.trim());models=ai.discover(aiProvider)
                            diagnostic="${models.count{it.optString("provider")==aiProvider}} modelo(s) descoberto(s). Selecione e teste uma resposta para habilitar o automático."
                        }}){Text("Só descobrir modelos")}}
                        item {TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(ai.provider(aiProvider).keyUrl)))}){Text(if(aiProvider=="pollinations")"Documentação e limites do serviço sem chave" else "Obter chave / consultar condições oficiais")};TextButton(onClick={tab="Chat"}){Text("Ir para o Chat")}}
                        item {Text("Modo automático: ${ai.automaticModels().size} modelos com resposta real verificada há menos de 30 minutos. Se a quota terminar, o modelo é retirado do automático até novo teste.",style=MaterialTheme.typography.bodySmall)}
                    } else item {Text("Servidor PC: execute Instalar-e-Iniciar.cmd no Windows, mantenha a janela aberta e complete a ligação nas Definições.");Button(onClick={tab="Definições"}){Text("Configurar ligação ao PC")}}
                    if(tab=="Configuração") {
                        item {Text("Serviços oficiais gratuitos no navegador — não são APIs integradas:",style=MaterialTheme.typography.titleMedium)}
                        items(freeServices){ service -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)){Text(service.name+" · "+service.modality);Text(service.note,style=MaterialTheme.typography.bodySmall);TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(service.url)))}){Text("Abrir serviço oficial")}}} }
                    }
                }
                if (tab in listOf("Chat","Programação")) {
                    item { Title(if(tab=="Chat") "Converse com os seus modelos" else "Assistente de programação", "As respostas ficam guardadas neste Android. O envio ao modelo segue as opções de privacidade abaixo.") }
                    item { TextButton(onClick={tab="Configuração"}){Text(if(models.isEmpty())"Nenhuma IA ligada — configurar agora" else "Configurar ou testar outra IA")} }
                    item { Choice("Modelo (automático apenas entre modelos testados)",model,models.map { it.getString("provider")+"|"+it.getString("id") to ((if(direct) ai.provider(it.getString("provider")).name+" · " else "")+it.optString("name",it.getString("id"))+(if(direct&&!it.optBoolean("verified"))" (por testar)" else "")) },{ model=it }) }
                    item { Choice("Contexto do projeto local",selectedProject,projects.map { it.getString("id") to it.getString("name") },{ selectedProject=it }) }
                    item { if(direct) Toggle("Autorizar envio ao fornecedor de IA",remote,{remote=it;vault.set("remote_consent",it.toString())}) else Privacy(remote,{remote=it},metered,{metered=it},false) }
                    items(messages) { m -> Card { Column(Modifier.fillMaxWidth().padding(14.dp)) { Text(if(m.optString("role")=="user") "Tu" else "Assistente",color=violet); Text(m.optString("content")) } } }
                    item { Field(prompt,{prompt=it},if(tab=="Programação") "Código ou instrução" else "Escreva uma mensagem",4) }
                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(enabled=!busy && prompt.isNotBlank(),onClick={action("Consultar modelo") {
                            val current = messages + JSONObject().put("role","user").put("content",prompt)
                            val contextText = projects.firstOrNull { it.getString("id")==selectedProject }?.optString("context").orEmpty()
                            val sent = (if(contextText.isNotBlank()) listOf(JSONObject().put("role","system").put("content",contextText)) else emptyList()) + current
                            val body = JSONObject().put("messages",JSONArray(sent))
                            val result = if(direct) {
                                ai.chat(model,sent,remote,false) {status=it}.also {models=ai.cachedModels()}
                            } else {
                                if(model.isNotBlank()) body.put("candidates",JSONArray().put(JSONObject().put("provider",model.substringBefore('|')).put("model",model.substringAfter('|'))))
                                api.obj("/v1/chat","POST",body.put("remote_consent",remote).put("allow_metered",metered)).getString("text")
                            }
                            messages = current + JSONObject().put("role","assistant").put("content",result)
                            db.save("conversation",JSONObject().put("messages",JSONArray(messages)).put("title",prompt.take(80))); history=db.list("conversation",historyQuery); prompt=""
                        }}) {Text("Enviar")}
                        OutlinedButton(onClick={messages=emptyList()}) {Text("Nova conversa")}
                    } }
                    if (!direct) item { OutlinedButton(enabled=!busy && prompt.isNotBlank(),onClick={action("Comparar modelos") {
                        require(models.isNotEmpty()) { "Descubra modelos primeiro" }
                        val candidates = if(model.isNotBlank()) listOf(JSONObject().put("provider",model.substringBefore('|')).put("model",model.substringAfter('|'))) + models.filter { it.getString("provider") != model.substringBefore('|') }.take(2).map { JSONObject().put("provider",it.getString("provider")).put("model",it.getString("id")) } else models.take(3).map { JSONObject().put("provider",it.getString("provider")).put("model",it.getString("id")) }
                        val body = JSONObject().put("messages",JSONArray().put(JSONObject().put("role","user").put("content",prompt))).put("candidates",JSONArray(candidates.take(3))).put("remote_consent",remote).put("allow_metered",metered)
                        val rows = JSONArray(api.request("/v1/compare","POST",body)).objects()
                        messages = messages + rows.map { JSONObject().put("role","assistant").put("content",it.optString("provider")+" / "+it.optString("model")+"\n"+it.optString("text",it.optString("error"))) }
                    }}) {Text("Comparar até 3 modelos disponíveis")} }
                    item { Field(historyQuery,{historyQuery=it; history=db.list("conversation",it)},"Pesquisar histórico local") }
                    items(history.take(20)) { h -> Card { Column(Modifier.padding(12.dp)) { Text(h.optString("title","Conversa")); Row {TextButton(onClick={messages=h.getJSONArray("messages").objects()}) {Text("Abrir")}; TextButton(onClick={db.delete(h.getString("id"));history=db.list("conversation",historyQuery)}) {Text("Eliminar")}} } } }
                    if(!direct) item { Choice("Documento para contexto",image,files.filter { it.optString("name").substringAfterLast('.').lowercase() in listOf("pdf","txt","srt","json") }.map { it.getString("id") to it.getString("name") },{image=it}); OutlinedButton(enabled=image.isNotBlank()&&!busy,onClick={action("Extrair documento") { prompt += "\n\n" + api.obj("/v1/files/$image/text").getString("text") }}) {Text("Acrescentar texto ao pedido")} }
                }
                if(tab in listOf("Imagens","Vídeo")) {
                    val modality = if(tab=="Imagens") "image" else "video"
                    item { Title(if(modality=="image") "Estúdio de imagem" else "Vídeo generativo", "Executa workflows reais ComfyUI. Os controlos só têm efeito quando ligados aos inputs do workflow importado. Modelos, nós e licenças são geridos no PC.") }
                    if(modality=="image") item {
                        Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Imagem gratuita sem chave — Pollinations")
                            Field(generationPrompt,{generationPrompt=it},"Instrução para imagem",3)
                            Toggle("Autorizar envio da descrição ao serviço público",remote,{remote=it})
                            Button(enabled=!busy&&generationPrompt.isNotBlank()&&remote,onClick={action("Gerar imagem gratuita") {
                                val bytes=ai.image(generationPrompt,width.toInt(),height.toInt(),seed.toLong(),remote){status=it}
                                val dir=File(context.filesDir,"gallery").apply{mkdirs()};val f=File(dir,java.util.UUID.randomUUID().toString()+".jpg")
                                withContext(Dispatchers.IO){f.writeBytes(bytes)}
                                db.save("local_file",JSONObject().put("name","Imagem Pollinations").put("path",f.absolutePath));localResults=db.list("local_file")
                            }}){Text("Gerar imagem sem PC")}
                            Text("API Classic: saída JPEG com marca do serviço e área máxima 768×768; a proporção é mantida. Intervalo mínimo 45 s. Não usa créditos nem carteira de pagamentos.",style=MaterialTheme.typography.bodySmall)
                        }}
                    }
                    item { Choice("Servidor ComfyUI",comfyProvider,providers.filter { it.optString("type")=="comfy" }.map {it.getString("id") to it.getString("name")},{comfyProvider=it}) }
                    item { Choice("Workflow",template,workflows.filter {it.optString("modality")==modality}.map {it.getString("id") to it.getString("name")},{template=it}) }
                    item { Field(generationPrompt,{generationPrompt=it},"Instruções: personagem, ação, cenário, estilo e câmara",4) }
                    item { Choice("Imagem inicial / referência (opcional)",image,files.filter {it.optString("disk").substringAfterLast('.') in listOf("png","jpg","jpeg","webp")}.map {it.getString("id") to it.getString("name")},{image=it}) }
                    if(modality=="video") {
                        item { Choice("Imagem final (requer workflow compatível)",endImage,files.filter {it.optString("disk").substringAfterLast('.') in listOf("png","jpg","jpeg","webp")}.map {it.getString("id") to it.getString("name")},{endImage=it}) }
                        item { Choice("Vídeo de entrada / continuação",inputVideo,files.filter {it.optString("disk").substringAfterLast('.') in listOf("mp4","webm","mov")}.map {it.getString("id") to it.getString("name")},{inputVideo=it}) }
                        item { Field(duration,{duration=it},"Duração em segundos");Field(fps,{fps=it},"Fotogramas por segundo") }
                    }
                    item { Choice("Proporção",aspect,listOf("1:1","4:5","9:16","16:9").map {it to it},{ aspect=it; val size=when(it){"1:1"->720 to 720;"4:5"->720 to 900;"9:16"->720 to 1280;else->1280 to 720}; width=size.first.toString();height=size.second.toString() }) }
                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)){Field(width,{width=it},"Largura")}; Box(Modifier.weight(1f)){Field(height,{height=it},"Altura")} };Field(seed,{seed=it},"Seed (consistência entre gerações)") }
                    item { Button(enabled=!busy && comfyProvider.isNotBlank() && template.isNotBlank(),onClick={action("Executar geração") {
                        val workflow = workflows.first {it.getString("id")==template}
                        val bindings = workflow.optJSONObject("bindings") ?: JSONObject()
                        require(bindings.has("prompt") || generationPrompt.isBlank()) { "Este workflow não tem um binding prompt. Corrija o manifesto antes de gerar." }
                        val values = JSONObject().put("prompt",generationPrompt).put("width",width.toInt()).put("height",height.toInt()).put("seed",seed.toLong()).put("duration",duration.toDouble()).put("fps",fps.toInt())
                        if(image.isNotBlank()) values.put("image",image); if(endImage.isNotBlank()) values.put("end_image",endImage); if(inputVideo.isNotBlank()) values.put("video",inputVideo)
                        val j = api.obj("/v1/generate","POST",JSONObject().put("provider",comfyProvider).put("template",template).put("values",values)); queueSummary(j);jobs=api.list("/v1/jobs")
                    }}) {Text("Gerar com ComfyUI")} }
                    item { Text("Sem workflow instalado? Importe um manifesto em Modelos. Wan, LTX e AnimateDiff exigem pesos e nós próprios. Zoom/pan do editor são edição e nunca são identificados como vídeo generativo.",style=MaterialTheme.typography.bodySmall) }
                }
                if(tab=="Música e Áudio") {
                    item { Title("Laboratório de som", "Voz local Android, análise de energia, normalização e transcrição através de um endpoint compatível. A análise de batidas é uma estimativa.") }
                    item { Field(voiceText,{voiceText=it},"Texto para narração",4); Row { Button(onClick={
                        if(speechReady) { tts?.speak(voiceText,TextToSpeech.QUEUE_FLUSH,null,"aihub");status="A reproduzir com a voz Android instalada" } else status="Instale uma voz de português nas definições Android de síntese de voz"
                    },enabled=voiceText.isNotBlank()) {Text("Ouvir voz local")};TextButton(onClick={tts?.stop()}) {Text("Parar")} } }
                    item { Choice("Ficheiro de áudio",audio,files.filter {it.optString("disk").substringAfterLast('.') in listOf("mp3","wav","m4a","ogg","flac","mp4")}.map {it.getString("id") to it.getString("name")},{audio=it}) }
                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(enabled=!busy&&audio.isNotBlank(),onClick={action("Analisar áudio") {api.obj("/v1/audio/analyse/$audio","POST");jobs=api.list("/v1/jobs")}}) {Text("Analisar")}
                        OutlinedButton(enabled=!busy&&audio.isNotBlank(),onClick={action("Normalizar áudio") {api.obj("/v1/audio/normalize/$audio","POST");jobs=api.list("/v1/jobs")}}) {Text("Normalizar")}
                    } }
                    item { Choice("Fornecedor de transcrição",transcriptionProvider,providers.filter {it.optString("type")=="openai"}.map {it.getString("id") to it.getString("name")},{transcriptionProvider=it});Field(transcriptionModel,{transcriptionModel=it},"Modelo de transcrição"); Privacy(remote,{remote=it},metered,{metered=it},false) }
                    item { Button(enabled=!busy&&audio.isNotBlank()&&transcriptionProvider.isNotBlank(),onClick={action("Transcrever para SRT") {
                        api.obj("/v1/transcribe","POST",JSONObject().put("provider",transcriptionProvider).put("model",transcriptionModel).put("file",audio).put("remote_consent",remote).put("allow_metered",metered));files=api.list("/v1/files")
                    }}) {Text("Criar legendas SRT")} }
                    item { Choice("Workflow de música / efeitos / separação de fontes",template,workflows.filter {it.optString("modality")=="audio"}.map {it.getString("id") to it.getString("name")},{template=it});Choice("ComfyUI",comfyProvider,providers.filter {it.optString("type")=="comfy"}.map {it.getString("id") to it.getString("name")},{comfyProvider=it});Field(generationPrompt,{generationPrompt=it},"Descrição musical ou instrução",3) }
                    item { Button(enabled=!busy&&template.isNotBlank()&&comfyProvider.isNotBlank(),onClick={action("Executar workflow de áudio") {
                        val v=JSONObject().put("prompt",generationPrompt).put("seed",seed.toLong());if(audio.isNotBlank()) v.put("audio",audio)
                        api.obj("/v1/generate","POST",JSONObject().put("provider",comfyProvider).put("template",template).put("values",v));jobs=api.list("/v1/jobs")
                    }}) {Text("Gerar / processar áudio")} }
                    val analysis = jobs.firstOrNull {it.optString("task")=="audio-analysis"&&it.optString("status")=="done"}?.optJSONObject("result")
                    if(analysis!=null) item {
                        Text("Duração: ${analysis.optDouble("duration")} s · BPM estimado: ${analysis.opt("bpm_estimate")}")
                        val values = analysis.optJSONArray("rms") ?: JSONArray()
                        Canvas(Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surface)) {
                            if(values.length()>0) {val step=maxOf(1,values.length()/200); for(i in 0 until values.length() step step) {
                                val x=i.toFloat()/values.length()*size.width;val h=(values.optDouble(i).toFloat()*size.height).coerceIn(0f,size.height/2)
                                drawLine(violet,Offset(x,size.height/2-h),Offset(x,size.height/2+h),2f)
                            }}
                        }
                    }
                }
                if(tab=="Editor") {
                    item { Title("Montagem e legendas", "Timeline sequencial de vídeo/imagens, uma faixa musical e uma faixa de legendas. A exportação usa FFmpeg no PC. Multipista visual, keyframes e partículas ainda não estão implementados.") }
                    item { Choice("Clip da biblioteca",clipFile,files.filter {it.optString("disk").substringAfterLast('.') in listOf("mp4","webm","mov","png","jpg","jpeg","webp")}.map {it.getString("id") to it.getString("name")},{clipFile=it});Field(clipStart,{clipStart=it},"Início do corte (segundos)");Field(clipDuration,{clipDuration=it},"Duração do clip (segundos)");Choice("Efeito",effect,listOf("none" to "Sem efeito","fade" to "Entrada e saída suaves","gray" to "Preto e branco"),{effect=it}) }
                    item { Button(enabled=clipFile.isNotBlank(),onClick={runCatching { timeline=timeline+Clip(clipFile,clipStart.toDouble(),clipDuration.toDouble(),effect) }.onFailure {status="Intervalo inválido: início ≥ 0 e duração entre 0 e 600 segundos"}}) {Text("Adicionar à timeline")} }
                    items(timeline.indices.toList()) { index -> val clip=timeline[index]; Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${index+1}. ${files.firstOrNull {it.optString("id")==clip.file}?.optString("name") ?: clip.file.take(8)} · ${clip.duration}s · ${clip.effect}")
                        Row { TextButton(enabled=index>0,onClick={val l=timeline.toMutableList();val c=l.removeAt(index);l.add(index-1,c);timeline=l}) {Text("Subir")};TextButton(onClick={timeline=timeline.filterIndexed {i,_->i!=index}}) {Text("Remover")} }
                    } } }
                    item { Choice("Faixa musical (opcional)",audio,files.filter {it.optString("disk").substringAfterLast('.') in listOf("mp3","wav","m4a","flac","ogg")}.map {it.getString("id") to it.getString("name")},{audio=it});Choice("Legendas SRT da biblioteca (opcional)",srtFile,files.filter {it.optString("disk").endsWith(".srt")}.map {it.getString("id") to it.getString("name")},{srtFile=it});Choice("Formato de exportação",aspect,listOf("1:1","4:5","9:16","16:9").map{it to it},{aspect=it}) }
                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(enabled=!busy&&timeline.isNotEmpty(),onClick={action("Exportar MP4") {
                            val clips=JSONArray(timeline.map {JSONObject().put("file",it.file).put("start",it.start).put("duration",it.duration).put("effect",it.effect)})
                            val b=JSONObject().put("clips",clips).put("aspect",aspect);if(audio.isNotBlank())b.put("audio",audio);if(srtFile.isNotBlank())b.put("srt",srtFile)
                            api.obj("/v1/render","POST",b);jobs=api.list("/v1/jobs")
                        }}) {Text("Exportar MP4")}
                        OutlinedButton(onClick={db.save("timeline",JSONObject().put("clips",JSONArray(timeline.map{JSONObject().put("file",it.file).put("start",it.start).put("duration",it.duration).put("effect",it.effect)})).put("aspect",aspect));status="Timeline guardada neste Android"}) {Text("Guardar")}
                    } }
                    item { TextButton(onClick={runCatching {val t=db.list("timeline").first();timeline=t.getJSONArray("clips").objects().map{Clip(it.getString("file"),it.getDouble("start"),it.getDouble("duration"),it.getString("effect"))};aspect=t.getString("aspect")}.onFailure{status="Ainda não há uma timeline guardada"}}) {Text("Recuperar última timeline")} }
                    item { Field(localSrt,{localSrt=it},"Editor SRT — funciona sem PC",7);Field(offset,{offset=it},"Deslocar legendas em milissegundos") }
                    item { Row { OutlinedButton(onClick={importKind="srt";importFile.launch(arrayOf("*/*"))}) {Text("Importar SRT")};TextButton(onClick={runCatching {localSrt=Srt.write(Srt.shift(Srt.parse(localSrt),offset.toLong()));status="Tempos das legendas atualizados"}.onFailure{status=it.message.orEmpty()}}) {Text("Aplicar desvio")} } }
                    item { Button(onClick={runCatching{Srt.parse(localSrt);pendingLocal="";pendingDownload="";pendingText=localSrt;saveFile.launch("legendas.srt")}.onFailure{status=it.message.orEmpty()}}) {Text("Guardar SRT no Android")} }
                }
                if(tab=="Agentes") {
                    if(direct) {
                        item {Title("Agente com IA ligada", "O modelo decompõe o pedido e executa até três etapas de redação, programação ou storyboard. O resultado é guardado num projeto local. Imagens, vídeo e áudio continuam nas ferramentas próprias, sem alegar ações que não foram executadas.")}
                        item {Choice("Modelo do agente (automático = só modelos testados)",model,models.map{it.getString("provider")+"|"+it.getString("id") to it.getString("id")},{model=it});Field(planPrompt,{planPrompt=it},"Pedido ao agente",4);Toggle("Autorizar envio de conteúdo ao modelo",remote,{remote=it})}
                        item {Button(enabled=!busy&&planPrompt.isNotBlank()&&remote,onClick={action("Executar agente de IA") {
                            val result=AgentRunner(ai).run(planPrompt,model,remote){status=it}
                            taskDetail=result.toString(2);db.save("agent",result)
                            db.save("project",JSONObject().put("name",planPrompt.take(80)).put("context",taskDetail));projects=db.list("project")
                        }}){Text("Executar agente de IA")};Text(taskDetail)}
                    }

                    item { Title("Plano de montagem no PC (determinístico)", "Cria um plano determinístico com cenas de 5 segundos e requisitos explícitos. Não executa autonomamente modelos nem aprova custos.") }
                    item { Field(planPrompt,{planPrompt=it},"Objetivo do projeto",4);Field(planDuration,{planDuration=it},"Duração total em segundos") }
                    item { Button(enabled=!busy&&planPrompt.isNotBlank(),onClick={action("Criar plano") { taskDetail=api.obj("/v1/agent/plan","POST",JSONObject().put("prompt",planPrompt).put("duration",planDuration.toDouble())).toString(2) }}) {Text("Criar plano de produção")} }
                    item { Text(taskDetail);Text("Execute a análise em Música e Áudio, crie o storyboard no Chat, gere os clips em Imagens/Vídeo e monte-os no Editor. Uma cadeia automática com recuperação de erros ainda requer implementação.") }
                }
                if(tab=="Modelos") {
                    item { Title("Fornecedores e modelos", "Configure endpoints autorizados. Descoberta e respostas exigem serviços acessíveis; uma lista de modelos não prova que há quota disponível.") }
                    item { Button(enabled=!busy,onClick={action("Atualizar modelos e biblioteca") {refresh()}}) {Text(if(direct) "Atualizar modelos do fornecedor" else "Atualizar catálogo do PC")} }
                    if(!direct) {
                        item { OutlinedButton(enabled=!busy,onClick={action("Ler fornecedores predefinidos") {presetRows=api.list("/v1/presets")}}) {Text("Carregar fornecedores predefinidos")} }
                        item { Choice("Predefinição",providerPreset,presetRows.map {it.getString("name") to it.getString("name")},{ value -> providerPreset=value;presetRows.firstOrNull{it.getString("name")==value}?.let {providerName=it.getString("name");providerType=it.getString("type");providerUrl=it.getString("url");providerLocal=it.getBoolean("local")} }) }
                        item { Toggle("Autorizar envio do PC ao fornecedor externo",remote,{remote=it});Toggle("Conta gratuita sem faturação no fornecedor PC",freePlan,{freePlan=it});Field(providerName,{providerName=it},"Nome do fornecedor");Choice("Protocolo",providerType,listOf("ollama","openai","gemini","comfy").map{it to it},{providerType=it});Field(providerUrl,{providerUrl=it},"Endpoint base");Field(providerKey,{providerKey=it},"Chave API (opcional)",secret=true);Toggle("Servidor local autorizado",providerLocal,{providerLocal=it}) }
                        item { Button(enabled=!busy&&providerName.isNotBlank(),onClick={action("Guardar fornecedor no PC") {
                            api.obj("/v1/providers","POST",JSONObject().put("name",providerName).put("type",providerType).put("url",providerUrl).put("local",providerLocal).put("key",providerKey).put("free_plan",freePlan));providerKey="";providers=api.list("/v1/providers")
                        }}) {Text("Adicionar fornecedor")} }
                        items(providers) { p -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(p.getString("name"),style=MaterialTheme.typography.titleMedium);Text(p.getString("url"),style=MaterialTheme.typography.bodySmall);Text(if(p.optBoolean("has_key")) "Chave guardada no cofre do PC" else "Sem chave guardada");Row {
                            TextButton(enabled=!busy,onClick={action("Descobrir ${p.getString("name")}") { val r=api.obj("/v1/providers/${p.getString("id")}/discover","POST");if(r.has("nodes"))taskDetail="ComfyUI: ${r.getJSONArray("nodes").length()} nós disponíveis";models=api.list("/v1/models") }}) {Text("Descobrir")}
                            TextButton(enabled=!busy,onClick={action("Testar resposta real") {diagnostic=api.obj("/v1/providers/${p.getString("id")}/test","POST",JSONObject().put("remote_consent",remote)).toString(2);models=api.list("/v1/models")}}){Text("Testar IA")}
                            TextButton(enabled=!busy,onClick={action("Remover fornecedor") {api.request("/v1/providers/${p.getString("id")}","DELETE");refresh()}}) {Text("Remover")}
                        } } } }
                        item { Text(diagnostic);Text(taskDetail);OutlinedButton(onClick={importKind="workflow";importFile.launch(arrayOf("application/json","text/plain","*/*"))}) {Text("Importar manifesto ComfyUI JSON")} }
                        items(workflows) { w -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)){Text("${w.getString("name")} · ${w.getString("modality")}");Text(w.optString("requirements"));Text("Parâmetros ligados: ${w.optJSONObject("bindings")?.keys()?.asSequence()?.joinToString() ?: "nenhum"}",style=MaterialTheme.typography.bodySmall);TextButton(enabled=!busy,onClick={action("Remover workflow") {api.request("/v1/workflows/${w.getString("id")}","DELETE");workflows=api.list("/v1/workflows")}}){Text("Remover")}}} }
                        item { OutlinedButton(enabled=!busy,onClick={action("Consultar limites publicados") {taskDetail=api.request("/v1/quotas")}}) {Text("Consultar limites e quotas")} }
                    }
                    items(models) { m -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {Text(m.getString("id"));Text(if(m.optBoolean("free")) "Local ou preço zero confirmado na descoberta" else if(direct) "Quota gratuita dependente da conta; teste de resposta obrigatório" else "Preço / quota não confirmados — bloqueado por defeito",style=MaterialTheme.typography.bodySmall);Text(m.optJSONArray("capabilities")?.toString().orEmpty(),color=MaterialTheme.colorScheme.secondary)} } }
                    item { Text("Serviços oficiais no navegador (sessão e termos próprios):")
                        listOf("Gemini" to "https://gemini.google.com/","Hugging Face" to "https://huggingface.co/spaces","OpenRouter" to "https://openrouter.ai/").forEach {(name,url)->TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)))}) {Text("Abrir $name")}}
                    }
                }
                if(tab=="Projetos") {
                    item { Title("Projetos persistentes", "Guarde instruções e contexto neste Android, mesmo sem rede. Selecione um projeto no Chat para acrescentar o contexto aos pedidos.") }
                    item { Field(projectName,{projectName=it},"Nome do projeto");Field(projectContext,{projectContext=it},"Contexto, personagens e instruções",5) }
                    item { Button(enabled=projectName.isNotBlank(),onClick={db.save("project",JSONObject().put("name",projectName).put("context",projectContext));projects=db.list("project");projectName="";projectContext="";status="Projeto guardado localmente"}) {Text("Guardar neste Android")} }
                    items(projects) { p -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {Text(p.getString("name"));Text(p.optString("context").take(400));Row {
                        TextButton(onClick={selectedProject=p.getString("id");tab="Chat"}) {Text("Usar no Chat")}
                        TextButton(enabled=!busy,onClick={action("Copiar projeto para o PC") {api.obj("/v1/projects","POST",JSONObject().put("name",p.getString("name")).put("context",p.optString("context")));serverProjects=api.list("/v1/projects")}}) {Text("Copiar para PC")}
                        TextButton(onClick={db.delete(p.getString("id"));projects=db.list("project");if(selectedProject==p.getString("id"))selectedProject=""}) {Text("Eliminar")}
                    } } } }
                    if(serverProjects.isNotEmpty()) item {Text("Projetos no PC",style=MaterialTheme.typography.titleMedium)}
                    items(serverProjects) {p-> Card {Column(Modifier.fillMaxWidth().padding(12.dp)){Text(p.getString("name"));TextButton(onClick={db.save("project",JSONObject().put("name",p.getString("name")).put("context",p.optString("context")));projects=db.list("project")}){Text("Copiar para Android")}}} }
                }
                if(tab=="Definições") {
                    item { Title("Ligação e privacidade", "O PC executa os modelos e o FFmpeg. As chaves dos fornecedores são guardadas no cofre do PC; o token de emparelhamento fica cifrado no Android.") }
                    item { Field(base,{base=it},"URL do servidor AI Hub PC");Field(token,{token=it},"Token de emparelhamento",secret=true);Toggle("IA direta no Android (sem servidor AI Hub)",direct,{direct=it;model="";models=if(it)ai.cachedModels() else emptyList()});Field(ollama,{ollama=it},"URL do Ollama direto") }
                    item { Toggle("Comparei esta impressão digital com a mostrada no PC",fingerprintConfirmed,{fingerprintConfirmed=it});Field(fingerprint,{fingerprint=it;fingerprintConfirmed=false},"Impressão digital TLS do PC (SHA256)");Field(pairingJson,{pairingJson=it},"Ou colar conteúdo de emparelhamento.json",3)
                        OutlinedButton(onClick={runCatching{val j=JSONObject(pairingJson);base=j.getString("url");token=j.getString("token");fingerprint=j.getString("fingerprint");status="Compare a impressão digital com o PC antes de guardar"}.onFailure{status="JSON de emparelhamento inválido"}}){Text("Ler dados de emparelhamento")}
                    }
                    item {Button(enabled=!busy,onClick={action("Procurar servidores na rede local") {lanServers=LanDiscovery(context).discover();diagnostic=if(lanServers.isEmpty())"Nenhum servidor anunciado. Confirme mesma rede, servidor aberto e firewall; também pode introduzir IP manualmente." else "${lanServers.size} servidor(es) encontrado(s). Compare a impressão digital no PC; mDNS não autentica o servidor."}}){Text("Procurar PC na rede (8 s)")};Text(diagnostic)}
                    items(lanServers){server->Card{Column(Modifier.fillMaxWidth().padding(12.dp)){Text(server.name);Text(server.url);Text(server.fingerprint,style=MaterialTheme.typography.bodySmall);TextButton(onClick={base=server.url;fingerprint=server.fingerprint;fingerprintConfirmed=false;status="Endereço selecionado. Compare a impressão digital com o PC e introduza o token antes de guardar."}){Text("Selecionar endereço; confirmar no PC")}}}}
                    item { Button(enabled=!busy,onClick={action("Guardar e testar ligação") {require(direct||fingerprint.isBlank()||fingerprintConfirmed){"Compare e confirme a impressão digital TLS no PC antes do emparelhamento"};vault.set("base",base.trim());vault.set("token",token.trim());vault.set("fingerprint",fingerprint.trim());vault.set("direct",direct.toString());vault.set("mode_v2",if(direct)"direct" else "pc");vault.set("ollama",ollama.trim());if(direct){models=ai.discover(aiProvider)}else{require(api.obj("/health").optString("service")=="AI Hub Studio"){"O URL não aponta para um servidor AI Hub"};hardware=api.obj("/v1/diagnostics").toString(2);refresh();vault.set("setup_v2","done")}}}) {Text("Guardar e testar")} }
                    item { Text("Por USB: adb reverse tcp:8787 tcp:8787 e, para Ollama, adb reverse tcp:11434 tcp:11434. Utilize 127.0.0.1 nos URLs. Na rede local, utilize o IP privado do PC com HTTPS e instale a CA de confiança no Android. Nunca desative a verificação TLS nem abra portas no router.",style=MaterialTheme.typography.bodySmall) }
                    item { OutlinedButton(enabled=!busy,onClick={action("Detetar hardware do PC") {hardware=api.obj("/v1/hardware").toString(2)}}) {Text("Detetar hardware do PC")};Text(hardware) }
                    item { Text("Android: ${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}. Esta versão não executa pesos de modelos no próprio telemóvel.",style=MaterialTheme.typography.bodySmall) }
                    item { OutlinedButton(onClick={confirmClear=true}) {Text("Eliminar histórico e projetos deste Android")} }
                    item { Text("Notificações: a aplicação acompanha tarefas enquanto está aberta. O PC continua as tarefas quando a app fecha. Reabra e atualize para consultar os resultados. Não existe ainda serviço Android de acompanhamento em segundo plano.") }
                }
                if(tab in listOf("Imagens","Projetos")&&localResults.isNotEmpty()) {
                    item{Text("Resultados guardados no Android",style=MaterialTheme.typography.titleMedium)}
                    items(localResults){r->Card{Column(Modifier.fillMaxWidth().padding(12.dp)){Text(r.getString("name"));Row{
                        TextButton(onClick={pendingLocal=r.getString("path");pendingDownload="";saveFile.launch("imagem-aihub.jpg")}){Text("Guardar imagem")}
                        TextButton(enabled=!busy,onClick={action("Enviar imagem para o PC") {File(r.getString("path")).inputStream().use{api.upload("imagem-aihub.jpg",it)};files=api.list("/v1/files")}}){Text("Enviar para editor PC")}
                    }}}}
                }
                if(tab in listOf("Imagens","Vídeo","Música e Áudio","Editor","Projetos")) {
                    item { HorizontalDivider();Text("Biblioteca do PC",style=MaterialTheme.typography.titleLarge);Row {
                        OutlinedButton(enabled=!busy,onClick={importKind="file";importFile.launch(arrayOf("*/*"))}) {Text("Importar ficheiro")}
                        TextButton(enabled=!busy,onClick={action("Atualizar biblioteca") {files=api.list("/v1/files");workflows=api.list("/v1/workflows");providers=api.list("/v1/providers");jobs=api.list("/v1/jobs")}}) {Text("Atualizar")}
                    };Text("Os ficheiros selecionados são enviados para o PC emparelhado. Só seguem para fornecedores externos mediante autorização.",style=MaterialTheme.typography.bodySmall) }
                    items(files) { f -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(f.getString("name"));Text("${f.optLong("size")/1024} KiB",style=MaterialTheme.typography.bodySmall);Row {
                        TextButton(enabled=!busy,onClick={preview(f)}) {Text("Abrir")};TextButton(enabled=!busy,onClick={download(f)}) {Text("Guardar")}
                        TextButton(enabled=!busy,onClick={action("Eliminar ficheiro do PC") {api.request("/v1/files/${f.getString("id")}","DELETE");files=api.list("/v1/files")}}) {Text("Eliminar")}
                    } } } }
                }
                if(jobs.isNotEmpty()) {
                    item { HorizontalDivider();Text("Tarefas no PC",style=MaterialTheme.typography.titleLarge) }
                    items(jobs.take(30)) { j -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        val labels=mapOf("queued" to "Em espera","running" to "A executar","done" to "Concluída","failed" to "Falhou","interrupted" to "Interrompida","cancelled" to "Cancelada")
                        Text("${j.optString("task")} · ${labels[j.optString("status")] ?: j.optString("status")}")
                        val progress=j.optDouble("progress",0.0).toFloat()
                        if(j.optString("status")=="running" && progress==0f) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
                        if(j.has("error"))Text(j.getString("error"),color=MaterialTheme.colorScheme.error)
                        if(j.optString("status")=="queued")TextButton(enabled=!busy,onClick={action("Cancelar tarefa em espera") {api.obj("/v1/jobs/${j.getString("id")}/cancel","POST");jobs=api.list("/v1/jobs")}}) {Text("Cancelar")}
                        if(j.has("result"))Text(j.getJSONObject("result").optString("method","Resultado disponível na biblioteca"),style=MaterialTheme.typography.bodySmall)
                    } } }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable private fun Title(title: String, body: String) { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(title,style=MaterialTheme.typography.headlineSmall);Text(body,style=MaterialTheme.typography.bodyMedium)} }
@Composable private fun Field(value: String, change: (String)->Unit, label: String, lines: Int=1, secret: Boolean=false) {
    OutlinedTextField(value=value,onValueChange=change,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(vertical=3.dp),minLines=lines,singleLine=lines==1,
        visualTransformation=if(secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}
@Composable private fun Toggle(label: String, value: Boolean, change: (Boolean)->Unit) {Row(Modifier.fillMaxWidth().toggleable(value=value,role=Role.Switch,onValueChange=change),horizontalArrangement=Arrangement.SpaceBetween) {Text(label,Modifier.weight(1f).padding(vertical=12.dp));Switch(checked=value,onCheckedChange=null)} }
@Composable private fun Privacy(remote: Boolean, setRemote:(Boolean)->Unit,metered:Boolean,setMetered:(Boolean)->Unit,direct:Boolean) {
    if(!direct) {Toggle("Autorizar envio a serviços externos",remote,setRemote);Toggle("Autorizar modelos com custo não confirmado",metered,setMetered)
        if(metered)Text("Pode haver cobranças. Confirme o plano e desative a faturação no fornecedor se pretende usar apenas quotas gratuitas.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
}
@Composable private fun Choice(label: String, value: String, options: List<Pair<String,String>>, change:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Column {Text(label,style=MaterialTheme.typography.labelMedium);Box {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {Text(options.firstOrNull {it.first==value}?.second ?: "Nenhum / automático",maxLines=2)}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=360.dp)) {
            DropdownMenuItem(text={Text("Nenhum / automático")},onClick={change("");expanded=false})
            options.forEach {(id,name)->DropdownMenuItem(text={Text(name)},onClick={change(id);expanded=false})}
        }
    }}
}
