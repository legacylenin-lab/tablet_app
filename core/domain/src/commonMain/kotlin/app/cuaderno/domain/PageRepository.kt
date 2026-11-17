package app.cuaderno.domain

import app.cuaderno.model.EntityId
import app.cuaderno.model.Page
import app.cuaderno.model.PageTemplate
import kotlinx.coroutines.flow.Flow

/** Contrato de las páginas (docs/arquitectura.md §7). Los borrados son suaves. */
interface PageRepository {
    /** Páginas visibles del cuaderno, en orden. Si el cuaderno está borrado, la lista queda vacía. */
    fun observeByNotebook(notebookId: EntityId): Flow<List<Page>>

    /** Agrega una página justo después de [after]; con `null`, al final del cuaderno. */
    suspend fun add(notebookId: EntityId, template: PageTemplate, after: EntityId?): Page

    /** Copia la página con todo su contenido (trazos y texto), justo después de la original. */
    suspend fun duplicate(id: EntityId): Page

    /** Mueve la página justo después de [after]; con `null`, al principio. */
    suspend fun move(id: EntityId, after: EntityId?)

    suspend fun delete(id: EntityId)
}
