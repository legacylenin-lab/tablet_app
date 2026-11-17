package app.cuaderno.notebooks

/** Reglas del título de un cuaderno (RF-01). Lógica pura, para poder probarla. */
object NotebookTitle {
    const val MAX_LENGTH = 80

    /**
     * Limpia lo que escribió el usuario: quita espacios de los extremos, junta los espacios repetidos
     * y lo corta a [MAX_LENGTH] letras. Devuelve `null` si queda vacío (no se permite un cuaderno sin nombre).
     */
    fun normalize(raw: String): String? {
        val clean = raw.trim().split(Regex("\\s+")).joinToString(" ").take(MAX_LENGTH).trimEnd()
        return clean.ifEmpty { null }
    }
}
