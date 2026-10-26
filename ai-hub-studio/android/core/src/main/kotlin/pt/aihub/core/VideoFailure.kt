package pt.aihub.core

/** Provider text is data, never rendered as active HTML or used to select paid access. */
fun videoFailure(detail:String):String {
    val clean=detail.replace(Regex("<[^>]*>")," ").replace(Regex("\\s+")," ").trim().take(700)
    val hint=when {
        clean.contains("quota",true)->"Quota gratuita insuficiente no fornecedor. Aguarde a reposição ou configure um token de conta gratuita; não há repetição automática nem alternativa paga."
        clean.contains("maximum allowed",true)->"Este Space exige mais tempo de GPU do que a conta permite. Escolha outro modelo compatível; a app não ativa subscrições."
        clean.contains("No GPU was available",true)->"Não há GPU disponível no fornecedor. Tente mais tarde; não é uma falha do telemóvel."
        clean.contains("authenticate",true)||clean.contains("sign in",true)->"O fornecedor exige autenticação legítima. Configure um token gratuito nas definições."
        else->"O Space recusou ou falhou a geração."
    }
    return "$hint Mensagem do fornecedor: $clean"
}
