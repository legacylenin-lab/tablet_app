package app.cuaderno.notebooks

/**
 * Dónde está el usuario dentro de un cuaderno: página [number] (desde 1) de [total]. Lógica pura.
 */
data class PageNav(val number: Int, val total: Int) {
    val label: String get() = "Página $number de $total"
    val canGoBack: Boolean get() = number > 1
    val canGoForward: Boolean get() = number < total

    companion object {
        /** Convierte la posición pedida (desde 0) en una válida para una lista de [total] páginas. */
        fun clampIndex(index: Int, total: Int): Int = index.coerceIn(0, maxOf(0, total - 1))

        fun at(index: Int, total: Int) = PageNav(clampIndex(index, total) + 1, maxOf(total, 1))

        /** Posición que queda después de eliminar la página [index] de [total]: la misma, o la anterior si era la última. */
        fun indexAfterDelete(index: Int, total: Int): Int = clampIndex(index, total - 1)
    }
}
