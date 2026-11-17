package app.cuaderno.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import app.cuaderno.domain.NewStroke
import app.cuaderno.model.StrokeCodec
import app.cuaderno.model.StrokeTool
import app.cuaderno.model.Stroke as ModelStroke

/**
 * Fidelidad del trazo, en unidades de la página (décimas de milímetro): la distancia más pequeña
 * que la librería de tinta considera distinta. 0,13 equivale a poco más de una décima de píxel.
 */
internal const val BRUSH_EPSILON_UNITS = 0.13f

/** La brocha de la librería de tinta para una herramienta: lápiz de presión o resaltador. */
internal fun brushFor(tool: StrokeTool, colorArgb: Int, size: Float): Brush = Brush.createWithColorIntArgb(
    family = if (tool == StrokeTool.HIGHLIGHTER) StockBrushes.highlighter() else StockBrushes.pressurePen(),
    colorIntArgb = colorArgb,
    size = size,
    epsilon = BRUSH_EPSILON_UNITS,
)

/**
 * Convierte un trazo de la librería de tinta en lo que guarda la base de datos, con la [tool] con que se hizo.
 * Devuelve `null` si el trazo no tiene puntos. Si la entrada no trae presión, se usa la máxima.
 */
internal fun Stroke.toNewStroke(tool: StrokeTool = StrokeTool.PEN): NewStroke? {
    val scratch = StrokeInput()
    val hasPressure = inputs.hasPressure()
    val raw = List(inputs.size) { i ->
        inputs.populate(i, scratch)
        RawInput(scratch.x, scratch.y, scratch.elapsedTimeMillis, if (hasPressure) scratch.pressure else 1f)
    }
    val points = InkConversion.toStrokePoints(raw)
    if (points.isEmpty()) return null
    return NewStroke(tool, brush.colorIntArgb, brush.size, StrokeCodec.encode(points))
}

/** Reconstruye el trazo de la tinta a partir de lo guardado. Devuelve `null` si los datos no sirven. */
internal fun ModelStroke.toInkStroke(): Stroke? = try {
    val decoded = InkConversion.forInk(StrokeCodec.decode(points))
    if (decoded.isEmpty()) {
        null
    } else {
        val batch = MutableStrokeInputBatch()
        for (p in decoded) {
            batch.add(
                InputToolType.STYLUS, p.x.toFloat(), p.y.toFloat(), p.tMs,
                InkConversion.STROKE_UNIT_LENGTH_CM, InkConversion.byteToPressure(p.pressure),
            )
        }
        Stroke(brushFor(tool, colorArgb, baseWidth), batch)
    }
} catch (e: IllegalArgumentException) {
    null
}
