package pt.aihub.core

/** Portable timeline domain; reusable by future JVM desktop clients. */
data class Clip(val file: String, val start: Double, val duration: Double, val effect: String = "none") {
    init { require(file.isNotBlank()); require(start.isFinite() && start >= 0); require(duration.isFinite() && duration > 0 && duration <= 600) }
}
data class Subtitle(val startMs: Long, val endMs: Long, val text: String) {
    init { require(startMs >= 0 && endMs > startMs) }
}
object Srt {
    private val timing = Regex("(\\d{2,}):(\\d{2}):(\\d{2})[,\\.](\\d{3})\\s*-->\\s*(\\d{2,}):(\\d{2}):(\\d{2})[,\\.](\\d{3})")
    fun parse(source: String): List<Subtitle> = source.replace("\r", "").trim().split(Regex("\\n\\s*\\n")).map { block ->
        val lines = block.lines()
        val index = lines.indexOfFirst { "-->" in it }
        require(index >= 0) { "Bloco SRT sem intervalo" }
        val match = timing.matchEntire(lines[index]) ?: error("Intervalo SRT inválido")
        val v = match.groupValues.drop(1).map(String::toLong)
        fun ms(i: Int) = v[i] * 3600000 + v[i + 1] * 60000 + v[i + 2] * 1000 + v[i + 3]
        Subtitle(ms(0), ms(4), lines.drop(index + 1).joinToString("\n"))
    }
    fun shift(items: List<Subtitle>, offset: Long) = items.map { it.copy(startMs = it.startMs + offset, endMs = it.endMs + offset) }
    fun write(items: List<Subtitle>): String {
        fun time(ms: Long): String = "%02d:%02d:%02d,%03d".format(java.util.Locale.ROOT, ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000)
        return items.mapIndexed { i, s -> "${i+1}\n${time(s.startMs)} --> ${time(s.endMs)}\n${s.text}" }.joinToString("\n\n") + "\n"
    }
}
