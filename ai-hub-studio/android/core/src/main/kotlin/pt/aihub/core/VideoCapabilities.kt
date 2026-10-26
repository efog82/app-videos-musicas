package pt.aihub.core

/** Declared API limits, separate from account quota and live GPU availability. */
data class VideoCapabilities(
    val textToVideo:Boolean=true,
    val imageToVideo:Boolean=true,
    val minSeconds:Double=0.3,
    val maxSeconds:Double=8.5,
    val defaultSeconds:Double=2.0,
    val referenceDeterminesSize:Boolean=false
) {
    fun validate(seconds:Double,hasReference:Boolean) {
        require(seconds.isFinite()&&seconds in minSeconds..maxSeconds){"Duração fora dos limites publicados ($minSeconds–$maxSeconds s)"}
        require(if(hasReference)imageToVideo else textToVideo){if(hasReference)"Este modelo não aceita imagens" else "Este modelo exige uma imagem de referência"}
    }
}
