package pt.aihub.studio

data class MediaProvider(val id:String,val title:String,val kind:String,val base:String,val api:String,val key:Boolean,val reference:Boolean,val note:String,val models:List<String>,val tested:Boolean=false)
val mediaProviders=listOf(
    MediaProvider("pollinations-image","Pollinations Classic","image","https://image.pollinations.ai","classic",false,false,"Gratuito anónimo. Uma imagem de cada vez, ≥45 s. Máximo 768×768 em área, com marca do serviço. Sem imagem de referência. Disponibilidade de melhor esforço.",listOf("Classic — modelo do serviço"),true),
    MediaProvider("hf-flux","Hugging Face · FLUX Schnell","image","https://black-forest-labs-flux-1-schnell.hf.space","infer",false,false,"Space público oficial. ZeroGPU tem filas e quota; chamadas API podem exigir conta/token Hugging Face. Não ativa PRO nem compra GPU.",listOf("black-forest-labs/FLUX.1-schnell"),true),
    MediaProvider("cloudflare","Cloudflare Workers AI","image","https://api.cloudflare.com/client/v4","cloudflare",true,false,"Requer Account ID e token de uma conta Workers Free. Quota diária publicada: 10 000 neurónios; esgotamento bloqueia. Não ative Workers Paid.",listOf("@cf/black-forest-labs/flux-1-schnell","@cf/stabilityai/stable-diffusion-xl-base-1.0")),
    MediaProvider("hf-ltx","Hugging Face · LTX Distilled","video","https://lightricks-ltx-video-distilled.hf.space","generate",false,true,"Space público oficial Lightricks. Texto e imagem → vídeo testados em API pública em 26-10-2026; disponibilidade sujeita à quota. API/GPU pode exigir login/token gratuito, ter quota esgotada ou estar offline. Duração 0,3–8,5 s, dimensões múltiplas de 32.",listOf("LTX-Video distilled"),true),
    MediaProvider("hf-ltx-original","Hugging Face · LTX Video","video","https://lightricks-ltx-video.hf.space","generate",false,true,"Alternativa pública experimental; o adaptador valida a assinatura API antes de enviar. Pode estar indisponível ou ter mudado.",listOf("LTX-Video"))
)
