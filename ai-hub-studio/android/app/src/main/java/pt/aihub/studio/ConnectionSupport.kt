package pt.aihub.studio

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

fun pinnedTls(fingerprint:String):javax.net.ssl.SSLSocketFactory {
    val expected=fingerprint.replace(":","").lowercase()
    require(expected.matches(Regex("[a-f0-9]{64}"))) {"Impressão digital TLS inválida: copie os 64 caracteres do PC"}
    val manager=object:X509TrustManager {
        override fun getAcceptedIssuers()=emptyArray<X509Certificate>()
        override fun checkClientTrusted(chain:Array<out X509Certificate>?,authType:String?) {throw java.security.cert.CertificateException("Não é um certificado de cliente")}
        override fun checkServerTrusted(chain:Array<out X509Certificate>?,authType:String?) {
            val leaf=chain?.firstOrNull() ?: throw java.security.cert.CertificateException("Certificado em falta")
            leaf.checkValidity()
            val actual=MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString(""){"%02x".format(it)}
            if(!MessageDigest.isEqual(actual.toByteArray(),expected.toByteArray()))throw java.security.cert.CertificateException("A impressão digital não corresponde ao PC emparelhado")
        }
    }
    return SSLContext.getInstance("TLS").apply{init(null,arrayOf(manager),SecureRandom())}.socketFactory
}

data class LanServer(val name:String,val url:String,val fingerprint:String)
class LanDiscovery(private val context:Context) {
    suspend fun discover():List<LanServer> {
        val nsd=context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found=java.util.concurrent.ConcurrentHashMap<String,LanServer>()
        val wifi=context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock=wifi.createMulticastLock("aihub-discovery").apply{setReferenceCounted(false)}
        val listener=object:NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type:String){}
            override fun onDiscoveryStopped(type:String){}
            override fun onStartDiscoveryFailed(type:String,code:Int){}
            override fun onStopDiscoveryFailed(type:String,code:Int){}
            override fun onServiceLost(service:NsdServiceInfo){}
            override fun onServiceFound(service:NsdServiceInfo){
                if(service.serviceType!="_aihub._tcp.")return
                @Suppress("DEPRECATION")
                nsd.resolveService(service,object:NsdManager.ResolveListener{
                    override fun onResolveFailed(info:NsdServiceInfo,code:Int){}
                    override fun onServiceResolved(info:NsdServiceInfo){
                        val host=info.host?.hostAddress?.substringBefore('%') ?: return
                        val address=if(host.contains(':'))"[$host]" else host
                        val fingerprint=info.attributes["fp"]?.toString(Charsets.UTF_8).orEmpty()
                        found[info.serviceName]=LanServer(info.serviceName,"https://$address:${info.port}",fingerprint)
                    }
                })
            }
        }
        try {
            lock.acquire(); nsd.discoverServices("_aihub._tcp.",NsdManager.PROTOCOL_DNS_SD,listener)
            delay(8000)
            return found.values.sortedBy{it.name}
        } finally {
            runCatching{nsd.stopServiceDiscovery(listener)}
            if(lock.isHeld)lock.release()
        }
    }
}

fun connectionMessage(e:Throwable):String=when(e){
    is java.net.UnknownHostException->"URL incorreto ou DNS indisponível. Confirme o endereço e a ligação à Internet."
    is java.net.ConnectException->"Servidor desligado ou porta inacessível. Confirme IP do PC, porta, rede Wi-Fi e firewall privada. 127.0.0.1 no Android é o próprio telemóvel (exceto com adb reverse)."
    is java.net.SocketTimeoutException->"Tempo de ligação excedido: servidor indisponível, firewall ou rede isolada. Confirme que Android e PC estão na mesma rede."
    is javax.net.ssl.SSLException->"TLS recusado. Confirme data/hora e a impressão digital do certificado mostrada pelo PC. Não aceite um certificado desconhecido."
    is org.json.JSONException->"Resposta incompatível com a API: confirme o URL, o protocolo e a porta."
    else->e.message?.take(1200) ?: "Não foi possível concluir a ligação"
}
