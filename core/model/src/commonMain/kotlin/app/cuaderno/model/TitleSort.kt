package app.cuaderno.model

/**
 * Clave para ordenar títulos como lo espera un lector de español (docs/arquitectura.md §3.5).
 * Es Kotlin puro: da el mismo resultado en Android y en Windows, y la base de datos la usa en
 * una columna (`title_sort`) para ordenar y paginar sin salir de SQL.
 *
 * Reglas: minúsculas; sin tildes ni diéresis en las vocales; la **ñ** es una letra propia que va
 * después de la n (se codifica como `n` + [ENYE_MARK]); espacios repetidos colapsados y recortados.
 */
object TitleSort {
    /** Carácter que sigue a la `n` de una ñ. Es mayor que cualquier letra ASCII, así que "ñ" ordena después de "nz". */
    const val ENYE_MARK = '\u007F'

    private const val COMBINING_TILDE = '̃'

    fun of(title: String): String {
        val out = StringBuilder(title.length)
        var pendingSpace = false
        for (c in title.lowercase()) {
            when {
                c.isWhitespace() -> if (out.isNotEmpty()) pendingSpace = true
                // Texto descompuesto (NFD): "n" seguida de una tilde combinada es una ñ; las demás marcas se ignoran.
                c == COMBINING_TILDE && out.lastOrNull() == 'n' -> out.append(ENYE_MARK)
                c in '̀'..'ͯ' -> Unit
                else -> {
                    if (pendingSpace) {
                        out.append(' ')
                        pendingSpace = false
                    }
                    when (c) {
                        'á', 'à', 'â', 'ä', 'ã', 'å', 'ā' -> out.append('a')
                        'é', 'è', 'ê', 'ë', 'ē' -> out.append('e')
                        'í', 'ì', 'î', 'ï', 'ī' -> out.append('i')
                        'ó', 'ò', 'ô', 'ö', 'õ', 'ō' -> out.append('o')
                        'ú', 'ù', 'û', 'ü', 'ū' -> out.append('u')
                        'ý', 'ÿ' -> out.append('y')
                        'ç' -> out.append('c')
                        'ñ' -> out.append('n').append(ENYE_MARK)
                        else -> out.append(c)
                    }
                }
            }
        }
        return out.toString()
    }
}
