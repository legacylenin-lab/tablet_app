package app.cuaderno

/**
 * Algo que hizo el usuario y que otra parte de la página sabe deshacer y rehacer (por ejemplo,
 * eliminar un cuadro de texto). Así «Deshacer» de la hoja sigue el orden real de todo lo que se hizo,
 * trazos y textos mezclados.
 */
interface UndoableAction {
    fun undo()

    fun redo()
}
