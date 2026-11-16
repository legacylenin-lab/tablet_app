package app.cuaderno.domain

import app.cuaderno.model.Notebook
import kotlinx.coroutines.flow.Flow

/** Contrato de los cuadernos (docs/arquitectura.md §7). Los borrados son suaves. */
interface NotebookRepository {
    /** Cuadernos no borrados, en el orden de `sortKey`. Se vuelve a emitir cuando cambian. */
    fun observeAll(): Flow<List<Notebook>>

    suspend fun create(title: String): Notebook

    suspend fun rename(id: String, title: String)

    /** Mueve el cuaderno justo después de [after]; con `null`, al principio. Recalcula `sortKey`. */
    suspend fun move(id: String, after: String?)

    suspend fun delete(id: String)
}
