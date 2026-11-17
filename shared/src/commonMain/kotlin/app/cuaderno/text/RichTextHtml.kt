package app.cuaderno.text

/**
 * Deja el HTML del editor de texto enriquecido en el formato acordado (docs/arquitectura.md §5.4):
 * solo `p`, `b`, `i`, `u`, `ul`, `ol`, `li`, `h1`–`h3` y `br`. Cualquier otra etiqueta se descarta.
 *
 * El editor guarda un título como un párrafo con un tamaño de letra (`<p><span style="font-size: 2.0em;">`),
 * así que aquí ese párrafo se convierte en `h1` (2 em o más), `h2` (1,4 em o más) o `h3` (1,1 em o más).
 * Lógica pura, para poder probarla sin pantalla.
 */
object RichTextHtml {
    private val ALLOWED = setOf("p", "b", "i", "u", "ul", "ol", "li", "h1", "h2", "h3", "br")

    private val headingParagraph = Regex("""<p><span style="font-size:\s*([0-9.]+)em;?">(.*?)</span></p>""", RegexOption.DOT_MATCHES_ALL)
    private val dangerousElements = Regex("""<(script|style)\b[^>]*>.*?</\1\s*>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val anyTag = Regex("""</?([a-zA-Z][a-zA-Z0-9]*)\b[^>]*>""")

    fun normalize(html: String): String {
        var s = dangerousElements.replace(html, "")
        s = headingParagraph.replace(s) { m ->
            val size = m.groupValues[1].toFloatOrNull() ?: 1f
            val tag = when {
                size >= 1.9f -> "h1"
                size >= 1.4f -> "h2"
                size >= 1.1f -> "h3"
                else -> null
            }
            if (tag == null) "<p>${m.groupValues[2]}</p>" else "<$tag>${m.groupValues[2]}</$tag>"
        }
        return anyTag.replace(s) { m ->
            val name = m.groupValues[1].lowercase()
            if (name !in ALLOWED) "" else if (m.value.startsWith("</")) "</$name>" else "<$name>"
        }
    }
}
