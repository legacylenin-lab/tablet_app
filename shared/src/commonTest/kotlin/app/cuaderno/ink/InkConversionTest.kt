package app.cuaderno.ink

import app.cuaderno.model.StrokeCodec
import app.cuaderno.model.StrokePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InkConversionTest {

    @Test
    fun laPresionIdaYVueltaConservaLos256Valores() {
        for (b in 0..255) assertEquals(b, InkConversion.pressureToByte(InkConversion.byteToPressure(b)))
        assertEquals(0, InkConversion.pressureToByte(-1f))
        assertEquals(255, InkConversion.pressureToByte(7f))
        assertEquals(128, InkConversion.pressureToByte(0.5f))
    }

    @Test
    fun elTiempoPasaARelativoYLasCoordenadasSeRedondean() {
        val puntos = InkConversion.toStrokePoints(
            listOf(
                RawInput(10.4f, 20.6f, 5_000, 0.2f),
                RawInput(11.5f, 21.2f, 5_008, 0.4f),
                RawInput(-3.6f, 0f, 5_020, 1f),
            ),
        )
        assertEquals(
            listOf(
                StrokePoint(10, 21, 51, 0),
                StrokePoint(12, 21, 102, 8),
                StrokePoint(-4, 0, 255, 20),
            ),
            puntos,
        )
    }

    @Test
    fun elPrimerPuntoSiempreQuedaEnTiempoCero() {
        assertEquals(0L, InkConversion.toStrokePoints(listOf(RawInput(1f, 1f, 123_456_789, 0.5f))).single().tMs)
    }

    @Test
    fun descartaLosPuntosQueRepitenAlAnteriorTrasRedondear() {
        val puntos = InkConversion.toStrokePoints(
            listOf(
                RawInput(10.1f, 10.1f, 100, 0.5f),
                RawInput(10.3f, 9.9f, 100, 0.5f), // mismo punto y mismo tiempo tras redondear
                RawInput(10.3f, 9.9f, 108, 0.5f), // mismo punto, otro instante: se conserva
            ),
        )
        assertEquals(listOf(StrokePoint(10, 10, 128, 0), StrokePoint(10, 10, 128, 8)), puntos)
    }

    @Test
    fun elTiempoNuncaRetrocede() {
        val puntos = InkConversion.toStrokePoints(
            listOf(RawInput(0f, 0f, 100, 1f), RawInput(5f, 5f, 110, 1f), RawInput(9f, 9f, 105, 1f)),
        )
        assertEquals(listOf(0L, 10L, 10L), puntos.map { it.tMs })
    }

    @Test
    fun sinEntradasNoHayPuntos() {
        assertEquals(emptyList(), InkConversion.toStrokePoints(emptyList()))
        assertEquals(emptyList(), InkConversion.forInk(emptyList()))
    }

    @Test
    fun forInkQuitaRepetidosYTiemposHaciaAtras() {
        val puntos = listOf(
            StrokePoint(1, 1, 10, 0),
            StrokePoint(1, 1, 10, 0), // repetido exacto
            StrokePoint(2, 2, 10, 8),
            StrokePoint(3, 3, 10, 4), // tiempo hacia atrás
            StrokePoint(3, 3, 10, 8), // mismo tiempo que el anterior aceptado, otro lugar: vale
        )
        assertEquals(
            listOf(StrokePoint(1, 1, 10, 0), StrokePoint(2, 2, 10, 8), StrokePoint(3, 3, 10, 8)),
            InkConversion.forInk(puntos),
        )
    }

    @Test
    fun losPuntosConvertidosSeGuardanYSeLeenIgualConElCodec() {
        val entradas = List(500) { i -> RawInput(100f + i * 1.7f, 200f - i * 0.9f, 1_000L + i * 8, (i % 100) / 100f) }
        val puntos = InkConversion.toStrokePoints(entradas)
        assertEquals(puntos, StrokeCodec.decode(StrokeCodec.encode(puntos)))
        assertEquals(puntos, InkConversion.forInk(puntos))
        assertTrue(puntos.first().tMs == 0L)
    }
}
