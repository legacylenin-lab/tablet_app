package app.cuaderno.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.model.EntityId
import app.cuaderno.model.PageTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SqlPageRepositoryTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CuadernoDatabase.Schema.create(it) }
    private val db = CuadernoDatabase(driver)
    private var now = 1_000L
    private val ids = SequentialIdGenerator()

    private class Repos(val notebooks: SqlNotebookRepository, val pages: SqlPageRepository)

    private fun TestScope.repos(): Repos {
        val ctx = DataContext(db, deviceId = testId(1), authorId = testId(2), ids = ids, nowMillis = { now++ })
        val dispatcher = StandardTestDispatcher(testScheduler)
        return Repos(SqlNotebookRepository(ctx, dispatcher), SqlPageRepository(ctx, dispatcher))
    }

    private fun changes() = db.cuadernoQueries.selectChanges().executeAsList()

    private fun addStroke(pageId: EntityId, byte: Byte) {
        db.cuadernoQueries.upsertStroke(
            testId(5000 + byte).value, pageId.value, "PEN", 0xFF000000, 2.5, byteArrayOf(byte),
            "001-0000-x", "001-0000-x", testId(1).value, testId(2).value, null,
        )
    }

    private fun addText(pageId: EntityId, text: String) {
        db.cuadernoQueries.upsertTextBlock(
            testId(6000 + text.length).value, pageId.value, 100, 200, 800, text, "001-0000-x",
            "001-0000-x", "001-0000-x", testId(1).value, testId(2).value, null,
        )
    }

    @Test
    fun agregarSinReferenciaPoneLaPaginaAlFinal() = runTest {
        val r = repos()
        val cuaderno = r.notebooks.create("Cálculo")
        val a = r.pages.add(cuaderno.meta.id, PageTemplate.BLANK, after = null)
        val b = r.pages.add(cuaderno.meta.id, PageTemplate.GRID, after = null)
        val c = r.pages.add(cuaderno.meta.id, PageTemplate.LINED, after = null)
        val lista = r.pages.observeByNotebook(cuaderno.meta.id).first()
        assertEquals(listOf(a.meta.id, b.meta.id, c.meta.id), lista.map { it.meta.id })
        assertEquals(listOf(PageTemplate.BLANK, PageTemplate.GRID, PageTemplate.LINED), lista.map { it.template })
        assertEquals(210, lista[0].widthMm)
        assertEquals(297, lista[0].heightMm)
    }

    @Test
    fun agregarDespuesDeUnaPaginaLaInsertaEnMedio() = runTest {
        val r = repos()
        val n = r.notebooks.create("N").meta.id
        val a = r.pages.add(n, PageTemplate.BLANK, null)
        val b = r.pages.add(n, PageTemplate.BLANK, null)
        val medio = r.pages.add(n, PageTemplate.GRID, after = a.meta.id)
        assertEquals(
            listOf(a.meta.id, medio.meta.id, b.meta.id),
            r.pages.observeByNotebook(n).first().map { it.meta.id },
        )
    }

    @Test
    fun moverReordenaYSoloTocaLaPaginaMovida() = runTest {
        val r = repos()
        val n = r.notebooks.create("N").meta.id
        val a = r.pages.add(n, PageTemplate.BLANK, null)
        val b = r.pages.add(n, PageTemplate.BLANK, null)
        val c = r.pages.add(n, PageTemplate.BLANK, null)
        r.pages.move(c.meta.id, after = null)
        assertEquals(listOf(c, a, b).map { it.meta.id }, r.pages.observeByNotebook(n).first().map { it.meta.id })
        r.pages.move(c.meta.id, after = a.meta.id)
        assertEquals(listOf(a, c, b).map { it.meta.id }, r.pages.observeByNotebook(n).first().map { it.meta.id })
        val porId = r.pages.observeByNotebook(n).first().associateBy { it.meta.id }
        assertEquals(a.sortKey, porId.getValue(a.meta.id).sortKey)
        assertEquals(b.sortKey, porId.getValue(b.meta.id).sortKey)
    }

    @Test
    fun borrarUnaPaginaLaOcultaPeroConservaLaFila() = runTest {
        val r = repos()
        val n = r.notebooks.create("N").meta.id
        val a = r.pages.add(n, PageTemplate.BLANK, null)
        val b = r.pages.add(n, PageTemplate.BLANK, null)
        r.pages.delete(a.meta.id)
        assertEquals(listOf(b.meta.id), r.pages.observeByNotebook(n).first().map { it.meta.id })
        val filas = driver.executeQuery(null, "SELECT COUNT(*) FROM page", {
            it.next()
            app.cash.sqldelight.db.QueryResult.Value(it.getLong(0))
        }, 0).value
        assertEquals(2L, filas)
    }

    @Test
    fun borrarUnCuadernoOcultaSusPaginas() = runTest {
        val r = repos()
        val n = r.notebooks.create("N")
        val otro = r.notebooks.create("Otro")
        r.pages.add(n.meta.id, PageTemplate.BLANK, null)
        val paginaOtro = r.pages.add(otro.meta.id, PageTemplate.BLANK, null)
        r.notebooks.delete(n.meta.id)
        assertEquals(emptyList(), r.pages.observeByNotebook(n.meta.id).first())
        // El otro cuaderno no se ve afectado.
        assertEquals(listOf(paginaOtro.meta.id), r.pages.observeByNotebook(otro.meta.id).first().map { it.meta.id })
    }

    @Test
    fun duplicarCopiaLaPaginaConSuContenidoJustoDespues() = runTest {
        val r = repos()
        val n = r.notebooks.create("N").meta.id
        val a = r.pages.add(n, PageTemplate.GRID, null)
        val b = r.pages.add(n, PageTemplate.BLANK, null)
        addStroke(a.meta.id, 1)
        addStroke(a.meta.id, 2)
        addText(a.meta.id, "hola")

        val copia = r.pages.duplicate(a.meta.id)

        assertEquals(listOf(a.meta.id, copia.meta.id, b.meta.id), r.pages.observeByNotebook(n).first().map { it.meta.id })
        assertNotEquals(a.meta.id, copia.meta.id)
        assertEquals(PageTemplate.GRID, copia.template)
        val trazos = db.cuadernoQueries.selectStrokesByPage(copia.meta.id.value).executeAsList()
        assertEquals(2, trazos.size)
        assertContentEquals(listOf<Byte>(1, 2), trazos.map { it.points[0] })
        val textos = db.cuadernoQueries.selectTextBlocksByPage(copia.meta.id.value).executeAsList()
        assertEquals(listOf("hola"), textos.map { it.content })
        // La original conserva su contenido, y las copias tienen identificadores propios.
        assertEquals(2, db.cuadernoQueries.selectStrokesByPage(a.meta.id.value).executeAsList().size)
        val todos = trazos.map { it.id } + db.cuadernoQueries.selectStrokesByPage(a.meta.id.value).executeAsList().map { it.id }
        assertEquals(4, todos.toSet().size)
    }

    @Test
    fun duplicarRegistraTodoEnElRegistroDeCambios() = runTest {
        val r = repos()
        val n = r.notebooks.create("N").meta.id
        val a = r.pages.add(n, PageTemplate.BLANK, null)
        addStroke(a.meta.id, 1)
        val antes = changes().size
        r.pages.duplicate(a.meta.id)
        val nuevos = changes().drop(antes)
        assertEquals(listOf("page", "stroke"), nuevos.map { it.entity })
        assertEquals(2, nuevos.map { it.hlc }.toSet().size)
    }

    @Test
    fun cadaOperacionDejaSuFilaEnElRegistro() = runTest {
        val r = repos()
        val n = r.notebooks.create("N").meta.id
        val a = r.pages.add(n, PageTemplate.BLANK, null)
        r.pages.move(a.meta.id, null)
        r.pages.delete(a.meta.id)
        val paginas = changes().filter { it.entity == "page" }
        assertEquals(listOf("UPSERT", "UPSERT", "DELETE"), paginas.map { it.op })
    }

    @Test
    fun operacionesInvalidasFallanSinDejarRastro() = runTest {
        val r = repos()
        val n = r.notebooks.create("N").meta.id
        val a = r.pages.add(n, PageTemplate.BLANK, null)
        val antes = changes().size
        assertFailsWith<IllegalArgumentException> { r.pages.add(unknownId, PageTemplate.BLANK, null) }
        assertFailsWith<IllegalArgumentException> { r.pages.add(n, PageTemplate.BLANK, after = unknownId) }
        assertFailsWith<IllegalArgumentException> { r.pages.move(a.meta.id, after = unknownId) }
        assertFailsWith<IllegalArgumentException> { r.pages.duplicate(unknownId) }
        assertFailsWith<IllegalArgumentException> { r.pages.delete(unknownId) }
        assertEquals(antes, changes().size)
    }

    @Test
    fun noSePuedeAgregarAUnCuadernoBorrado() = runTest {
        val r = repos()
        val n = r.notebooks.create("N")
        r.notebooks.delete(n.meta.id)
        assertFailsWith<IllegalArgumentException> { r.pages.add(n.meta.id, PageTemplate.BLANK, null) }
    }

    @Test
    fun milPaginasSeguidasMantienenElOrden() = runTest {
        val r = repos()
        val n = r.notebooks.create("N").meta.id
        val ids = List(200) { r.pages.add(n, PageTemplate.BLANK, null).meta.id }
        assertEquals(ids, r.pages.observeByNotebook(n).first().map { it.meta.id })
        assertTrue(ids.toSet().size == 200)
    }
}
