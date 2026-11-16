package app.cuaderno.model

/**
 * Claves de orden fraccionales: texto que se ordena alfabéticamente y siempre permite
 * insertar una clave entre otras dos sin tocar las demás (docs/arquitectura.md §3.2).
 *
 * Los caracteres son `0-9`, `A-Z` y `a-z` (en ese orden en ASCII, así que el orden
 * del texto coincide con el de SQL y el de Kotlin). Ninguna clave termina en `0`:
 * eso garantiza que siempre hay espacio antes de cualquier clave.
 */
object SortKey {
    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62

    /** Clave para el primer elemento de una lista vacía. */
    val first: String get() = between(null, null)

    /**
     * Devuelve una clave estrictamente mayor que [before] y menor que [after].
     * Con [before] nulo, es "antes de todo"; con [after] nulo, "después de todo".
     *
     * @throws IllegalArgumentException si alguna clave es inválida o [before] no es menor que [after].
     */
    fun between(before: String?, after: String?): String {
        before?.let(::validate)
        after?.let(::validate)
        if (before != null && after != null) {
            require(before < after) { "La clave anterior ($before) debe ser menor que la siguiente ($after)" }
        }
        return midpoint(before ?: "", after)
    }

    private fun validate(key: String) {
        require(key.isNotEmpty() && key.all { it in DIGITS } && !key.endsWith('0')) {
            "Clave de orden inválida: \"$key\""
        }
    }

    private fun midpoint(a: String, b: String?): String {
        if (b != null) {
            // Prefijo común (la parte corta de `a` se completa con ceros).
            var n = 0
            while (n < b.length && (a.getOrElse(n) { '0' }) == b[n]) n++
            if (n > 0) return b.substring(0, n) + midpoint(a.drop(n), b.drop(n))
        }
        val digitA = if (a.isNotEmpty()) DIGITS.indexOf(a[0]) else 0
        val digitB = if (b != null) DIGITS.indexOf(b[0]) else BASE
        return if (digitB - digitA > 1) {
            DIGITS[(digitA + digitB) / 2].toString()
        } else if (b != null && b.length > 1) {
            b.substring(0, 1)
        } else {
            DIGITS[digitA] + midpoint(a.drop(1), null)
        }
    }
}
