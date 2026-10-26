package pt.aihub.studio

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProviderSettings(vm:StudioModel,onDismiss:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var provider by remember{mutableStateOf(vm.selection.substringBefore('|').takeIf{id->aiProviders.any{it.id==id}}?:"pollinations")}
    var key by remember(provider){mutableStateOf(vm.vault.get("key_$provider"))}
    var free by remember(provider){mutableStateOf(vm.vault.get("free_$provider")=="true")}
    var hfKey by remember{mutableStateOf(vm.vault.get("key_huggingface"))}
    var cfKey by remember{mutableStateOf(vm.vault.get("media_cloudflare_key"))}
    var cfAccount by remember{mutableStateOf(vm.vault.get("media_cloudflare_account"))}
    var cfFree by remember{mutableStateOf(vm.vault.get("media_cloudflare_free")=="true")}
    var testing by remember{mutableStateOf("")}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Scaffold(topBar={TopAppBar(title={Text("Fornecedores e definições")},navigationIcon={IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"Fechar definições")}},actions={IconButton(onClick={vm.theme()}){Icon(if(vm.dark)Icons.Outlined.LightMode else Icons.Outlined.DarkMode,"Alternar tema")}})}){padding->
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("providers-settings"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                item{Text(vm.status,Modifier.testTag("settings-status"),color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.bodySmall)}
                item{Text("A tua IA de conversa",style=MaterialTheme.typography.headlineSmall);Text("Chaves guardadas com Android Keystore. Não há chaves partilhadas no APK.")}
                item{SelectField("Fornecedor de chat",provider,aiProviders.map{it.id to it.name}){provider=it};Text(vm.ai.provider(provider).info,style=MaterialTheme.typography.bodySmall)}
                if(provider!="pollinations"&&!vm.ai.provider(provider).local)item{OutlinedTextField(key,{key=it},label={Text("Chave API gratuita")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());LabeledSwitch("Confirmo conta gratuita, sem faturação automática",free){free=it}}
                item{SelectField("Modelo de chat",vm.selection,vm.models.filter{it.optString("provider")==provider}.map{provider+"|"+it.getString("id") to it.getString("id")}){vm.chooseModel(it)}}
                item{LabeledSwitch("Autorizar envio de conteúdo aos fornecedores escolhidos",vm.consent){vm.consent=it;vm.vault.set("remote_consent",it.toString())}}
                item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(enabled=!vm.configurationBusy&&(vm.consent||vm.ai.provider(provider).local),onClick={vm.configure(provider,key,free,true)}){Text("Descobrir e testar IA")}
                    OutlinedButton(enabled=!vm.configurationBusy&&(vm.consent||vm.ai.provider(provider).local),onClick={vm.configure(provider,key,free,false)}){Text("Descobrir")}
                };if(vm.configurationBusy)LinearProgressIndicator(Modifier.fillMaxWidth())}
                item{HorizontalDivider();Text("Imagem e vídeo online",style=MaterialTheme.typography.headlineSmall);Text("Os testes de API abaixo verificam acesso e assinatura. Só uma geração válida confirma que a GPU e a quota estão disponíveis.",style=MaterialTheme.typography.bodySmall)}
                item{OutlinedTextField(hfKey,{hfKey=it},label={Text("Token Hugging Face (opcional)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Text("Spaces públicos podem aceitar acesso anónimo ou exigir uma conta gratuita. Não é feita subscrição PRO.",style=MaterialTheme.typography.labelSmall)}
                item{OutlinedTextField(cfAccount,{cfAccount=it},label={Text("Cloudflare Account ID")},modifier=Modifier.fillMaxWidth());OutlinedTextField(cfKey,{cfKey=it},label={Text("Token Cloudflare Workers AI")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());LabeledSwitch("A conta Cloudflare está no plano Workers Free",cfFree){cfFree=it}}
                item{Button(onClick={vm.vault.set("key_huggingface",hfKey.trim());vm.vault.set("media_cloudflare_key",cfKey.trim());vm.vault.set("media_cloudflare_account",cfAccount.trim());vm.vault.set("media_cloudflare_free",cfFree.toString());vm.status="Credenciais multimédia guardadas no Keystore"}){Icon(Icons.Outlined.Lock,null,Modifier.size(18.dp));Text("Guardar credenciais",Modifier.padding(start=8.dp))}}
                items(mediaProviders){p->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(p.title,style=MaterialTheme.typography.titleMedium);Text((if(p.kind=="video")"Vídeo · texto e referência" else "Imagem · texto")+if(p.key)" · chave necessária" else " · acesso público / token opcional",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
                    Text(p.note,style=MaterialTheme.typography.bodySmall)
                    Text(if(p.tested)"Geração real confirmada em teste público; quota atual pode variar" else "Implementado, geração ainda não comprovada nesta conta",style=MaterialTheme.typography.labelSmall)
                    OutlinedButton(enabled=testing.isBlank(),onClick={testing=p.id;scope.launch{try{vm.status=OnlineMedia(context,vm.vault).test(p)}catch(e:Exception){vm.status=connectionMessage(e)}finally{testing=""}}}){Text(if(testing==p.id)"A testar…" else "Testar API (sem gerar)")}
                }}}
                item{HorizontalDivider();Text("Ferramentas preservadas",style=MaterialTheme.typography.titleLarge);Text("Editor FFmpeg, áudio, agente, programação, importação de workflows e ligação PC continuam disponíveis como ferramentas avançadas. Não são requisitos do novo chat, imagens ou vídeo online.",style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={context.startActivity(Intent(context,LegacyActivity::class.java))}){Icon(Icons.Outlined.Build,null,Modifier.size(18.dp));Text("Abrir ferramentas avançadas",Modifier.padding(start=8.dp))}}
                item{Text("Condições: quotas e filas dos fornecedores, sem compras de créditos, ativação de faturação ou alternativa paga automática. Os modelos sem teste de resposta não entram no automático.",style=MaterialTheme.typography.bodySmall)}
                item{Spacer(Modifier.height(30.dp))}
            }
        }
    }
}
