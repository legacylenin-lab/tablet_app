package app.cuaderno.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.domain.AddDocumentResult
import app.cuaderno.domain.NewAnnotation
import app.cuaderno.domain.NewDocument
import app.cuaderno.model.AnnotationKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SqlLibraryRepositoryTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CuadernoDatabase.Schema.create(it) }
    private val db = CuadernoDatabase(driver)
    private var now = 1_000L
    private val ids = SequentialIdGenerator()

    private class Repos(val docs: SqlDocumentRepository, val annotations: SqlAnnotationRepository)

    private fun TestScope.repos(): Repos {
        val ctx = DataContext(db, deviceId = testId(1), authorId = testId(2), ids = ids, nowMillis = { now++ })
        val d = StandardTestDispatcher(testScheduler)
        return Repos(SqlDocumentRepository(ctx, d), SqlAnnotationRepository(ctx, d))
    }

    private fun changes() = db.cuadernoQueries.selectChanges().executeAsList()

    private fun pdf(titulo: String = "Apuntes.pdf", hash: String = "aa11", paginas: Int = 10) =
        NewDocument(titulo, hash, paginas, 123_456)

    private suspend fun Repos.nuevoDocumento(hash: String = "aa11", paginas: Int = 10) =
        (docs.add(pdf(hash = hash, paginas = paginas)) as AddDocumentResult.Added).document

    private fun resaltado(byte: Byte = 1) = NewAnnotation(AnnotationKind.HIGHLIGHT, 0xFFFFFF00.toInt(), byteArrayOf(byte))

    // ---- Documentos ----

    @Test
    fun agregarUnDocumentoLoDejaEnLaBiblioteca() = runTest {
        val r = repos()
        val resultado = r.docs.add(pdf())
        val doc = assertIs<AddDocumentResult.Added>(resultado).document
        assertEquals(doc, r.docs.observeAll().first().single())
        assertEquals("aa11", doc.fileHash)
        assertEquals(10, doc.pageCount)
        assertEquals(0, doc.lastReadPage)
    }

    @Test
    fun importarElMismoPdfDosVecesNoLoDuplica() = runTest {
        val r = repos()
        val primero = r.nuevoDocumento()
        val segundo = r.docs.add(pdf(titulo = "Otro nombre.pdf"))
        val existente = assertIs<AddDocumentResult.AlreadyExists>(segundo).document
        assertEquals(primero.meta.id, existente.meta.id)
        assertEquals(1, r.docs.observeAll().first().size)
        // El título original no se pisa.
        assertEquals("Apuntes.pdf", r.docs.observeAll().first().single().title)
    }

    @Test
    fun laBibliotecaSeOrdenaComoEnEspanol() = runTest {
        val r = repos()
        r.docs.add(pdf("zebra.pdf", "h1"))
        r.docs.add(pdf("Álgebra.pdf", "h2"))
        r.docs.add(pdf("cálculo.pdf", "h3"))
        r.docs.add(pdf("Biología.pdf", "h4"))
        r.docs.add(pdf("ñandú.pdf", "h5"))
        r.docs.add(pdf("nube.pdf", "h6"))
        r.docs.add(pdf("oso.pdf", "h7"))
        val titulos = r.docs.observeAll().first().map { it.title }
        assertEquals(
            listOf("Álgebra.pdf", "Biología.pdf", "cálculo.pdf", "nube.pdf", "ñandú.pdf", "oso.pdf", "zebra.pdf"),
            titulos,
        )
    }

    @Test
    fun laClaveDeOrdenDelDocumentoSeGuardaYSeRecalculaAlReaparecer() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento()
        fun claveGuardada() = db.cuadernoQueries.selectDocumentByHash("aa11").executeAsOne().title_sort
        assertEquals("apuntes.pdf", claveGuardada())
        r.docs.delete(doc.meta.id)
        r.docs.add(pdf(titulo = "Éxamen Ñoño.pdf"))
        assertEquals(app.cuaderno.model.TitleSort.of("Éxamen Ñoño.pdf"), claveGuardada())
    }

    @Test
    fun recuerdaLaPaginaDeLectura() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento(paginas = 10)
        r.docs.setLastReadPage(doc.meta.id, 7)
        assertEquals(7, r.docs.observeAll().first().single().lastReadPage)
        r.docs.setLastReadPage(doc.meta.id, 9)
        assertEquals(9, r.docs.observeAll().first().single().lastReadPage)
    }

    @Test
    fun repetirLaMismaPaginaNoEscribeNada() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento()
        r.docs.setLastReadPage(doc.meta.id, 3)
        val base = changes().size
        r.docs.setLastReadPage(doc.meta.id, 3)
        assertEquals(base, changes().size)
    }

    @Test
    fun paginaDeLecturaFueraDeRangoFalla() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento(paginas = 10)
        assertFailsWith<IllegalArgumentException> { r.docs.setLastReadPage(doc.meta.id, 10) }
        assertFailsWith<IllegalArgumentException> { r.docs.setLastReadPage(doc.meta.id, -1) }
        assertEquals(0, r.docs.observeAll().first().single().lastReadPage)
    }

    @Test
    fun borrarOcultaElDocumentoPeroConservaLaFila() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento()
        r.docs.delete(doc.meta.id)
        assertEquals(0, r.docs.observeAll().first().size)
        assertEquals("DELETE", changes().last().op)
    }

    @Test
    fun importarDeNuevoUnDocumentoBorradoLoHaceReaparecerConSuMismoId() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento()
        r.docs.delete(doc.meta.id)
        val resultado = r.docs.add(pdf(titulo = "Nuevo título.pdf"))
        val vuelto = assertIs<AddDocumentResult.Added>(resultado).document
        assertEquals(doc.meta.id, vuelto.meta.id)
        assertEquals(false, vuelto.meta.isDeleted)
        assertEquals(listOf("Nuevo título.pdf"), r.docs.observeAll().first().map { it.title })
    }

    @Test
    fun datosInvalidosDeDocumentoFallan() = runTest {
        val r = repos()
        assertFailsWith<IllegalArgumentException> { r.docs.add(NewDocument("x", "", 1, 1)) }
        assertFailsWith<IllegalArgumentException> { r.docs.add(NewDocument("x", "h", -1, 1)) }
        assertFailsWith<IllegalArgumentException> { r.docs.add(NewDocument("x", "h", 1, -1)) }
        assertFailsWith<IllegalArgumentException> { r.docs.delete(unknownId) }
        assertFailsWith<IllegalArgumentException> { r.docs.setLastReadPage(unknownId, 0) }
        assertEquals(0, changes().size)
    }

    // ---- Anotaciones ----

    @Test
    fun agregarAnotacionYLeerlaPorDocumentoYPagina() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento()
        val a = r.annotations.add(doc.meta.id, 2, resaltado())
        assertEquals(a, r.annotations.observe(doc.meta.id, 2).first().single())
        assertEquals(0, r.annotations.observe(doc.meta.id, 3).first().size)
    }

    @Test
    fun borrarYRestaurarAnotaciones() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento()
        val a = r.annotations.add(doc.meta.id, 0, resaltado(1))
        val b = r.annotations.add(doc.meta.id, 0, resaltado(2))
        r.annotations.erase(listOf(a.meta.id))
        assertEquals(listOf(b.meta.id), r.annotations.observe(doc.meta.id, 0).first().map { it.meta.id })
        r.annotations.restore(listOf(a.meta.id))
        val lista = r.annotations.observe(doc.meta.id, 0).first()
        assertEquals(listOf(a.meta.id, b.meta.id), lista.map { it.meta.id })
        assertContentEquals(a.geometry, lista[0].geometry)
    }

    @Test
    fun borrarUnDocumentoOcultaSusAnotaciones() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento()
        r.annotations.add(doc.meta.id, 0, resaltado())
        r.docs.delete(doc.meta.id)
        assertEquals(0, r.annotations.observe(doc.meta.id, 0).first().size)
        // Al reimportarlo, reaparecen sus anotaciones.
        r.docs.add(pdf())
        assertEquals(1, r.annotations.observe(doc.meta.id, 0).first().size)
    }

    @Test
    fun anotacionesInvalidasFallanSinDejarRastro() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento(paginas = 5)
        val a = r.annotations.add(doc.meta.id, 0, resaltado())
        val base = changes().size
        assertFailsWith<IllegalArgumentException> { r.annotations.add(unknownId, 0, resaltado()) }
        assertFailsWith<IllegalArgumentException> { r.annotations.add(doc.meta.id, 5, resaltado()) }
        assertFailsWith<IllegalArgumentException> { r.annotations.add(doc.meta.id, -1, resaltado()) }
        assertFailsWith<IllegalArgumentException> { r.annotations.erase(listOf(a.meta.id, unknownId)) }
        assertEquals(1, r.annotations.observe(doc.meta.id, 0).first().size)
        assertEquals(base, changes().size)
    }

    @Test
    fun cadaOperacionDeLaBibliotecaDejaSuFilaEnElRegistro() = runTest {
        val r = repos()
        val doc = r.nuevoDocumento()
        val a = r.annotations.add(doc.meta.id, 0, resaltado())
        r.annotations.erase(listOf(a.meta.id))
        r.docs.setLastReadPage(doc.meta.id, 4)
        r.docs.delete(doc.meta.id)
        assertEquals(
            listOf(
                "document" to "UPSERT", "annotation" to "UPSERT", "annotation" to "DELETE",
                "document" to "UPSERT", "document" to "DELETE",
            ),
            changes().map { it.entity to it.op },
        )
        val hlcs = changes().map { it.hlc }
        assertEquals(hlcs.sorted(), hlcs)
        assertTrue(hlcs.toSet().size == hlcs.size)
    }
}
