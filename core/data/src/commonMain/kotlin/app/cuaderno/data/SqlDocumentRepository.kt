package app.cuaderno.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cuaderno.domain.AddDocumentResult
import app.cuaderno.domain.DocumentRepository
import app.cuaderno.domain.NewDocument
import app.cuaderno.model.Document
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.newId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.cuaderno.data.db.Document as DocumentRow

class SqlDocumentRepository(
    private val ctx: DataContext,
    private val dispatcher: CoroutineDispatcher,
) : DocumentRepository {

    private val queries get() = ctx.db.cuadernoQueries

    override fun observeAll(): Flow<List<Document>> =
        queries.selectDocuments().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toModel() } }

    override suspend fun add(newDocument: NewDocument): AddDocumentResult = withContext(dispatcher) {
        require(newDocument.pageCount >= 0) { "pageCount inválido: ${newDocument.pageCount}" }
        require(newDocument.sizeBytes >= 0) { "sizeBytes inválido: ${newDocument.sizeBytes}" }
        require(newDocument.fileHash.isNotBlank()) { "Falta el hash del archivo" }
        ctx.db.transactionWithResult {
            val existing = queries.selectDocumentByHash(newDocument.fileHash).executeAsOneOrNull()?.toModel()
            when {
                existing != null && !existing.meta.isDeleted -> AddDocumentResult.AlreadyExists(existing)
                existing != null -> {
                    // Estaba borrado: reaparece con su mismo identificador, para no chocar con el hash único.
                    val hlc = ctx.nextHlc()
                    val back = existing.copy(
                        meta = existing.meta.updated(hlc, ctx.deviceId).copy(deletedHlc = null),
                        title = newDocument.title,
                    )
                    save(back, hlc, ChangeOp.UPSERT)
                    AddDocumentResult.Added(back)
                }
                else -> {
                    val hlc = ctx.nextHlc()
                    val created = Document(
                        meta = Meta.created(newId(), hlc, ctx.authorId),
                        title = newDocument.title,
                        fileHash = newDocument.fileHash,
                        pageCount = newDocument.pageCount,
                        sizeBytes = newDocument.sizeBytes,
                    )
                    save(created, hlc, ChangeOp.UPSERT)
                    AddDocumentResult.Added(created)
                }
            }
        }
    }

    override suspend fun setLastReadPage(id: String, page: Int) {
        withContext(dispatcher) {
            ctx.db.transaction {
                val current = find(id)
                require(page in 0 until maxOf(current.pageCount, 1)) {
                    "Página fuera de rango: $page (el documento tiene ${current.pageCount})"
                }
                if (current.lastReadPage == page) return@transaction
                val hlc = ctx.nextHlc()
                save(current.copy(meta = current.meta.updated(hlc, ctx.deviceId), lastReadPage = page), hlc, ChangeOp.UPSERT)
            }
        }
    }

    override suspend fun delete(id: String) {
        withContext(dispatcher) {
            ctx.db.transaction {
                val current = find(id)
                val hlc = ctx.nextHlc()
                save(current.copy(meta = current.meta.deleted(hlc, ctx.deviceId)), hlc, ChangeOp.DELETE)
            }
        }
    }

    private fun find(id: String): Document =
        queries.selectDocumentById(id).executeAsOneOrNull()?.toModel()
            ?: throw IllegalArgumentException("No existe el documento: $id")

    /** Escribe la fila y su registro en `change_log`. Siempre dentro de una transacción. */
    private fun save(d: Document, hlc: Hlc, op: ChangeOp) {
        queries.upsertDocument(
            id = d.meta.id,
            title = d.title,
            file_hash = d.fileHash,
            page_count = d.pageCount.toLong(),
            last_read_page = d.lastReadPage.toLong(),
            size_bytes = d.sizeBytes,
            created_hlc = d.meta.createdHlc.toText(),
            updated_hlc = d.meta.updatedHlc.toText(),
            device_id = d.meta.deviceId,
            author_id = d.meta.authorId,
            deleted_hlc = d.meta.deletedHlc?.toText(),
        )
        ctx.logChange("document", d.meta.id, op, hlc)
    }

    private fun DocumentRow.toModel() = Document(
        meta = Meta(
            id = id,
            createdHlc = Hlc.parse(created_hlc),
            updatedHlc = Hlc.parse(updated_hlc),
            deviceId = device_id,
            authorId = author_id,
            deletedHlc = deleted_hlc?.let(Hlc::parse),
        ),
        title = title,
        fileHash = file_hash,
        pageCount = page_count.toInt(),
        lastReadPage = last_read_page.toInt(),
        sizeBytes = size_bytes,
    )
}
