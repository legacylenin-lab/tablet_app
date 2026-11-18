package app.cuaderno.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.domain.ImportResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Importar PDF a la biblioteca (RF-10, RF-11, CS-13; docs/arquitectura.md §5.2). */
class DocumentImportTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CuadernoDatabase.Schema.create(it) }
    private val db = CuadernoDatabase(driver)
    private val root: File = Files.createTempDirectory("import-test").toFile()
    private val documentsDir = File(root, "documents")
    private val engine = FakePdfEngine(pages = 12)
    private var now = 1_000L

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun TestScope.repo(open: (BytesFile) -> java.io.InputStream = { it.bytes.inputStream() }): SqlDocumentRepository {
        val ctx = DataContext(db, deviceId = testId(1), authorId = testId(2), ids = SequentialIdGenerator(), nowMillis = { now++ })
        val d = StandardTestDispatcher(testScheduler)
        val files = FileDocumentStore(documentsDir, d) { open(it as BytesFile) }
        return SqlDocumentRepository(ctx, d, files, engine)
    }

    private fun pdfBytes(text: String = "contenido") = "%PDF-1.7\n$text\n%%EOF".encodeToByteArray()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun storedFiles() = documentsDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()

    @Test
    fun importarCopiaElPdfConSuHashYLoRegistra() = runTest {
        val r = repo()
        val bytes = pdfBytes()
        val result = assertIs<ImportResult.Imported>(r.import(BytesFile("Cálculo I - Tema 3.pdf", bytes)))

        val hash = sha256(bytes)
        assertEquals("Cálculo I - Tema 3", result.document.title)
        assertEquals(hash, result.document.fileHash)
        assertEquals(12, result.document.pageCount)
        assertEquals(bytes.size.toLong(), result.document.sizeBytes)
        assertEquals(listOf("$hash.pdf"), storedFiles())
        assertContentEquals(bytes, File(documentsDir, "$hash.pdf").readBytes())
        assertEquals(listOf(result.document), r.observeAll().first())
    }

    @Test
    fun elMismoPdfDosVecesNoSeDuplica() = runTest {
        val r = repo()
        val first = assertIs<ImportResult.Imported>(r.import(BytesFile("a.pdf", pdfBytes())))
        val again = assertIs<ImportResult.AlreadyExists>(r.import(BytesFile("otro nombre.pdf", pdfBytes())))
        assertEquals(first.document.meta.id, again.document.meta.id)
        assertEquals(1, storedFiles().size)
        assertEquals(1, r.observeAll().first().size)
    }

    @Test
    fun unPdfBorradoVuelveAlImportarloDeNuevo() = runTest {
        val r = repo()
        val first = assertIs<ImportResult.Imported>(r.import(BytesFile("a.pdf", pdfBytes())))
        r.delete(first.document.meta.id)
        val back = assertIs<ImportResult.Imported>(r.import(BytesFile("a.pdf", pdfBytes())))
        assertEquals(first.document.meta.id, back.document.meta.id)
        assertEquals(1, storedFiles().size)
    }

    @Test
    fun unWordNoSeImportaYPideConvertirlo() = runTest {
        val r = repo()
        assertEquals(ImportResult.NotPdf(isWord = true), r.import(BytesFile("Informe.docx", "PK...".encodeToByteArray())))
        assertEquals(emptyList(), storedFiles())
        assertTrue(engine.asked.isEmpty())
    }

    @Test
    fun unArchivoQueNoEsPdfNoDejaRestos() = runTest {
        val r = repo()
        assertEquals(ImportResult.NotPdf(isWord = false), r.import(BytesFile("foto.pdf", "\u0089PNG....".encodeToByteArray())))
        assertEquals(emptyList(), storedFiles())
        assertTrue(r.observeAll().first().isEmpty())
    }

    @Test
    fun unPdfDanadoNoDejaRestos() = runTest {
        engine.failWith = IOException("archivo dañado")
        val r = repo()
        assertEquals(ImportResult.Corrupt, r.import(BytesFile("roto.pdf", pdfBytes())))
        assertEquals(emptyList(), storedFiles())
        assertTrue(r.observeAll().first().isEmpty())
    }

    @Test
    fun unPdfSinPaginasSeConsideraDanado() = runTest {
        engine.pages = 0
        assertEquals(ImportResult.Corrupt, repo().import(BytesFile("vacio.pdf", pdfBytes())))
        assertEquals(emptyList(), storedFiles())
    }

    @Test
    fun siFallaLaLecturaDelArchivoNoQuedaNadaAMedias() = runTest {
        val r = repo { throw IOException("sin permiso") }
        assertFailsWith<IOException> { r.import(BytesFile("a.pdf", pdfBytes())) }
        assertEquals(emptyList(), storedFiles())
    }

    @Test
    fun sePermiteAlgoDeBasuraAntesDelEncabezadoPdf() = runTest {
        val bytes = "\r\n  ".encodeToByteArray() + pdfBytes()
        assertIs<ImportResult.Imported>(repo().import(BytesFile("a.pdf", bytes)))
    }

    @Test
    fun unPdfGrandeSeCopiaCompleto() = runTest {
        val big = pdfBytes() + ByteArray(300_000) { (it % 251).toByte() }
        val result = assertIs<ImportResult.Imported>(repo().import(BytesFile("grande.pdf", big)))
        assertEquals(big.size.toLong(), result.document.sizeBytes)
        assertContentEquals(big, File(documentsDir, "${sha256(big)}.pdf").readBytes())
    }

    @Test
    fun titulosAPartirDelNombreDelArchivo() {
        assertEquals("Apuntes", titleFromFileName("Apuntes.PDF"))
        assertEquals("libro.v2", titleFromFileName("libro.v2.pdf"))
        assertEquals("sin extension", titleFromFileName("sin extension"))
        assertEquals("Documento sin título", titleFromFileName(".pdf"))
        assertTrue(isWordFile("Tarea.DOCX"))
        assertTrue(isWordFile("viejo.doc"))
        assertFalse(isWordFile("documento.pdf"))
    }
}
