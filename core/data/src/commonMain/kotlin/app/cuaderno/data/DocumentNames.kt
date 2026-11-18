package app.cuaderno.data

/** Título de un documento importado: el nombre del archivo sin la extensión `.pdf`. */
internal fun titleFromFileName(name: String): String {
    val base = name.trim().let { if (it.endsWith(".pdf", ignoreCase = true)) it.dropLast(4) else it }.trim()
    return base.ifEmpty { "Documento sin título" }
}

/** Un archivo de Word no se importa: se muestra la guía para convertirlo a PDF (RF-11). */
internal fun isWordFile(name: String): Boolean =
    name.trim().let { it.endsWith(".docx", ignoreCase = true) || it.endsWith(".doc", ignoreCase = true) }
