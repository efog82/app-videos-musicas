package pt.aihub.core

import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

enum class MediaAvailability(val label:String) {
    AVAILABLE("Disponível"), LOW("Quota reduzida"), EXHAUSTED("Quota esgotada"), WAITING("Em espera"), UNAVAILABLE("Temporariamente indisponível"), INVALID_CREDENTIALS("Credenciais inválidas ou sem permissão"), MODEL_UNAVAILABLE("Modelo indisponível"), UNKNOWN("Estado desconhecido")
}
data class MediaQuota(
    val state:MediaAvailability=MediaAvailability.UNKNOWN,
    val observedAt:Long=0,
    val resetAt:Long?=null,
    val predicted:Boolean=false,
    val window:String="desconhecida",
    val remaining:Long?=null,
    val limit:Long?=null,
    val unit:String="pedidos",
    val source:String="Sem informação do fornecedor",
    val detail:String=""
) {
    fun effectiveState(now:Long):MediaAvailability=if(resetAt!=null&&now>=resetAt&&state in listOf(MediaAvailability.EXHAUSTED,MediaAvailability.WAITING,MediaAvailability.LOW,MediaAvailability.UNAVAILABLE))MediaAvailability.UNKNOWN else state
    fun summary(now:Long):String {
        val stateNow=effectiveState(now)
        val balance=remaining?.takeIf{stateNow!=MediaAvailability.UNKNOWN}?.let{" · $it ${if(limit!=null)"de $limit " else ""}$unit restantes ($window)"}?:" · saldo não divulgado"
        val reset=when {
            resetAt==null->"Reposição desconhecida — não informada pelo fornecedor"
            now>=resetAt->"Prazo informado decorrido; disponibilidade por confirmar"
            else->{
                val seconds=ceil((resetAt-now)/1000.0).toLong();val duration=if(seconds>=3600)"${seconds/3600} h ${(seconds%3600)/60} min" else if(seconds>=60)"${seconds/60} min ${seconds%60} s" else "$seconds s"
                val date=Instant.ofEpochMilli(resetAt).atZone(ZoneId.of("Europe/Lisbon")).format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss z",java.util.Locale.forLanguageTag("pt-PT")))
                "${if(predicted)"Reposição prevista" else "Tentar novamente"} dentro de $duration · $date (Portugal continental)"
            }
        }
        return stateNow.label+balance+"\n"+reset+if(detail.isBlank())"" else "\n"+detail
    }
}
object MediaQuotaParser {
    fun retryAt(value:String?,now:Long):Long? {
        val text=value?.trim()?:return null
        text.toLongOrNull()?.takeIf{it in 0..31536000}?.let{return now+it*1000}
        return runCatching{ZonedDateTime.parse(text,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli().takeIf{it>=now}}.getOrNull()
    }
    fun parse(provider:String,status:Int,headers:Map<String,String>,body:String,now:Long):MediaQuota {
        val h=headers.mapKeys{it.key.lowercase()};val clean=body.replace(Regex("<[^>]*>")," ").replace(Regex("\\s+")," ").take(700)
        val cfCode=Regex("\"code\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
        val daily=provider=="cloudflare"&&(cfCode==3036||body.contains("daily free allocation",true))
        val capacity=cfCode==3040||body.contains("No GPU was available",true)||body.contains("out of capacity",true)||body.contains("capacity temporarily",true)
        val quota=body.contains("exceeded your",true)&&body.contains("quota",true)||body.contains("quota exhausted",true)||body.contains("quota esgotada",true)||body.contains("quota gratuita insuficiente",true)
        val modelMissing=status==404||cfCode in listOf(5007,3042,5035,5018,3041)
        val authentication=status==401||status==403&&!modelMissing||body.contains("authentication required",true)
        var reset=retryAt(h["retry-after"],now);var predicted=false;var source=if(reset!=null)"API: Retry-After" else "Resposta do fornecedor"
        var window=if(daily)"diária" else "desconhecida"
        var remaining=h["x-ratelimit-remaining-requests"]?.toLongOrNull()?:h["x-ratelimit-remaining"]?.toLongOrNull()
        val limit=h["x-ratelimit-limit-requests"]?.toLongOrNull()?:h["x-ratelimit-limit"]?.toLongOrNull()
        val rate=h["ratelimit"].orEmpty()
        if(remaining==null)remaining=Regex("(?:^|[;,])\\s*r=(\\d+)").find(rate)?.groupValues?.get(1)?.toLongOrNull()
        if(reset==null)Regex("(?:^|[;,])\\s*t=(\\d+)").find(rate)?.groupValues?.get(1)?.toLongOrNull()?.takeIf{it<=31536000}?.let{reset=now+it*1000;source="API: RateLimit"}
        // X-RateLimit-Reset convention is an epoch timestamp. Never guess a relative window.
        if(reset==null)h["x-ratelimit-reset"]?.toLongOrNull()?.takeIf{it in (now/1000)..(now/1000+31536000)}?.let{reset=it*1000;source="API: X-RateLimit-Reset"}
        val policy=h["ratelimit-policy"].orEmpty()
        val seconds=Regex("(?:^|[;,])\\s*w=(\\d+)").find(policy)?.groupValues?.get(1)?.toLongOrNull()
        if(!daily&&seconds!=null)window=when(seconds){60L->"por minuto";3600L->"por hora";86400L->"diária";else->"janela de $seconds s"}
        if(quota&&reset==null)Regex("try again in (\\d{1,3}):(\\d{2}):(\\d{2})",RegexOption.IGNORE_CASE).find(body)?.let{m->
            val (hours,minutes,secs)=m.destructured
            if(minutes.toInt()<60&&secs.toInt()<60){reset=now+(hours.toLong()*3600+minutes.toLong()*60+secs.toLong())*1000;source="Mensagem oficial da API: Try again in"}
        }
        if(daily&&reset==null){reset=Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();predicted=true;source="Documentação Cloudflare: 00:00 UTC; não garante desbloqueio"}
        val state=when {
            modelMissing->MediaAvailability.MODEL_UNAVAILABLE
            authentication->MediaAvailability.INVALID_CREDENTIALS
            daily||quota->MediaAvailability.EXHAUSTED
            capacity||status>=500->MediaAvailability.UNAVAILABLE
            status==429||status==402||remaining==0L->MediaAvailability.WAITING
            remaining!=null&&limit!=null&&remaining<=limit/10->MediaAvailability.LOW
            status in 200..299->MediaAvailability.AVAILABLE
            else->MediaAvailability.UNKNOWN
        }
        return MediaQuota(state,now,reset,predicted,window,remaining?.takeIf{it>=0},limit?.takeIf{it>=0},source=source,detail=if(status in 200..299)"" else clean)
    }
}
