package app.cuaderno.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cuaderno.domain.TextBlockRepository
import app.cuaderno.model.EntityId
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.TextBlock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.cuaderno.data.db.Text_block as TextBlockRow

class SqlTextBlockRepository(
    private val ctx: DataContext,
    private val dispatcher: CoroutineDispatcher,
) : TextBlockRepository {

    private val queries get() = ctx.db.cuadernoQueries

    override fun observeByPage(pageId: EntityId): Flow<List<TextBlock>> =
        queries.selectTextBlocksByPage(pageId.value).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun create(pageId: EntityId, x: Int, y: Int, width: Int): TextBlock = withContext(dispatcher) {
        require(width > 0) { "El ancho debe ser positivo: $width" }
        ctx.db.transactionWithResult {
            requireNotNull(queries.selectPageById(pageId.value).executeAsOneOrNull()) { "No existe la página: $pageId" }
            val hlc = ctx.nextHlc()
            val block = TextBlock(
                meta = Meta.created(ctx.ids.newId(), hlc, ctx.authorId),
                pageId = pageId, x = x, y = y, width = width, content = "", contentHlc = hlc,
            )
            save(block, hlc, ChangeOp.UPSERT)
            block
        }
    }

    override suspend fun updateContent(id: EntityId, html: String) {
        change(id) { block, hlc ->
            block.copy(meta = block.meta.updated(hlc, ctx.deviceId), content = html, contentHlc = hlc)
        }
    }

    override suspend fun moveResize(id: EntityId, x: Int, y: Int, width: Int) {
        require(width > 0) { "El ancho debe ser positivo: $width" }
        change(id) { block, hlc ->
            block.copy(meta = block.meta.updated(hlc, ctx.deviceId), x = x, y = y, width = width)
        }
    }

    override suspend fun delete(id: EntityId) {
        withContext(dispatcher) {
            ctx.db.transaction {
                val current = find(id)
                val hlc = ctx.nextHlc()
                save(current.copy(meta = current.meta.deleted(hlc, ctx.deviceId)), hlc, ChangeOp.DELETE)
            }
        }
    }

    private suspend fun change(id: EntityId, edit: (TextBlock, Hlc) -> TextBlock) {
        withContext(dispatcher) {
            ctx.db.transaction {
                val current = find(id)
                val hlc = ctx.nextHlc()
                save(edit(current, hlc), hlc, ChangeOp.UPSERT)
            }
        }
    }

    private fun find(id: EntityId): TextBlock =
        queries.selectTextBlockById(id.value).executeAsOneOrNull()?.toModel()
            ?: throw IllegalArgumentException("No existe el bloque de texto: $id")

    /** Escribe la fila y su registro en `change_log`. Siempre dentro de una transacción. */
    private fun save(b: TextBlock, hlc: Hlc, op: ChangeOp) {
        queries.upsertTextBlock(
            id = b.meta.id.value,
            page_id = b.pageId.value,
            x = b.x.toLong(),
            y = b.y.toLong(),
            width = b.width.toLong(),
            content = b.content,
            content_hlc = b.contentHlc.toText(),
            created_hlc = b.meta.createdHlc.toText(),
            updated_hlc = b.meta.updatedHlc.toText(),
            device_id = b.meta.deviceId.value,
            author_id = b.meta.authorId.value,
            deleted_hlc = b.meta.deletedHlc?.toText(),
        )
        ctx.logChange("text_block", b.meta.id, op, hlc)
    }

    private fun TextBlockRow.toModel() = TextBlock(
        meta = Meta(
            id = EntityId.parse(id),
            createdHlc = Hlc.parse(created_hlc),
            updatedHlc = Hlc.parse(updated_hlc),
            deviceId = EntityId.parse(device_id),
            authorId = EntityId.parse(author_id),
            deletedHlc = deleted_hlc?.let(Hlc::parse),
        ),
        pageId = EntityId.parse(page_id),
        x = x.toInt(),
        y = y.toInt(),
        width = width.toInt(),
        content = content,
        contentHlc = Hlc.parse(content_hlc),
    )
}
