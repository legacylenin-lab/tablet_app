package app.cuaderno.domain

import app.cuaderno.model.Page
import app.cuaderno.model.PageTemplate
import kotlinx.coroutines.flow.first

/**
 * Caso de uso del arranque: devuelve la primera página del primer cuaderno, creando lo que falte.
 * Con la app recién instalada crea "Mi primer cuaderno" con una página en blanco.
 */
class EnsureFirstPage(
    private val notebooks: NotebookRepository,
    private val pages: PageRepository,
) {
    suspend operator fun invoke(): Page {
        val notebook = notebooks.observeAll().first().firstOrNull() ?: notebooks.create(FIRST_NOTEBOOK_TITLE)
        return pages.observeByNotebook(notebook.meta.id).first().firstOrNull()
            ?: pages.add(notebook.meta.id, PageTemplate.BLANK, after = null)
    }

    companion object {
        const val FIRST_NOTEBOOK_TITLE = "Mi primer cuaderno"
    }
}
