package pt.aihub.studio

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** Model-based planner and sequential text tools. Never executes generated code or approves spending. */
class AgentRunner(private val ai:DirectAi) {
    suspend fun run(task:String,model:String,consent:Boolean,progress:(String)->Unit):JSONObject {
        progress("O modelo está a decompor a tarefa…")
        val system="És um planeador. Devolve apenas JSON {\"steps\":[{\"tool\":\"redigir\",\"instruction\":\"...\"}]}. Entre 1 e 3 etapas. Ferramentas executáveis: redigir, programar (gera código, não o executa), storyboard (descrições textuais). Não declares ter gerado imagens/vídeos, pesquisado na Internet ou processado ficheiros. Para tarefas multimédia prepara um storyboard ou instruções para as ferramentas PC. Escreve em português de Portugal."
        val plan=ai.chat(model,listOf(JSONObject().put("role","system").put("content",system),JSONObject().put("role","user").put("content",task)),consent,false,progress)
        val start=plan.indexOf('{');val end=plan.lastIndexOf('}')
        require(start>=0&&end>start){"O modelo não devolveu um plano JSON válido; não foi executada nenhuma ferramenta"}
        val steps=JSONObject(plan.substring(start,end+1)).getJSONArray("steps").objects()
        require(steps.size in 1..3&&steps.all{it.optString("tool") in listOf("redigir","programar","storyboard")&&it.optString("instruction").isNotBlank()}){"Plano recusado: ferramenta desconhecida ou número de etapas inválido"}
        val results=JSONArray()
        for((i,step) in steps.withIndex()) {
            progress("Etapa ${i+1}/${steps.size}: ${step.getString("tool")}…")
            val context=results.objects().joinToString("\n\n"){it.optString("output")}.takeLast(20000)
            val answer=ai.chat(model,listOf(JSONObject().put("role","system").put("content","Executa apenas a tarefa textual solicitada. Português de Portugal. Não inventes ações externas executadas."),
                JSONObject().put("role","user").put("content","Objetivo: $task\nResultados anteriores: $context\nEtapa atual: ${step.getString("instruction")}")),consent,false,progress)
            results.put(JSONObject().put("tool",step.getString("tool")).put("instruction",step.getString("instruction")).put("output",answer))
        }
        return JSONObject().put("task",task).put("steps",results).put("method","Planeamento por modelo real e execução sequencial de ferramentas textuais")
    }
}
