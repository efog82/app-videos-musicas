package pt.aihub.core

/** Exact per-model schemas. No substring matching or undocumented FLUX parameters. */
object CloudflareImageRequest {
    const val FLUX="@cf/black-forest-labs/flux-1-schnell"
    const val SDXL="@cf/stabilityai/stable-diffusion-xl-base-1.0"
    fun parameters(model:String,prompt:String,width:Int,height:Int,seed:Long,steps:Int=8):Map<String,Any> {
        require(prompt.isNotBlank()&&prompt.length<=2048){"Descrição Cloudflare inválida (1–2048 caracteres)"}
        return when(model) {
            FLUX->{require(steps in 4..8){"FLUX Cloudflare: escolha 4 a 8 passos"};mapOf("prompt" to prompt,"steps" to steps)}
            SDXL->{val size=SdxlQuality.effective(width,height);require(seed in 0..Int.MAX_VALUE.toLong());mapOf("prompt" to prompt,"width" to size.first,"height" to size.second,"seed" to seed,"num_steps" to 20,"guidance" to 7.5)}
            else->error("Modelo Cloudflare não autorizado nesta integração gratuita")
        }
    }
}
