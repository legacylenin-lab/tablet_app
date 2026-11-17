package app.cuaderno.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cuaderno.domain.NotebookRepository
import app.cuaderno.model.EntityId
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.Notebook
import app.cuaderno.model.SortKey
import app.cuaderno.model.TitleSort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.cuaderno.data.db.Notebook as NotebookRow

class SqlNotebookRepository(
    private val ctx: DataContext,
    private val dispatcher: CoroutineDispatcher,
) : NotebookRepository {

    private val queries get() = ctx.db.cuadernoQueries

    override fun observeAll(): Flow<List<Notebook>> =
        queries.selectNotebooks().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toModel() } }

    override fun observeAllByTitle(): Flow<List<Notebook>> =
        queries.selectNotebooksByTitle().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toModel() } }

    override suspend fun create(title: String): Notebook = withContext(dispatcher) {
        ctx.db.transactionWithResult {
            val hlc = ctx.nextHlc()
            val last = queries.selectNotebooks().executeAsList().lastOrNull()
            val notebook = Notebook(
                meta = Meta.created(ctx.ids.newId(), hlc, ctx.authorId),
                title = title,
                sortKey = SortKey.between(last?.sort_key, null),
            )
            save(notebook, hlc, ChangeOp.UPSERT)
            notebook
        }
    }

    override suspend fun rename(id: EntityId, title: String) {
        change(id) { notebook, hlc -> notebook.copy(meta = notebook.meta.updated(hlc, ctx.deviceId), title = title) }
    }

    override suspend fun move(id: EntityId, after: EntityId?) {
        withContext(dispatcher) {
            ctx.db.transaction {
                val others = queries.selectNotebooks().executeAsList().filter { it.id != id.value }
                val index = if (after == null) -1 else others.indexOfFirst { it.id == after.value }
                require(after == null || index >= 0) { "No existe el cuaderno de referencia: $after" }
                val newKey = SortKey.between(others.getOrNull(index)?.sort_key, others.getOrNull(index + 1)?.sort_key)
                val current = queries.selectNotebookById(id.value).executeAsOneOrNull()?.toModel()
                    ?: throw IllegalArgumentException("No existe el cuaderno: $id")
                val hlc = ctx.nextHlc()
                save(current.copy(meta = current.meta.updated(hlc, ctx.deviceId), sortKey = newKey), hlc, ChangeOp.UPSERT)
            }
        }
    }

    override suspend fun delete(id: EntityId) {
        withContext(dispatcher) {
            ctx.db.transaction {
                val current = queries.selectNotebookById(id.value).executeAsOneOrNull()?.toModel()
                    ?: throw IllegalArgumentException("No existe el cuaderno: $id")
                val hlc = ctx.nextHlc()
                save(current.copy(meta = current.meta.deleted(hlc, ctx.deviceId)), hlc, ChangeOp.DELETE)
            }
        }
    }

    private suspend fun change(id: EntityId, edit: (Notebook, Hlc) -> Notebook) {
        withContext(dispatcher) {
            ctx.db.transaction {
                val current = queries.selectNotebookById(id.value).executeAsOneOrNull()?.toModel()
                    ?: throw IllegalArgumentException("No existe el cuaderno: $id")
                val hlc = ctx.nextHlc()
                save(edit(current, hlc), hlc, ChangeOp.UPSERT)
            }
        }
    }

    /** Escribe la fila y su registro en `change_log`. Siempre dentro de una transacción. */
    private fun save(n: Notebook, hlc: Hlc, op: ChangeOp) {
        queries.upsertNotebook(
            id = n.meta.id.value,
            title = n.title,
            title_sort = TitleSort.of(n.title),
            semester = n.semester,
            color = n.color?.toLong(),
            sort_key = n.sortKey,
            created_hlc = n.meta.createdHlc.toText(),
            updated_hlc = n.meta.updatedHlc.toText(),
            device_id = n.meta.deviceId.value,
            author_id = n.meta.authorId.value,
            deleted_hlc = n.meta.deletedHlc?.toText(),
        )
        ctx.logChange("notebook", n.meta.id, op, hlc)
    }

    private fun NotebookRow.toModel() = Notebook(
        meta = Meta(
            id = EntityId.parse(id),
            createdHlc = Hlc.parse(created_hlc),
            updatedHlc = Hlc.parse(updated_hlc),
            deviceId = EntityId.parse(device_id),
            authorId = EntityId.parse(author_id),
            deletedHlc = deleted_hlc?.let(Hlc::parse),
        ),
        title = title,
        sortKey = sort_key,
        semester = semester,
        color = color?.toInt(),
    )
}
