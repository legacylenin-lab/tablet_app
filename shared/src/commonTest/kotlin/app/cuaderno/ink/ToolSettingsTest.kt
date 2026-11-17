package app.cuaderno.ink

import app.cuaderno.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ToolSettingsTest {

    @Test
    fun porDefectoEsLapizNegroDeCuatroDecimas() {
        val s = ToolSettings()
        assertEquals(StrokeTool.PEN, s.tool)
        assertEquals(0xFF000000.toInt(), s.colorArgb)
        assertEquals(4f, s.width)
    }

    @Test
    fun cambiarColorYGrosorAfectaSoloALaHerramientaActiva() {
        val red = InkTools.PEN_COLORS[2].argb
        val s = ToolSettings().withColor(red).withWidth(7f)
        assertEquals(red, s.colorArgb)
        assertEquals(7f, s.width)
        // El resaltador sigue con lo suyo.
        val h = s.withTool(StrokeTool.HIGHLIGHTER)
        assertEquals(InkTools.HIGHLIGHTER_COLORS.first().argb, h.colorArgb)
        assertEquals(InkTools.DEFAULT_HIGHLIGHTER_WIDTH, h.width)
    }

    @Test
    fun cadaHerramientaRecuerdaSuConfiguracionAlVolver() {
        val pink = InkTools.HIGHLIGHTER_COLORS[2].argb
        val s = ToolSettings().withColor(InkTools.PEN_COLORS[1].argb)
            .withTool(StrokeTool.HIGHLIGHTER).withColor(pink).withWidth(90f)
            .withTool(StrokeTool.PEN)
        assertEquals(InkTools.PEN_COLORS[1].argb, s.colorArgb)
        assertEquals(4f, s.width)
        val back = s.withTool(StrokeTool.HIGHLIGHTER)
        assertEquals(pink, back.colorArgb)
        assertEquals(90f, back.width)
    }

    @Test
    fun losColoresDelResaltadorSonTransparentesYLosDelLapizOpacos() {
        assertTrue(InkTools.PEN_COLORS.all { (it.argb ushr 24) == 0xFF })
        assertTrue(InkTools.HIGHLIGHTER_COLORS.all { (it.argb ushr 24) < 0xFF })
    }

    @Test
    fun laListaDeOpcionesDependeDeLaHerramienta() {
        assertEquals(InkTools.PEN_COLORS, InkTools.colorsFor(StrokeTool.PEN))
        assertEquals(InkTools.HIGHLIGHTER_COLORS, InkTools.colorsFor(StrokeTool.HIGHLIGHTER))
        assertEquals(InkTools.PEN_WIDTHS, InkTools.widthsFor(StrokeTool.PEN))
        assertEquals(InkTools.HIGHLIGHTER_WIDTHS, InkTools.widthsFor(StrokeTool.HIGHLIGHTER))
    }

    @Test
    fun losGrosoresPorDefectoEstanEnSusListasYNoSeRepitenColores() {
        assertTrue(InkTools.DEFAULT_PEN_WIDTH in InkTools.PEN_WIDTHS)
        assertTrue(InkTools.DEFAULT_HIGHLIGHTER_WIDTH in InkTools.HIGHLIGHTER_WIDTHS)
        assertEquals(InkTools.PEN_COLORS.size, InkTools.PEN_COLORS.map { it.argb }.toSet().size)
        assertNotEquals(InkTools.PEN_COLORS.map { it.argb }, InkTools.HIGHLIGHTER_COLORS.map { it.argb })
    }
}
