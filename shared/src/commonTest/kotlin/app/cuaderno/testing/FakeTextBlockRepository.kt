package app.cuaderno.testing

import app.cuaderno.domain.TextBlockRepository
import app.cuaderno.model.EntityId
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.TextBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Cuadro de texto guardado de prueba. */
fun testTextBlock(n: Int, pageId: EntityId, html: String, x: Int = 100, y: Int = 100, width: Int = 1000): TextBlock {
    val hlc = Hlc(n.toLong(), 0, testId(0xD))
    return TextBlock(Meta.created(testId(n), hlc, authorId = testId(0xA)), pageId, x, y, width, html, hlc)
}

/** Repositorio de cuadros de texto en memoria. Cuenta cuántas veces se guardó el contenido. */
class FakeTextBlockRepository(initial: List<TextBlock> = emptyList()) : TextBlockRepository {
    val items = MutableStateFlow(initial)
    var failWith: Exception? = null
    var contentSaves = 0
        private set
    private var nextId = 700

    /** Cuadros guardados y visibles. */
    val visible: List<TextBlock> get() = items.value.filter { !it.meta.isDeleted }

    override fun observeByPage(pageId: EntityId): Flow<List<TextBlock>> =
        items.map { list -> list.filter { it.pageId == pageId && !it.meta.isDeleted } }

    override suspend fun create(pageId: EntityId, x: Int, y: Int, width: Int): TextBlock {
        failWith?.let { throw it }
        val block = testTextBlock(nextId++, pageId, "", x, y, width)
        items.value = items.value + block
        return block
    }

    override suspend fun updateContent(id: EntityId, html: String) {
        failWith?.let { throw it }
        contentSaves++
        items.value = items.value.map { if (it.meta.id == id) it.copy(content = html) else it }
    }

    override suspend fun moveResize(id: EntityId, x: Int, y: Int, width: Int) {
        failWith?.let { throw it }
        items.value = items.value.map { if (it.meta.id == id) it.copy(x = x, y = y, width = width) else it }
    }

    override suspend fun delete(id: EntityId) {
        failWith?.let { throw it }
        items.value = items.value.map { if (it.meta.id == id) it.copy(meta = it.meta.deleted(it.meta.updatedHlc)) else it }
    }
}
