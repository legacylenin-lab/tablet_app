package app.cuaderno.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cuaderno.domain.PageRepository
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.Page
import app.cuaderno.model.PageTemplate
import app.cuaderno.model.SortKey
import app.cuaderno.model.newId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.cuaderno.data.db.Page as PageRow

class SqlPageRepository(
    private val ctx: DataContext,
    private val dispatcher: CoroutineDispatcher,
) : PageRepository {

    private val queries get() = ctx.db.cuadernoQueries

    override fun observeByNotebook(notebookId: String): Flow<List<Page>> =
        queries.selectPagesByNotebook(notebookId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun add(notebookId: String, template: PageTemplate, after: String?): Page =
        withContext(dispatcher) {
            ctx.db.transactionWithResult {
                requireNotNull(queries.selectNotebookById(notebookId).executeAsOneOrNull()) {
                    "No existe el cuaderno: $notebookId"
                }
                val hlc = ctx.nextHlc()
                val page = newPage(notebookId, template, keyAfter(notebookId, after, endIfNull = true), hlc)
                save(page, hlc, ChangeOp.UPSERT)
                page
            }
        }

    override suspend fun duplicate(id: String): Page = withContext(dispatcher) {
        ctx.db.transactionWithResult {
            val original = findPage(id)
            val hlc = ctx.nextHlc()
            val copy = newPage(
                original.notebookId, original.template,
                keyAfter(original.notebookId, id, endIfNull = false), hlc,
            ).copy(widthMm = original.widthMm, heightMm = original.heightMm)
            save(copy, hlc, ChangeOp.UPSERT)
            copyContent(fromPageId = id, toPageId = copy.meta.id)
            copy
        }
    }

    override suspend fun move(id: String, after: String?) {
        withContext(dispatcher) {
            ctx.db.transaction {
                val current = findPage(id)
                val key = keyAfter(current.notebookId, after, endIfNull = false, excluding = id)
                val hlc = ctx.nextHlc()
                save(current.copy(meta = current.meta.updated(hlc, ctx.deviceId), sortKey = key), hlc, ChangeOp.UPSERT)
            }
        }
    }

    override suspend fun delete(id: String) {
        withContext(dispatcher) {
            ctx.db.transaction {
                val current = findPage(id)
                val hlc = ctx.nextHlc()
                save(current.copy(meta = current.meta.deleted(hlc, ctx.deviceId)), hlc, ChangeOp.DELETE)
            }
        }
    }

    private fun findPage(id: String): Page =
        queries.selectPageById(id).executeAsOneOrNull()?.toModel()
            ?: throw IllegalArgumentException("No existe la página: $id")

    /** Clave de orden para colocar algo justo después de [after] entre las páginas visibles del cuaderno. */
    private fun keyAfter(notebookId: String, after: String?, endIfNull: Boolean, excluding: String? = null): String {
        val pages = queries.selectPagesByNotebook(notebookId).executeAsList().filter { it.id != excluding }
        val index = when {
            after != null -> pages.indexOfFirst { it.id == after }.also {
                require(it >= 0) { "No existe la página de referencia: $after" }
            }
            endIfNull -> pages.size - 1
            else -> -1
        }
        return SortKey.between(pages.getOrNull(index)?.sort_key, pages.getOrNull(index + 1)?.sort_key)
    }

    private fun newPage(notebookId: String, template: PageTemplate, sortKey: String, hlc: Hlc) = Page(
        meta = Meta.created(newId(), hlc, ctx.authorId),
        notebookId = notebookId,
        sortKey = sortKey,
        template = template,
    )

    /** Copia trazos y bloques de texto visibles, con identificadores y marcas de tiempo nuevos. */
    private fun copyContent(fromPageId: String, toPageId: String) {
        for (s in queries.selectStrokesByPage(fromPageId).executeAsList()) {
            val hlc = ctx.nextHlc()
            val id = newId()
            queries.upsertStroke(
                id, toPageId, s.tool, s.color, s.base_width, s.points,
                hlc.toText(), hlc.toText(), ctx.deviceId, ctx.authorId, null,
            )
            ctx.logChange("stroke", id, ChangeOp.UPSERT, hlc)
        }
        for (t in queries.selectTextBlocksByPage(fromPageId).executeAsList()) {
            val hlc = ctx.nextHlc()
            val id = newId()
            queries.upsertTextBlock(
                id, toPageId, t.x, t.y, t.width, t.content, hlc.toText(),
                hlc.toText(), hlc.toText(), ctx.deviceId, ctx.authorId, null,
            )
            ctx.logChange("text_block", id, ChangeOp.UPSERT, hlc)
        }
    }

    /** Escribe la fila y su registro en `change_log`. Siempre dentro de una transacción. */
    private fun save(p: Page, hlc: Hlc, op: ChangeOp) {
        queries.upsertPage(
            id = p.meta.id,
            notebook_id = p.notebookId,
            sort_key = p.sortKey,
            template = p.template.name,
            width_mm = p.widthMm.toLong(),
            height_mm = p.heightMm.toLong(),
            created_hlc = p.meta.createdHlc.toText(),
            updated_hlc = p.meta.updatedHlc.toText(),
            device_id = p.meta.deviceId,
            author_id = p.meta.authorId,
            deleted_hlc = p.meta.deletedHlc?.toText(),
        )
        ctx.logChange("page", p.meta.id, op, hlc)
    }

    private fun PageRow.toModel() = Page(
        meta = Meta(
            id = id,
            createdHlc = Hlc.parse(created_hlc),
            updatedHlc = Hlc.parse(updated_hlc),
            deviceId = device_id,
            authorId = author_id,
            deletedHlc = deleted_hlc?.let(Hlc::parse),
        ),
        notebookId = notebook_id,
        sortKey = sort_key,
        template = PageTemplate.valueOf(template),
        widthMm = width_mm.toInt(),
        heightMm = height_mm.toInt(),
    )
}
