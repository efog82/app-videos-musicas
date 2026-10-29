package pt.aihub.studio

import org.json.JSONObject
import pt.aihub.core.*
import java.security.MessageDigest

/** Model observations are local evidence, never a fabricated provider balance. */
class MediaStatusStore(private val db:LocalData) {
    private fun id(provider:String,model:String)="media-status-"+MessageDigest.getInstance("SHA-256").digest("$provider|$model".toByteArray()).joinToString(""){"%02x".format(it)}
    @Synchronized fun put(provider:String,model:String,value:MediaQuota) {
        db.save("media_status",JSONObject().put("provider",provider).put("model",model).put("state",value.state.name).put("observedAt",value.observedAt).put("resetAt",value.resetAt).put("predicted",value.predicted).put("window",value.window).put("remaining",value.remaining).put("limit",value.limit).put("unit",value.unit).put("source",value.source).put("detail",value.detail),id(provider,model))
    }
    @Synchronized fun get(provider:String,model:String):MediaQuota {
        val rows=db.list("media_status")
        val own=rows.firstOrNull{it.optString("provider")==provider&&it.optString("model")==model}
        // ZeroGPU quota is shared across Spaces for the same identity, not per image/model.
        val shared=if(provider.startsWith("hf-"))rows.firstOrNull{it.optString("provider").startsWith("hf-")&&it.optString("state")==MediaAvailability.EXHAUSTED.name} else if(provider=="cloudflare")rows.firstOrNull{it.optString("provider")==provider&&it.optString("state")==MediaAvailability.EXHAUSTED.name&&it.optString("window")=="diária"} else null
        val row=if(shared!=null&&(own==null||shared.optLong("observedAt")>own.optLong("observedAt")))shared else own
        if(row==null)return MediaQuota()
        return MediaQuota(runCatching{MediaAvailability.valueOf(row.getString("state"))}.getOrDefault(MediaAvailability.UNKNOWN),row.optLong("observedAt"),if(row.isNull("resetAt"))null else row.optLong("resetAt"),row.optBoolean("predicted"),row.optString("window"),if(row.isNull("remaining"))null else row.optLong("remaining"),if(row.isNull("limit"))null else row.optLong("limit"),row.optString("unit","pedidos"),row.optString("source"),row.optString("detail"))
    }
    @Synchronized fun success(provider:String,model:String) {
        val current=get(provider,model)
        put(provider,model,if(current.remaining!=null)current.copy(observedAt=System.currentTimeMillis(),detail="Geração real validada no Android") else MediaQuota(state=MediaAvailability.AVAILABLE,observedAt=System.currentTimeMillis(),source="Geração real Android",detail="Geração real validada no Android"))
        val key="media-usage-$provider";val row=db.list("media_usage").firstOrNull{it.optString("id")==key}?:JSONObject()
        db.save("media_usage",row.put("successes",row.optLong("successes")+1),key)
    }
    fun localSuccesses(provider:String):Long=db.list("media_usage").firstOrNull{it.optString("id")=="media-usage-$provider"}?.optLong("successes")?:0
    @Synchronized fun credentialsChanged(providers:Set<String>){db.list("media_status").filter{it.optString("provider") in providers}.forEach{db.delete(it.getString("id"))}}
}
