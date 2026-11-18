package app.cuaderno.testing

import app.cuaderno.domain.NotebookRepository
import app.cuaderno.model.EntityId
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.Notebook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Identificador válido y predecible para pruebas: `testId(1)`, `testId(2)`… */
fun testId(n: Int): EntityId = EntityId.parse("00000000-0000-4000-8000-" + n.toString(16).padStart(12, '0'))

/** Cuaderno de prueba, sin pasar por la base de datos. */
fun testNotebook(n: Int, title: String = "Cuaderno $n"): Notebook {
    val hlc = Hlc(wallMillis = n.toLong(), counter = 0, deviceId = testId(0xD))
    return Notebook(meta = Meta.created(testId(n), hlc, authorId = testId(0xA)), title = title, sortKey = "k$n")
}

/** Repositorio de cuadernos en memoria. Con [failWith] distinto de `null`, cada escritura falla. */
class FakeNotebookRepository(initial: List<Notebook> = emptyList()) : NotebookRepository {
    val items = MutableStateFlow(initial)
    var failWith: Exception? = null
    private var nextId = 100

    override fun observeAll(): Flow<List<Notebook>> = items.map { list -> list.filter { !it.meta.isDeleted } }

    override fun observeAllByTitle(): Flow<List<Notebook>> = observeAll().map { list -> list.sortedBy { it.title } }

    override suspend fun create(title: String): Notebook {
        failWith?.let { throw it }
        val notebook = testNotebook(nextId++, title)
        items.value = items.value + notebook
        return notebook
    }

    override suspend fun rename(id: EntityId, title: String) {
        failWith?.let { throw it }
        items.value = items.value.map { if (it.meta.id == id) it.copy(title = title) else it }
    }

    override suspend fun move(id: EntityId, after: EntityId?) {
        failWith?.let { throw it }
    }

    override suspend fun delete(id: EntityId) {
        failWith?.let { throw it }
        items.value = items.value.map { if (it.meta.id == id) it.copy(meta = it.meta.deleted(it.meta.updatedHlc)) else it }
    }
}
