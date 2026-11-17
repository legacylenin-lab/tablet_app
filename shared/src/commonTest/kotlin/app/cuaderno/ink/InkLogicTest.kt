package app.cuaderno.ink

import androidx.compose.ui.input.pointer.PointerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// RF-03: entrada separada por tipo de dispositivo
class InkLogicTest {

    @Test
    fun soloElLapizEscribeYElDedoNunca() {
        assertEquals(InputAction.DRAW, inputActionFor(PointerType.Stylus))
        assertEquals(InputAction.ERASE, inputActionFor(PointerType.Eraser))
        assertEquals(InputAction.NAVIGATE, inputActionFor(PointerType.Touch))
        assertEquals(InputAction.IGNORE, inputActionFor(PointerType.Mouse))
        assertEquals(InputAction.IGNORE, inputActionFor(PointerType.Unknown))
    }

    @Test
    fun conElModoBorradorElLapizBorra() {
        assertEquals(InputAction.ERASE, inputActionFor(PointerType.Stylus, eraserMode = true))
        assertEquals(InputAction.DRAW, inputActionFor(PointerType.Stylus, eraserMode = false))
        // El botón no cambia lo que hace el dedo ni el ratón.
        assertEquals(InputAction.NAVIGATE, inputActionFor(PointerType.Touch, eraserMode = true))
        assertEquals(InputAction.IGNORE, inputActionFor(PointerType.Mouse, eraserMode = true))
    }

    @Test
    fun elGrosorCreceConLaPresion() {
        val suave = InkMath.width(4f, 0f)
        val medio = InkMath.width(4f, 0.5f)
        val fuerte = InkMath.width(4f, 1f)
        assertTrue(suave < medio && medio < fuerte)
        assertEquals(4f * InkMath.MIN_WIDTH_FACTOR, suave)
        assertEquals(4f * InkMath.MAX_WIDTH_FACTOR, fuerte)
    }

    @Test
    fun lasPresionesFueraDeRangoSeRecortan() {
        assertEquals(InkMath.width(4f, 0f), InkMath.width(4f, -3f))
        assertEquals(InkMath.width(4f, 1f), InkMath.width(4f, 9f))
    }

    @Test
    fun elBorradorDetectaLosTrazosCercanos() {
        val trazo = listOf(InkPoint(0f, 0f, 1f), InkPoint(100f, 0f, 1f))
        assertTrue(InkMath.isNear(trazo, 50f, 5f, radius = 10f))
        assertTrue(InkMath.isNear(trazo, 50f, 10f, radius = 10f))
        assertFalse(InkMath.isNear(trazo, 50f, 11f, radius = 10f))
        // Más allá de los extremos cuenta la distancia al extremo, no a la recta infinita.
        assertTrue(InkMath.isNear(trazo, 105f, 0f, radius = 10f))
        assertFalse(InkMath.isNear(trazo, 120f, 0f, radius = 10f))
    }

    @Test
    fun casosEspecialesDelBorrador() {
        assertFalse(InkMath.isNear(emptyList(), 0f, 0f, radius = 100f))
        assertTrue(InkMath.isNear(listOf(InkPoint(10f, 10f, 1f)), 12f, 10f, radius = 3f))
        assertFalse(InkMath.isNear(listOf(InkPoint(10f, 10f, 1f)), 20f, 10f, radius = 3f))
        // Dos puntos iguales (segmento de largo cero).
        val punto = listOf(InkPoint(5f, 5f, 1f), InkPoint(5f, 5f, 1f))
        assertTrue(InkMath.isNear(punto, 6f, 5f, radius = 2f))
    }
}
