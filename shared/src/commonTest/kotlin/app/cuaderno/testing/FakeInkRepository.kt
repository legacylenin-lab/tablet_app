package app.cuaderno.testing

import app.cuaderno.domain.InkRepository
import app.cuaderno.domain.NewStroke
import app.cuaderno.model.EntityId
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.Stroke
import app.cuaderno.model.StrokeCodec
import app.cuaderno.model.StrokePoint
import app.cuaderno.model.StrokeTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Puntos codificados de una línea recta horizontal en la altura [y], de [fromX] a [toX]. */
fun linePoints(fromX: Int, toX: Int, y: Int): ByteArray =
    StrokeCodec.encode(listOf(StrokePoint(fromX, y, 128, 0), StrokePoint(toX, y, 128, 10)))

fun newLine(fromX: Int, toX: Int, y: Int, tool: StrokeTool = StrokeTool.PEN) =
    NewStroke(tool, 0xFF000000.toInt(), 4f, linePoints(fromX, toX, y))

/** Trazo guardado de prueba: una línea recta en la altura [y] de la página [pageId]. */
fun savedLine(n: Int, y: Int, pageId: EntityId): Stroke = Stroke(
    Meta.created(testId(n), Hlc(n.toLong(), 0, testId(0xD)), authorId = testId(0xA)),
    pageId, StrokeTool.PEN, 0xFF000000.toInt(), 4f, linePoints(0, 1000, y),
)

/** Repositorio de tinta en memoria. Con [failWith] distinto de `null`, cada escritura falla. */
class FakeInkRepository(initial: List<Stroke> = emptyList()) : InkRepository {
    val items = MutableStateFlow(initial)
    var failWith: Exception? = null
    private var nextId = 900

    /** Ids de los trazos guardados y visibles. */
    val visibleIds: List<EntityId> get() = items.value.filter { !it.meta.isDeleted }.map { it.meta.id }

    override fun observeStrokes(pageId: EntityId): Flow<List<Stroke>> =
        items.map { list -> list.filter { it.pageId == pageId && !it.meta.isDeleted } }

    override suspend fun add(pageId: EntityId, stroke: NewStroke): Stroke {
        failWith?.let { throw it }
        val n = nextId++
        val meta = Meta.created(testId(n), Hlc(n.toLong(), 0, testId(0xD)), authorId = testId(0xA))
        val saved = Stroke(meta, pageId, stroke.tool, stroke.colorArgb, stroke.baseWidth, stroke.points)
        items.value = items.value + saved
        return saved
    }

    override suspend fun erase(ids: List<EntityId>) = setDeleted(ids, deleted = true)

    override suspend fun restore(ids: List<EntityId>) = setDeleted(ids, deleted = false)

    private fun setDeleted(ids: List<EntityId>, deleted: Boolean) {
        failWith?.let { throw it }
        items.value = items.value.map { s ->
            if (s.meta.id in ids) {
                val meta = if (deleted) s.meta.deleted(s.meta.updatedHlc) else s.meta.copy(deletedHlc = null)
                Stroke(meta, s.pageId, s.tool, s.colorArgb, s.baseWidth, s.points)
            } else {
                s
            }
        }
    }
}
