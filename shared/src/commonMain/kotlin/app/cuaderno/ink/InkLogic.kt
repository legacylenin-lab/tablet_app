package app.cuaderno.ink

import androidx.compose.ui.input.pointer.PointerType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Qué hace la app con cada tipo de entrada (RF-03, docs/arquitectura.md §8). */
enum class InputAction {
    /** Lápiz: escribe. */
    DRAW,

    /** Extremo borrador del lápiz: borra trazos. */
    ERASE,

    /** Dedo: solo desplaza, hace zoom y cambia de página. Nunca escribe. */
    NAVIGATE,

    /** Ratón u otros: no escriben (en la PC no se dibuja, RF-21). */
    IGNORE,
}

/**
 * [eraserMode]: con el modo borrador activo, el lápiz borra en vez de escribir (el Lenovo Tab Pen Plus no
 * tiene extremo borrador; su botón alterna el modo, ver PenButton). Solo cambia el comportamiento del lápiz.
 */
fun inputActionFor(type: PointerType, eraserMode: Boolean = false): InputAction = when (type) {
    PointerType.Stylus -> if (eraserMode) InputAction.ERASE else InputAction.DRAW
    PointerType.Eraser -> InputAction.ERASE
    PointerType.Touch -> InputAction.NAVIGATE
    else -> InputAction.IGNORE
}

/** Punto de un trazo en pantalla: posición en píxeles y presión de 0 a 1. */
data class InkPoint(val x: Float, val y: Float, val pressure: Float)

object InkMath {
    /** Grosor mínimo, en proporción del grosor base, cuando la presión es casi cero. */
    const val MIN_WIDTH_FACTOR = 0.35f

    /** Grosor máximo, en proporción del grosor base, con la presión al máximo. */
    const val MAX_WIDTH_FACTOR = 1.6f

    /** Grosor de la línea según la presión: crece de forma lineal entre el mínimo y el máximo. */
    fun width(baseWidth: Float, pressure: Float): Float {
        val p = min(1f, max(0f, pressure))
        return baseWidth * (MIN_WIDTH_FACTOR + (MAX_WIDTH_FACTOR - MIN_WIDTH_FACTOR) * p)
    }

    /** `true` si el punto ([x], [y]) está a [radius] píxeles o menos de la línea que une los puntos del trazo. */
    fun isNear(points: List<InkPoint>, x: Float, y: Float, radius: Float): Boolean {
        if (points.isEmpty()) return false
        if (points.size == 1) return hypot(points[0].x - x, points[0].y - y) <= radius
        for (i in 0 until points.size - 1) {
            if (distanceToSegment(x, y, points[i], points[i + 1]) <= radius) return true
        }
        return false
    }

    private fun distanceToSegment(px: Float, py: Float, a: InkPoint, b: InkPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return hypot(px - a.x, py - a.y)
        val t = min(1f, max(0f, ((px - a.x) * dx + (py - a.y) * dy) / lengthSquared))
        return hypot(px - (a.x + t * dx), py - (a.y + t * dy))
    }
}
