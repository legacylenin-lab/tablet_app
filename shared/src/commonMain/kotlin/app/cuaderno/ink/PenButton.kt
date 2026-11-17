package app.cuaderno.ink

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Pulsaciones del botón del lápiz. El botón del Lenovo Tab Pen Plus no llega como botón del puntero:
 * llega como una tecla instantánea de un dispositivo aparte, que la actividad de Android reenvía aquí.
 * Cada pulsación alterna entre lápiz y borrador.
 */
object PenButton {
    var clicks by mutableIntStateOf(0)
        private set

    fun click() {
        clicks++
    }
}
