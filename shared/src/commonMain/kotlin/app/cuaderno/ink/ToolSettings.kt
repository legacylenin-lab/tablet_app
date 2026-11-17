package app.cuaderno.ink

import app.cuaderno.model.StrokeTool

/** Un color de la barra de herramientas. [argb] es el color con su transparencia (0xAARRGGBB). */
data class InkColor(val name: String, val argb: Int)

/**
 * Herramientas de escritura a mano (RF-02): lápiz y resaltador, cada uno con su color y su grosor.
 * Lógica pura, sin Android, para poder probarla.
 *
 * Los grosores están en décimas de milímetro (docs/arquitectura.md §3.4): 4 unidades = 0,4 mm.
 */
object InkTools {
    val PEN_COLORS = listOf(
        InkColor("Negro", 0xFF000000.toInt()),
        InkColor("Azul", 0xFF1A56DB.toInt()),
        InkColor("Rojo", 0xFFD32F2F.toInt()),
        InkColor("Verde", 0xFF2E7D32.toInt()),
        InkColor("Morado", 0xFF7B1FA2.toInt()),
        InkColor("Naranja", 0xFFEF6C00.toInt()),
    )

    /** Colores del resaltador: semitransparentes (alfa 0x66), para que se lea el texto de abajo. */
    val HIGHLIGHTER_COLORS = listOf(
        InkColor("Amarillo", 0x66FFEB3B),
        InkColor("Verde", 0x6676FF03),
        InkColor("Rosa", 0x66FF4081),
        InkColor("Celeste", 0x6629B6F6),
    )

    /** Grosores del lápiz: fino 0,2 mm, medio 0,4 mm (el de siempre), grueso 0,7 mm y extra 1,2 mm. */
    val PEN_WIDTHS = listOf(2f, 4f, 7f, 12f)

    /** Grosores del resaltador: 4 mm, 6 mm y 9 mm. */
    val HIGHLIGHTER_WIDTHS = listOf(40f, 60f, 90f)

    const val DEFAULT_PEN_WIDTH = 4f
    const val DEFAULT_HIGHLIGHTER_WIDTH = 60f

    fun colorsFor(tool: StrokeTool): List<InkColor> =
        if (tool == StrokeTool.HIGHLIGHTER) HIGHLIGHTER_COLORS else PEN_COLORS

    fun widthsFor(tool: StrokeTool): List<Float> =
        if (tool == StrokeTool.HIGHLIGHTER) HIGHLIGHTER_WIDTHS else PEN_WIDTHS
}

/** Herramienta elegida ahora mismo. Cada herramienta recuerda su propio color y grosor al cambiar de una a otra. */
data class ToolSettings(
    val tool: StrokeTool = StrokeTool.PEN,
    val penColorArgb: Int = InkTools.PEN_COLORS.first().argb,
    val penWidth: Float = InkTools.DEFAULT_PEN_WIDTH,
    val highlighterColorArgb: Int = InkTools.HIGHLIGHTER_COLORS.first().argb,
    val highlighterWidth: Float = InkTools.DEFAULT_HIGHLIGHTER_WIDTH,
) {
    val colorArgb: Int get() = if (tool == StrokeTool.HIGHLIGHTER) highlighterColorArgb else penColorArgb
    val width: Float get() = if (tool == StrokeTool.HIGHLIGHTER) highlighterWidth else penWidth

    fun withTool(newTool: StrokeTool) = copy(tool = newTool)

    /** Cambia el color de la herramienta activa. */
    fun withColor(argb: Int) =
        if (tool == StrokeTool.HIGHLIGHTER) copy(highlighterColorArgb = argb) else copy(penColorArgb = argb)

    /** Cambia el grosor de la herramienta activa. */
    fun withWidth(units: Float) =
        if (tool == StrokeTool.HIGHLIGHTER) copy(highlighterWidth = units) else copy(penWidth = units)
}
