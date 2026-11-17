package app.cuaderno.text

import androidx.compose.ui.text.TextRange
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlin.test.Test
import kotlin.test.assertEquals

class RichTextHtmlTest {
    @Test
    fun conservaLasEtiquetasPermitidas() {
        val html = "<h1>T</h1><p>a <b>b</b> <i>c</i> <u>d</u><br></p><ul><li>x</li></ul><ol><li>y</li></ol>"
        assertEquals(html, RichTextHtml.normalize(html))
    }

    @Test
    fun descartaLasEtiquetasNoPermitidasYSusAtributos() {
        assertEquals("<p>hola</p>", RichTextHtml.normalize("<p class=\"x\" onclick=\"mal()\">hola</p>"))
        assertEquals("<p>ab</p>", RichTextHtml.normalize("<p><span style=\"color: red;\">a</span><a href=\"http://x\">b</a></p>"))
    }

    @Test
    fun quitaScriptYEstiloConTodoSuContenido() {
        assertEquals("<p>ok</p>", RichTextHtml.normalize("<p>ok</p><script>alert(1)</script><style>p{}</style>"))
    }

    @Test
    fun unParrafoConTamanoGrandeSeVuelveTitulo() {
        assertEquals("<h1>Hola</h1>", RichTextHtml.normalize("<p><span style=\"font-size: 2.0em;\"><b>Hola</b></span></p>").replace("<b>", "").replace("</b>", ""))
        assertEquals("<h2><b>Sub</b></h2>", RichTextHtml.normalize("<p><span style=\"font-size: 1.5em;\"><b>Sub</b></span></p>"))
        assertEquals("<h3>x</h3>", RichTextHtml.normalize("<p><span style=\"font-size: 1.17em;\">x</span></p>"))
        assertEquals("<p>x</p>", RichTextHtml.normalize("<p><span style=\"font-size: 1.0em;\">x</span></p>"))
    }

    @Test
    fun laMayusculaDeLasEtiquetasSeNormaliza() {
        assertEquals("<p><b>a</b></p>", RichTextHtml.normalize("<P><B>a</B></P>"))
    }

    // Ida y vuelta con el editor real: editor -> HTML -> editor -> HTML da lo mismo.
    @Test
    fun elContenidoResisteIdaYVueltaPorElEditor() {
        val original = "<h1>Título</h1><p>Hola <b>negrita</b> <i>cursiva</i> <u>sub</u></p><ul><li>uno</li><li>dos</li></ul><ol><li>a</li></ol><h2>Sub</h2>"
        val a = RichTextState().apply { setHtml(original) }
        val html1 = RichTextHtml.normalize(a.toHtml())
        assertEquals(original, html1)
        val b = RichTextState().apply { setHtml(html1) }
        assertEquals(html1, RichTextHtml.normalize(b.toHtml()))
    }

    @Test
    fun unTituloAplicadoComoEstiloVuelveComoTitulo() {
        val a = RichTextState().apply { setHtml("<p>Hola mundo</p>") }
        a.selection = TextRange(0, 10)
        a.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontSize = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Em)))
        val html = RichTextHtml.normalize(a.toHtml())
        assertEquals("<h1>Hola mundo</h1>", html)
        assertEquals("<h1>Hola mundo</h1>", RichTextHtml.normalize(RichTextState().apply { setHtml(html) }.toHtml()))
    }
}
