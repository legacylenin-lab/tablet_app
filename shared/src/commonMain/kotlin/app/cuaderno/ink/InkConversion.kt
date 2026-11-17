package app.cuaderno.ink

import app.cuaderno.model.StrokePoint
import kotlin.math.roundToInt

/** Un punto tal como lo entrega la librería de tinta: coordenadas decimales y tiempo absoluto. */
data class RawInput(val x: Float, val y: Float, val elapsedMs: Long, val pressure: Float)

/**
 * Conversión entre los puntos de la tinta (decimales) y los que guarda la base de datos
 * (`StrokePoint`: coordenadas enteras, tiempo relativo y presión de 0 a 255). Lógica pura,
 * para poder probarla sin Android.
 *
 * Las coordenadas son del "mundo" de la página: décimas de milímetro (docs/arquitectura.md §3.4).
 */
object InkConversion {
    /** Longitud física de una unidad de coordenada: una décima de milímetro, en centímetros. */
    const val STROKE_UNIT_LENGTH_CM = 0.01f

    fun pressureToByte(pressure: Float): Int = (pressure.coerceIn(0f, 1f) * 255f).roundToInt()

    fun byteToPressure(value: Int): Float = value.coerceIn(0, 255) / 255f

    /**
     * Pasa a puntos guardables: redondea las coordenadas, hace el tiempo relativo al primer punto
     * (el primero queda en 0, como exige `StrokeCodec`), evita que el tiempo retroceda y descarta
     * los puntos que repiten exactamente al anterior.
     */
    fun toStrokePoints(inputs: List<RawInput>): List<StrokePoint> {
        if (inputs.isEmpty()) return emptyList()
        val start = inputs.first().elapsedMs
        val result = ArrayList<StrokePoint>(inputs.size)
        for (input in inputs) {
            val t = maxOf(input.elapsedMs - start, result.lastOrNull()?.tMs ?: 0L)
            val point = StrokePoint(input.x.roundToInt(), input.y.roundToInt(), pressureToByte(input.pressure), t)
            val last = result.lastOrNull()
            if (last != null && last.x == point.x && last.y == point.y && last.tMs == point.tMs) continue
            result += point
        }
        return result
    }

    /**
     * La librería de tinta rechaza puntos repetidos (misma x, y y tiempo) o con el tiempo hacia atrás.
     * Devuelve solo los puntos que acepta.
     */
    fun forInk(points: List<StrokePoint>): List<StrokePoint> {
        val result = ArrayList<StrokePoint>(points.size)
        for (p in points) {
            val last = result.lastOrNull()
            if (last != null && (p.tMs < last.tMs || (p.x == last.x && p.y == last.y && p.tMs == last.tMs))) continue
            result += p
        }
        return result
    }
}
