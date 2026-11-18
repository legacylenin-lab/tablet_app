package app.cuaderno.testing

import app.cuaderno.domain.AddDocumentResult
import app.cuaderno.domain.DocumentRepository
import app.cuaderno.domain.ImportResult
import app.cuaderno.domain.NewDocument
import app.cuaderno.domain.PlatformFile
import app.cuaderno.model.Document
import app.cuaderno.model.EntityId
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Documento de prueba, sin pasar por la base de datos. */
fun testDocument(n: Int, title: String = "Documento $n", pages: Int = 10): Document {
    val hlc = Hlc(wallMillis = n.toLong(), counter = 0, deviceId = testId(0xD))
    return Document(Meta.created(testId(n), hlc, authorId = testId(0xA)), title, "hash$n", pages, sizeBytes = 1000)
}

/** Archivo elegido por el usuario, para pruebas. */
class TestFile(override val name: String) : PlatformFile

/**
 * Biblioteca en memoria. [nextResult] decide qué devuelve la próxima importación (por defecto, la
 * agrega); [failWith] la hace fallar; con [gate] la importación espera hasta que se complete.
 */
class FakeDocumentRepository(initial: List<Document> = emptyList()) : DocumentRepository {
    val items = MutableStateFlow(initial)
    var nextResult: ImportResult? = null
    var failWith: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    val imported = mutableListOf<String>()
    private var nextId = 300

    override fun observeAll(): Flow<List<Document>> = items.map { list -> list.filter { !it.meta.isDeleted } }

    override suspend fun import(source: PlatformFile): ImportResult {
        imported += source.name
        gate?.await()
        failWith?.let { throw it }
        nextResult?.let { return it }
        val document = testDocument(nextId++, title = source.name.removeSuffix(".pdf"))
        items.value = items.value + document
        return ImportResult.Imported(document)
    }

    override suspend fun add(newDocument: NewDocument): AddDocumentResult = error("no se usa")

    /** Páginas guardadas con [setLastReadPage], en orden. */
    val savedPages = mutableListOf<Int>()

    override suspend fun setLastReadPage(id: EntityId, page: Int) {
        savedPages += page
    }

    override suspend fun delete(id: EntityId) = Unit
}
