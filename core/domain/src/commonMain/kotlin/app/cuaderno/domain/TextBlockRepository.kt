package app.cuaderno.domain

import app.cuaderno.model.EntityId
import app.cuaderno.model.TextBlock
import kotlinx.coroutines.flow.Flow

/** Contrato de los bloques de texto (docs/arquitectura.md §7). Los borrados son suaves. */
interface TextBlockRepository {
    fun observeByPage(pageId: EntityId): Flow<List<TextBlock>>

    suspend fun create(pageId: EntityId, x: Int, y: Int, width: Int): TextBlock

    /** Cambia el contenido (HTML limitado, §5.4). Actualiza también `contentHlc`, que decide los conflictos. */
    suspend fun updateContent(id: EntityId, html: String)

    /** Mueve o cambia el ancho. No toca `contentHlc`: mover no cuenta como editar el texto. */
    suspend fun moveResize(id: EntityId, x: Int, y: Int, width: Int)

    suspend fun delete(id: EntityId)
}
