package app.cuaderno.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HlcTest {

    private val a = "aaaa"
    private val b = "bbbb"

    @Test
    fun ordenaPorTiempoLuegoContadorLuegoDispositivo() {
        assertTrue(Hlc(1, 0, b) < Hlc(2, 0, a))
        assertTrue(Hlc(2, 0, b) < Hlc(2, 1, a))
        assertTrue(Hlc(2, 1, a) < Hlc(2, 1, b))
        assertEquals(0, Hlc(2, 1, a).compareTo(Hlc(2, 1, a)))
    }

    @Test
    fun textoIdaYVuelta() {
        val hlc = Hlc(1_731_772_800_000, 3, "6f1c-demo")
        assertEquals("001731772800000-0003-6f1c-demo", hlc.toText())
        assertEquals(hlc, Hlc.parse(hlc.toText()))
    }

    @Test
    fun elTextoSeOrdenaIgualQueElHlc() {
        val valores = listOf(
            Hlc(5, 0, a), Hlc(5, 1, a), Hlc(5, 10, a), Hlc(5, 255, a), Hlc(5, 255, b),
            Hlc(6, 0, a), Hlc(1_731_772_800_000, 0, a),
        )
        assertEquals(valores.sorted(), valores.shuffled().sortedBy { it.toText() })
    }

    @Test
    fun textoInvalidoFalla() {
        assertFailsWith<IllegalArgumentException> { Hlc.parse("basura") }
        assertFailsWith<IllegalArgumentException> { Hlc.parse("00000000000000x-0000-a") }
    }

    @Test
    fun valoresFueraDeRangoFallan() {
        assertFailsWith<IllegalArgumentException> { Hlc(-1, 0, a) }
        assertFailsWith<IllegalArgumentException> { Hlc(0, -1, a) }
        assertFailsWith<IllegalArgumentException> { Hlc(0, Hlc.MAX_COUNTER + 1, a) }
    }

    @Test
    fun eventoLocalConRelojAvanzandoReiniciaContador() {
        val clock = HlcClock(a)
        assertEquals(Hlc(100, 0, a), clock.next(100))
        assertEquals(Hlc(200, 0, a), clock.next(200))
    }

    @Test
    fun eventoLocalEnElMismoMilisegundoSubeContador() {
        val clock = HlcClock(a)
        clock.next(100)
        assertEquals(Hlc(100, 1, a), clock.next(100))
        assertEquals(Hlc(100, 2, a), clock.next(100))
    }

    @Test
    fun nuncaRetrocedeSiElRelojDelSistemaRetrocede() {
        val clock = HlcClock(a)
        val primero = clock.next(1_000)
        val segundo = clock.next(500)
        assertTrue(segundo > primero)
        assertEquals(1_000, segundo.wallMillis)
    }

    @Test
    fun continuaDesdeElUltimoValorGuardado() {
        val clock = HlcClock(a, initial = Hlc(1_000, 4, a))
        assertEquals(Hlc(1_000, 5, a), clock.next(900))
    }

    @Test
    fun cadaEmisionEsMayorQueLaAnterior() {
        val clock = HlcClock(a)
        val tiempos = listOf(10L, 10, 10, 9, 11, 11, 5, 12)
        val emitidos = tiempos.map { clock.next(it) }
        assertEquals(emitidos.sorted(), emitidos)
        assertEquals(emitidos.size, emitidos.toSet().size)
    }

    @Test
    fun recibirRemotoAdelantadoAdoptaSuTiempo() {
        val clock = HlcClock(a)
        clock.next(100)
        assertEquals(Hlc(500, 8, a), clock.receive(Hlc(500, 7, b), nowMillis = 200))
    }

    @Test
    fun recibirRemotoAtrasadoSoloSubeContadorLocal() {
        val clock = HlcClock(a)
        clock.next(500)
        assertEquals(Hlc(500, 1, a), clock.receive(Hlc(100, 9, b), nowMillis = 200))
    }

    @Test
    fun recibirConMismoTiempoTomaElMayorContador() {
        val clock = HlcClock(a, initial = Hlc(500, 3, a))
        assertEquals(Hlc(500, 10, a), clock.receive(Hlc(500, 9, b), nowMillis = 100))
    }

    @Test
    fun relojDeSistemaMasNuevoReiniciaContador() {
        val clock = HlcClock(a, initial = Hlc(500, 3, a))
        assertEquals(Hlc(900, 0, a), clock.receive(Hlc(400, 9, b), nowMillis = 900))
    }

    @Test
    fun detectaAdelantoSospechoso() {
        val dia = 24L * 60 * 60 * 1000
        assertTrue(HlcClock.isSuspiciousSkew(Hlc(dia + 1_001, 0, b), nowMillis = 1_000))
        assertFalse(HlcClock.isSuspiciousSkew(Hlc(dia + 1_000, 0, b), nowMillis = 1_000))
    }

    @Test
    fun contadorDesbordadoFallaConClaridad() {
        val clock = HlcClock(a, initial = Hlc(100, Hlc.MAX_COUNTER, a))
        assertFailsWith<IllegalStateException> { clock.next(100) }
    }
}
