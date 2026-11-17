package app.cuaderno.domain

import app.cuaderno.model.EntityId
import app.cuaderno.model.Notebook
import kotlinx.coroutines.flow.Flow

/** Contrato de los cuadernos (docs/arquitectura.md §7). Los borrados son suaves. */
interface NotebookRepository {
    /** Cuadernos no borrados, en el orden de `sortKey`. Se vuelve a emitir cuando cambian. */
    fun observeAll(): Flow<List<Notebook>>

    /** Cuadernos no borrados en orden alfabético por título, como se lee en español ("Álgebra" antes que "Zebra"). */
    fun observeAllByTitle(): Flow<List<Notebook>>

    suspend fun create(title: String): Notebook

    suspend fun rename(id: EntityId, title: String)

    /** Mueve el cuaderno justo después de [after]; con `null`, al principio. Recalcula `sortKey`. */
    suspend fun move(id: EntityId, after: EntityId?)

    suspend fun delete(id: EntityId)
}
