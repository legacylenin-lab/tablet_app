package app.cuaderno.domain

import app.cuaderno.model.EntityId
import app.cuaderno.model.Annotation
import app.cuaderno.model.AnnotationKind
import app.cuaderno.model.Document
import kotlinx.coroutines.flow.Flow

/** Datos de un PDF recién importado. El archivo se guarda aparte, con su hash como nombre. */
class NewDocument(
    val title: String,
    /** SHA-256 del archivo, en hexadecimal. Identifica el contenido: el mismo PDF no se duplica. */
    val fileHash: String,
    val pageCount: Int,
    val sizeBytes: Long,
)

sealed interface AddDocumentResult {
    /** Documento nuevo (o uno borrado antes que se vuelve a importar y reaparece). */
    data class Added(val document: Document) : AddDocumentResult

    /** Ya estaba en la biblioteca: no se duplica (CS-13). */
    data class AlreadyExists(val document: Document) : AddDocumentResult
}

/**
 * Contrato de la biblioteca (docs/arquitectura.md §7). Copiar el archivo y leer el PDF son tareas
 * de plataforma ([DocumentFileStore], [PdfEngine]); el repositorio las usa para importar.
 */
interface DocumentRepository {
    fun observeAll(): Flow<List<Document>>

    /**
     * Importa un archivo que eligió el usuario (RF-10): lo copia a la carpeta privada, comprueba
     * que sea un PDF que se pueda abrir, cuenta sus páginas y lo registra. Si algo falla, no deja
     * restos. El título es el nombre del archivo sin `.pdf`.
     */
    suspend fun import(source: PlatformFile): ImportResult

    /** Registra un PDF ya copiado. Lo usa [import]; también sirve en pruebas. */
    suspend fun add(newDocument: NewDocument): AddDocumentResult

    /** Recuerda la página de lectura (0 hasta `pageCount - 1`). */
    suspend fun setLastReadPage(id: EntityId, page: Int)

    suspend fun delete(id: EntityId)
}

/** Datos de una anotación nueva; [geometry] va codificada (§5.1). */
class NewAnnotation(
    val kind: AnnotationKind,
    val colorArgb: Int,
    val geometry: ByteArray,
)

/** Anotaciones sobre PDF: igual que la tinta, pero por (documento, página). Inmutables, borrado suave. */
interface AnnotationRepository {
    fun observe(documentId: EntityId, pageIndex: Int): Flow<List<Annotation>>

    suspend fun add(documentId: EntityId, pageIndex: Int, annotation: NewAnnotation): Annotation

    /** Borrado suave. Repetirlo no hace nada; un id inexistente falla. */
    suspend fun erase(ids: List<EntityId>)

    /** Deshace un borrado. Un id inexistente falla. */
    suspend fun restore(ids: List<EntityId>)
}
