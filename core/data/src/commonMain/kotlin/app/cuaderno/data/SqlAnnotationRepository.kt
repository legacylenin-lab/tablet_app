package app.cuaderno.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cuaderno.domain.AnnotationRepository
import app.cuaderno.domain.NewAnnotation
import app.cuaderno.model.Annotation
import app.cuaderno.model.AnnotationKind
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.newId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.cuaderno.data.db.Annotation_ as AnnotationRow

class SqlAnnotationRepository(
    private val ctx: DataContext,
    private val dispatcher: CoroutineDispatcher,
) : AnnotationRepository {

    private val queries get() = ctx.db.cuadernoQueries

    override fun observe(documentId: String, pageIndex: Int): Flow<List<Annotation>> =
        queries.selectAnnotationsByDocPage(documentId, pageIndex.toLong()).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun add(documentId: String, pageIndex: Int, annotation: NewAnnotation): Annotation =
        withContext(dispatcher) {
            ctx.db.transactionWithResult {
                val document = requireNotNull(queries.selectDocumentById(documentId).executeAsOneOrNull()) {
                    "No existe el documento: $documentId"
                }
                require(pageIndex in 0 until maxOf(document.page_count.toInt(), 1)) {
                    "Página fuera de rango: $pageIndex (el documento tiene ${document.page_count})"
                }
                val hlc = ctx.nextHlc()
                val created = Annotation(
                    meta = Meta.created(newId(), hlc, ctx.authorId),
                    documentId = documentId,
                    pageIndex = pageIndex,
                    kind = annotation.kind,
                    colorArgb = annotation.colorArgb,
                    geometry = annotation.geometry.copyOf(),
                )
                save(created, hlc, ChangeOp.UPSERT)
                created
            }
        }

    override suspend fun erase(ids: List<String>) {
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

    override suspend fun restore(ids: List<String>) {
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

    private fun find(id: String): Annotation =
        queries.selectAnnotationAnyById(id).executeAsOneOrNull()?.toModel()
            ?: throw IllegalArgumentException("No existe la anotación: $id")

    private fun Annotation.withMeta(meta: Meta) = Annotation(meta, documentId, pageIndex, kind, colorArgb, geometry)

    /** Escribe la fila y su registro en `change_log`. Siempre dentro de una transacción. */
    private fun save(a: Annotation, hlc: Hlc, op: ChangeOp) {
        queries.upsertAnnotation(
            id = a.meta.id,
            document_id = a.documentId,
            page_index = a.pageIndex.toLong(),
            kind = a.kind.name,
            color = a.colorArgb.toLong(),
            geometry = a.geometry,
            created_hlc = a.meta.createdHlc.toText(),
            updated_hlc = a.meta.updatedHlc.toText(),
            device_id = a.meta.deviceId,
            author_id = a.meta.authorId,
            deleted_hlc = a.meta.deletedHlc?.toText(),
        )
        ctx.logChange("annotation", a.meta.id, op, hlc)
    }

    private fun AnnotationRow.toModel() = Annotation(
        meta = Meta(
            id = id,
            createdHlc = Hlc.parse(created_hlc),
            updatedHlc = Hlc.parse(updated_hlc),
            deviceId = device_id,
            authorId = author_id,
            deletedHlc = deleted_hlc?.let(Hlc::parse),
        ),
        documentId = document_id,
        pageIndex = page_index.toInt(),
        kind = AnnotationKind.valueOf(kind),
        colorArgb = color.toInt(),
        geometry = geometry,
    )
}
