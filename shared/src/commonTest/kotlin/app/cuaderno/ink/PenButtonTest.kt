package app.cuaderno.ink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PenButtonTest {
    @Test
    fun elToqueSimpleCuentaAlSoltar() {
        assertTrue(PenButton.isPress(PenButton.SINGLE_TAP_KEY, isDown = false, repeatCount = 0))
        assertFalse(PenButton.isPress(PenButton.SINGLE_TAP_KEY, isDown = true, repeatCount = 0))
    }

    @Test
    fun elDobleToqueCuentaAlApretarYNoAlSoltar() {
        assertTrue(PenButton.isPress(601, isDown = true, repeatCount = 0))
        assertFalse(PenButton.isPress(601, isDown = false, repeatCount = 0))
    }

    @Test
    fun lasRepeticionesNoCuentan() {
        assertFalse(PenButton.isPress(601, isDown = true, repeatCount = 1))
    }

    @Test
    fun click_suma_una_pulsacion() {
        val antes = PenButton.clicks
        PenButton.click()
        assertEquals(antes + 1, PenButton.clicks)
    }
}
