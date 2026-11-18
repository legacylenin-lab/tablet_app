package app.cuaderno.testing

import app.cuaderno.domain.PageRepository
import app.cuaderno.model.EntityId
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.Page
import app.cuaderno.model.PageTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Página de prueba del cuaderno [notebook], sin pasar por la base de datos. */
fun testPage(n: Int, notebook: EntityId): Page {
    val hlc = Hlc(wallMillis = n.toLong(), counter = 0, deviceId = testId(0xD))
    return Page(meta = Meta.created(testId(n), hlc, authorId = testId(0xA)), notebookId = notebook, sortKey = "k$n")
}

/**
 * Repositorio de páginas en memoria. La lista guarda el orden real (no usa `sortKey`).
 * Con [failWith] distinto de `null`, cada escritura falla.
 */
class FakePageRepository(initial: List<Page> = emptyList()) : PageRepository {
    val items = MutableStateFlow(initial)
    var failWith: Exception? = null
    private var nextId = 500

    override fun observeByNotebook(notebookId: EntityId): Flow<List<Page>> =
        items.map { list -> list.filter { it.notebookId == notebookId && !it.meta.isDeleted } }

    override suspend fun add(notebookId: EntityId, template: PageTemplate, after: EntityId?): Page {
        failWith?.let { throw it }
        val page = testPage(nextId++, notebookId).copy(template = template)
        insertAfter(page, after)
        return page
    }

    override suspend fun duplicate(id: EntityId): Page {
        failWith?.let { throw it }
        val original = items.value.first { it.meta.id == id }
        val copy = testPage(nextId++, original.notebookId).copy(template = original.template)
        insertAfter(copy, id)
        return copy
    }

    override suspend fun move(id: EntityId, after: EntityId?) {
        failWith?.let { throw it }
    }

    override suspend fun delete(id: EntityId) {
        failWith?.let { throw it }
        items.value = items.value.map { if (it.meta.id == id) it.copy(meta = it.meta.deleted(it.meta.updatedHlc)) else it }
    }

    private fun insertAfter(page: Page, after: EntityId?) {
        val list = items.value.toMutableList()
        val at = if (after == null) list.size else list.indexOfFirst { it.meta.id == after } + 1
        list.add(at, page)
        items.value = list
    }
}
