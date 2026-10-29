package pt.aihub.core
import kotlin.test.*
class TimelineTest {
    @Test fun roundTripAndShift() {
        val source = listOf(Subtitle(0, 1000, "Olá\nmundo"), Subtitle(1500, 2500, "Portugal"))
        assertEquals(source, Srt.parse(Srt.write(source)))
        assertEquals(2000L, Srt.shift(source, 2000).first().startMs)
    }
    @Test fun invalidCutRejected() { assertFailsWith<IllegalArgumentException> { Clip("a", 0.0, Double.NaN) } }
    @Test fun negativeShiftRejected() { assertFailsWith<IllegalArgumentException> { Srt.shift(listOf(Subtitle(0,1000,"A")), -100) } }
}
