package app.cuaderno.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StrokeCodecTest {

    private fun roundTrip(points: List<StrokePoint>) {
        assertEquals(points, StrokeCodec.decode(StrokeCodec.encode(points)))
    }

    @Test
    fun trazoVacio() {
        roundTrip(emptyList())
        assertContentEquals(byteArrayOf(1, 0), StrokeCodec.encode(emptyList()))
    }

    @Test
    fun unSoloPunto() {
        roundTrip(listOf(StrokePoint(1050, 1485, 128, 0)))
    }

    @Test
    fun trazoNormalIdaYVuelta() {
        roundTrip(
            listOf(
                StrokePoint(100, 200, 10, 0),
                StrokePoint(103, 198, 40, 8),
                StrokePoint(110, 190, 90, 16),
                StrokePoint(90, 250, 255, 33),
            ),
        )
    }

    @Test
    fun coordenadasNegativasYCero() {
        roundTrip(listOf(StrokePoint(0, 0, 0, 0), StrokePoint(-5, -300, 7, 0), StrokePoint(0, 0, 7, 5)))
    }

    @Test
    fun diezMilPuntosIdaYVuelta() {
        val random = Random(42)
        var x = 1000
        var y = 1500
        var t = 0L
        val points = List(10_000) { i ->
            if (i > 0) {
                x += random.nextInt(-20, 21)
                y += random.nextInt(-20, 21)
                t += random.nextLong(1, 20)
            }
            StrokePoint(x, y, random.nextInt(0, 256), t)
        }
        roundTrip(points)
    }

    @Test
    fun valoresExtremos() {
        roundTrip(
            listOf(
                StrokePoint(Int.MIN_VALUE, Int.MAX_VALUE, 0, 0),
                StrokePoint(Int.MAX_VALUE, Int.MIN_VALUE, 255, Int.MAX_VALUE.toLong() * 4),
                StrokePoint(Int.MIN_VALUE, Int.MIN_VALUE, 255, Int.MAX_VALUE.toLong() * 4),
            ),
        )
    }

    @Test
    fun ocupaMuchoMenosQueSinComprimir() {
        val random = Random(7)
        var x = 1000
        var y = 1500
        var t = 0L
        val points = List(1_000) { i ->
            if (i > 0) {
                x += random.nextInt(-8, 9)
                y += random.nextInt(-8, 9)
                t += 8
            }
            StrokePoint(x, y, random.nextInt(0, 256), t)
        }
        val bytes = StrokeCodec.encode(points)
        // Objetivo del documento: unos 4 o 5 bytes por punto frente a 16 sin comprimir.
        assertTrue(bytes.size <= points.size * 5, "Ocupa ${bytes.size} bytes")
    }

    @Test
    fun rechazaDatosInvalidosAlCodificar() {
        assertFailsWith<IllegalArgumentException> { StrokeCodec.encode(listOf(StrokePoint(0, 0, 256, 0))) }
        assertFailsWith<IllegalArgumentException> { StrokeCodec.encode(listOf(StrokePoint(0, 0, -1, 0))) }
        assertFailsWith<IllegalArgumentException> { StrokeCodec.encode(listOf(StrokePoint(0, 0, 0, 5))) }
        assertFailsWith<IllegalArgumentException> {
            StrokeCodec.encode(listOf(StrokePoint(0, 0, 0, 0), StrokePoint(1, 1, 0, 10), StrokePoint(2, 2, 0, 9)))
        }
    }

    @Test
    fun rechazaVersionDesconocida() {
        assertFailsWith<IllegalArgumentException> { StrokeCodec.decode(byteArrayOf(2, 0)) }
    }

    @Test
    fun rechazaBytesTruncadosOCorruptos() {
        val bytes = StrokeCodec.encode(listOf(StrokePoint(100, 200, 10, 0), StrokePoint(110, 210, 20, 5)))
        for (corte in 0 until bytes.size) {
            assertFailsWith<IllegalArgumentException> { StrokeCodec.decode(bytes.copyOf(corte)) }
        }
        assertFailsWith<IllegalArgumentException> { StrokeCodec.decode(bytes + byteArrayOf(0)) }
        // Dice tener 1000 puntos pero no los trae.
        assertFailsWith<IllegalArgumentException> { StrokeCodec.decode(byteArrayOf(1, 0xE8.toByte(), 0x07)) }
    }

    @Test
    fun textoAleatorioNuncaCausaOtroErrorQueElControlado() {
        val random = Random(1)
        repeat(500) {
            val basura = random.nextBytes(random.nextInt(0, 40))
            if (basura.isNotEmpty()) basura[0] = 1
            try {
                StrokeCodec.decode(basura)
            } catch (_: IllegalArgumentException) {
                // Es el error esperado para datos inválidos.
            }
        }
    }
}
