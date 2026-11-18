package app.cuaderno.ink

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Pulsaciones del botón del lápiz. El botón del Lenovo Tab Pen Plus no llega como botón del puntero:
 * llega como una tecla de un dispositivo aparte, que la actividad de Android reenvía aquí.
 * Cada pulsación alterna entre lápiz y borrador.
 *
 * Medido en la tablet (PQ-20): un toque manda la tecla 600 solo al soltar (sin «apretar»); el doble toque manda
 * la 601 completa (apretar y soltar).
 */
object PenButton {
    /** Tecla que manda el toque simple. Solo llega el evento «soltar». */
    const val SINGLE_TAP_KEY = 600

    var clicks by mutableIntStateOf(0)
        private set

    fun click() {
        clicks++
    }

    /**
     * ¿Este evento de tecla del lápiz cuenta como una pulsación? El toque simple cuenta al soltar; cualquier otra
     * tecla (el doble toque) cuenta al apretar. Así cada gesto cuenta una sola vez.
     */
    fun isPress(keyCode: Int, isDown: Boolean, repeatCount: Int): Boolean =
        repeatCount == 0 && if (keyCode == SINGLE_TAP_KEY) !isDown else isDown
}
