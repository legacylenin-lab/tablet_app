package app.cuaderno.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cuaderno.domain.InkRepository
import app.cuaderno.domain.NewStroke
import app.cuaderno.model.EntityId
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.Stroke
import app.cuaderno.model.StrokeTool
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.cuaderno.data.db.Stroke as StrokeRow

class SqlInkRepository(
    private val ctx: DataContext,
    private val dispatcher: CoroutineDispatcher,
) : InkRepository {

    private val queries get() = ctx.db.cuadernoQueries

    override fun observeStrokes(pageId: EntityId): Flow<List<Stroke>> =
        queries.selectStrokesByPage(pageId.value).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun add(pageId: EntityId, stroke: NewStroke): Stroke = withContext(dispatcher) {
        ctx.db.transactionWithResult {
            requireNotNull(queries.selectPageById(pageId.value).executeAsOneOrNull()) { "No existe la página: $pageId" }
            val hlc = ctx.nextHlc()
            val created = Stroke(
                meta = Meta.created(ctx.ids.newId(), hlc, ctx.authorId),
                pageId = pageId,
                tool = stroke.tool,
                colorArgb = stroke.colorArgb,
                baseWidth = stroke.baseWidth,
                points = stroke.points.copyOf(),
            )
            save(created, hlc, ChangeOp.UPSERT)
            created
        }
    }

    override suspend fun erase(ids: List<EntityId>) {
        withContext(dispatcher) {
            ctx.db.transaction {
                for (id in ids) {
                    val current = find(id)
                    if (current.meta.isDeleted) continue
                    val hlc = ctx.nextHlc()
                    save(current.withMeta(current.meta.deleted(hlc, ctx.deviceId)), hlc, ChangeOp.DELETE)
                }
            }
        }
    }

    override suspend fun restore(ids: List<EntityId>) {
        withContext(dispatcher) {
            ctx.db.transaction {
                for (id in ids) {
                    val current = find(id)
                    if (!current.meta.isDeleted) continue
                    val hlc = ctx.nextHlc()
                    val meta = current.meta.updated(hlc, ctx.deviceId).copy(deletedHlc = null)
                    save(current.withMeta(meta), hlc, ChangeOp.UPSERT)
                }
            }
        }
    }

    private fun find(id: EntityId): Stroke =
        queries.selectStrokeAnyById(id.value).executeAsOneOrNull()?.toModel()
            ?: throw IllegalArgumentException("No existe el trazo: $id")

    private fun Stroke.withMeta(meta: Meta) = Stroke(meta, pageId, tool, colorArgb, baseWidth, points)

    /** Escribe la fila y su registro en `change_log`. Siempre dentro de una transacción. */
    private fun save(s: Stroke, hlc: Hlc, op: ChangeOp) {
        queries.upsertStroke(
            id = s.meta.id.value,
            page_id = s.pageId.value,
            tool = s.tool.name,
            color = s.colorArgb.toLong(),
            base_width = s.baseWidth.toDouble(),
            points = s.points,
            created_hlc = s.meta.createdHlc.toText(),
            updated_hlc = s.meta.updatedHlc.toText(),
            device_id = s.meta.deviceId.value,
            author_id = s.meta.authorId.value,
            deleted_hlc = s.meta.deletedHlc?.toText(),
        )
        ctx.logChange("stroke", s.meta.id, op, hlc)
    }

    private fun StrokeRow.toModel() = Stroke(
        meta = Meta(
            id = EntityId.parse(id),
            createdHlc = Hlc.parse(created_hlc),
            updatedHlc = Hlc.parse(updated_hlc),
            deviceId = EntityId.parse(device_id),
            authorId = EntityId.parse(author_id),
            deletedHlc = deleted_hlc?.let(Hlc::parse),
        ),
        pageId = EntityId.parse(page_id),
        tool = StrokeTool.valueOf(tool),
        colorArgb = color.toInt(),
        baseWidth = base_width.toFloat(),
        points = points,
    )
}
