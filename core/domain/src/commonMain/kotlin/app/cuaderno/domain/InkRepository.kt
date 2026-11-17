package app.cuaderno.domain

import app.cuaderno.model.EntityId
import app.cuaderno.model.Stroke
import app.cuaderno.model.StrokeTool
import kotlinx.coroutines.flow.Flow

/** Datos de un trazo recién dibujado; el repositorio le pone identificador y marcas. */
class NewStroke(
    val tool: StrokeTool,
    val colorArgb: Int,
    val baseWidth: Float,
    /** Puntos ya codificados con `StrokeCodec`. */
    val points: ByteArray,
)

/** Contrato de la tinta (docs/arquitectura.md §7). Los trazos son inmutables: borrar es suave. */
interface InkRepository {
    /** Trazos visibles de la página, en el orden en que se dibujaron. */
    fun observeStrokes(pageId: EntityId): Flow<List<Stroke>>

    suspend fun add(pageId: EntityId, stroke: NewStroke): Stroke

    /** Borrado suave (borrador). Borrar algo ya borrado no hace nada; un id inexistente falla. */
    suspend fun erase(ids: List<EntityId>)

    /** Deshace un borrado. Restaurar algo no borrado no hace nada; un id inexistente falla. */
    suspend fun restore(ids: List<EntityId>)
}
