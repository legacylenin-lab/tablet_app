package app.cuaderno.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.domain.NewStroke
import app.cuaderno.model.PageTemplate
import app.cuaderno.model.StrokeCodec
import app.cuaderno.model.StrokePoint
import app.cuaderno.model.StrokeTool
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlInkAndTextRepositoryTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CuadernoDatabase.Schema.create(it) }
    private val db = CuadernoDatabase(driver)
    private var now = 1_000L

    private class Repos(
        val notebooks: SqlNotebookRepository,
        val pages: SqlPageRepository,
        val ink: SqlInkRepository,
        val text: SqlTextBlockRepository,
    )

    private fun TestScope.repos(): Repos {
        val ctx = DataContext(db, deviceId = "dev-1", authorId = "yo", nowMillis = { now++ })
        val d = StandardTestDispatcher(testScheduler)
        return Repos(SqlNotebookRepository(ctx, d), SqlPageRepository(ctx, d), SqlInkRepository(ctx, d), SqlTextBlockRepository(ctx, d))
    }

    private fun changes() = db.cuadernoQueries.selectChanges().executeAsList()

    private suspend fun Repos.nuevaPagina(): String {
        val n = notebooks.create("N").meta.id
        return pages.add(n, PageTemplate.BLANK, null).meta.id
    }

    private fun trazo(vararg puntos: StrokePoint, tool: StrokeTool = StrokeTool.PEN) =
        NewStroke(tool, 0xFF112233.toInt(), 2.5f, StrokeCodec.encode(puntos.toList()))

    private val unTrazo = trazo(StrokePoint(100, 200, 50, 0), StrokePoint(110, 210, 80, 8))

    // ---- Tinta ----

    @Test
    fun agregarTrazoLoDejaVisibleConSusDatosIntactos() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val creado = r.ink.add(pagina, unTrazo)
        val leido = r.ink.observeStrokes(pagina).first().single()
        assertEquals(creado, leido)
        assertEquals(StrokeTool.PEN, leido.tool)
        assertEquals(0xFF112233.toInt(), leido.colorArgb)
        assertEquals(2.5f, leido.baseWidth)
        // Los puntos guardados se pueden decodificar y son los mismos.
        assertEquals(
            listOf(StrokePoint(100, 200, 50, 0), StrokePoint(110, 210, 80, 8)),
            StrokeCodec.decode(leido.points),
        )
    }

    @Test
    fun losTrazosSalenEnElOrdenEnQueSeDibujaron() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val ids = List(30) { r.ink.add(pagina, unTrazo).meta.id }
        assertEquals(ids, r.ink.observeStrokes(pagina).first().map { it.meta.id })
    }

    @Test
    fun borrarOcultaYRestaurarDevuelve() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val a = r.ink.add(pagina, unTrazo)
        val b = r.ink.add(pagina, unTrazo)
        r.ink.erase(listOf(a.meta.id))
        assertEquals(listOf(b.meta.id), r.ink.observeStrokes(pagina).first().map { it.meta.id })
        r.ink.restore(listOf(a.meta.id))
        // Vuelve en su sitio original, y los datos no cambiaron.
        val lista = r.ink.observeStrokes(pagina).first()
        assertEquals(listOf(a.meta.id, b.meta.id), lista.map { it.meta.id })
        assertContentEquals(a.points, lista[0].points)
        assertEquals(a.meta.createdHlc, lista[0].meta.createdHlc)
    }

    @Test
    fun borrarVariosATravesDeUnaSolaOperacion() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val ids = List(5) { r.ink.add(pagina, unTrazo).meta.id }
        r.ink.erase(ids.take(3))
        assertEquals(ids.drop(3), r.ink.observeStrokes(pagina).first().map { it.meta.id })
    }

    @Test
    fun borrarOrestaurarEsIdempotenteYNoDejaRegistroDeMas() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val a = r.ink.add(pagina, unTrazo)
        r.ink.restore(listOf(a.meta.id)) // no estaba borrado: no hace nada
        val base = changes().size
        r.ink.erase(listOf(a.meta.id))
        r.ink.erase(listOf(a.meta.id)) // ya estaba borrado: no hace nada
        assertEquals(base + 1, changes().size)
        assertEquals("DELETE", changes().last().op)
    }

    @Test
    fun cadaOperacionSobreTrazosDejaSuFilaEnElRegistro() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val a = r.ink.add(pagina, unTrazo)
        r.ink.erase(listOf(a.meta.id))
        r.ink.restore(listOf(a.meta.id))
        val trazos = changes().filter { it.entity == "stroke" }
        assertEquals(listOf("UPSERT", "DELETE", "UPSERT"), trazos.map { it.op })
    }

    @Test
    fun unIdInexistenteFallaYDeshaceTodaLaOperacion() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val a = r.ink.add(pagina, unTrazo)
        val base = changes().size
        assertFailsWith<IllegalArgumentException> { r.ink.erase(listOf(a.meta.id, "no-existe")) }
        // El primero tampoco se borró: la operación es todo o nada.
        assertEquals(1, r.ink.observeStrokes(pagina).first().size)
        assertEquals(base, changes().size)
        assertFailsWith<IllegalArgumentException> { r.ink.restore(listOf("no-existe")) }
    }

    @Test
    fun noSePuedeDibujarEnUnaPaginaInexistenteOBorrada() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        assertFailsWith<IllegalArgumentException> { r.ink.add("no-existe", unTrazo) }
        r.pages.delete(pagina)
        assertFailsWith<IllegalArgumentException> { r.ink.add(pagina, unTrazo) }
    }

    @Test
    fun losTrazosDeUnaPaginaBorradaOUnCuadernoBorradoSeOcultan() = runTest {
        val r = repos()
        val n = r.notebooks.create("N").meta.id
        val p1 = r.pages.add(n, PageTemplate.BLANK, null).meta.id
        val p2 = r.pages.add(n, PageTemplate.BLANK, null).meta.id
        r.ink.add(p1, unTrazo)
        r.ink.add(p2, unTrazo)
        r.pages.delete(p1)
        assertEquals(0, r.ink.observeStrokes(p1).first().size)
        assertEquals(1, r.ink.observeStrokes(p2).first().size)
        r.notebooks.delete(n)
        assertEquals(0, r.ink.observeStrokes(p2).first().size)
    }

    @Test
    fun elResaltadorYElLapizSeDistinguen() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        r.ink.add(pagina, trazo(StrokePoint(1, 1, 1, 0), tool = StrokeTool.HIGHLIGHTER))
        assertEquals(StrokeTool.HIGHLIGHTER, r.ink.observeStrokes(pagina).first().single().tool)
    }

    // ---- Bloques de texto ----

    @Test
    fun crearUnBloqueDeTextoLoDejaVacioYVisible() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val b = r.text.create(pagina, x = 100, y = 200, width = 800)
        val leido = r.text.observeByPage(pagina).first().single()
        assertEquals(b, leido)
        assertEquals("", leido.content)
        assertEquals(b.meta.createdHlc, leido.contentHlc)
    }

    @Test
    fun editarElTextoActualizaContentHlcPeroMoverNo() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val b = r.text.create(pagina, 100, 200, 800)

        r.text.updateContent(b.meta.id, "<p>Hola</p>")
        val editado = r.text.observeByPage(pagina).first().single()
        assertEquals("<p>Hola</p>", editado.content)
        assertTrue(editado.contentHlc > b.contentHlc)

        r.text.moveResize(b.meta.id, x = 300, y = 400, width = 500)
        val movido = r.text.observeByPage(pagina).first().single()
        assertEquals(Triple(300, 400, 500), Triple(movido.x, movido.y, movido.width))
        assertEquals("<p>Hola</p>", movido.content)
        assertEquals(editado.contentHlc, movido.contentHlc)
        assertTrue(movido.meta.updatedHlc > editado.meta.updatedHlc)
    }

    @Test
    fun borrarUnBloqueDeTextoLoOculta() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val a = r.text.create(pagina, 0, 0, 100)
        val b = r.text.create(pagina, 0, 0, 100)
        r.text.delete(a.meta.id)
        assertEquals(listOf(b.meta.id), r.text.observeByPage(pagina).first().map { it.meta.id })
        assertEquals(listOf("UPSERT", "UPSERT", "DELETE"), changes().filter { it.entity == "text_block" }.map { it.op })
    }

    @Test
    fun elTextoSobreviveAlRoundTripConAcentosYEmojis() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val b = r.text.create(pagina, 0, 0, 100)
        val html = "<h1>Cálculo ∫ ñ</h1><p>é — ✏️</p>"
        r.text.updateContent(b.meta.id, html)
        assertEquals(html, r.text.observeByPage(pagina).first().single().content)
    }

    @Test
    fun operacionesInvalidasSobreTextoFallan() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        val b = r.text.create(pagina, 0, 0, 100)
        val base = changes().size
        assertFailsWith<IllegalArgumentException> { r.text.create("no-existe", 0, 0, 100) }
        assertFailsWith<IllegalArgumentException> { r.text.create(pagina, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { r.text.updateContent("no-existe", "x") }
        assertFailsWith<IllegalArgumentException> { r.text.moveResize(b.meta.id, 0, 0, -5) }
        assertFailsWith<IllegalArgumentException> { r.text.delete("no-existe") }
        assertEquals(base, changes().size)
    }

    @Test
    fun duplicarUnaPaginaCopiaTrazosYTextoUsandoLosRepositorios() = runTest {
        val r = repos()
        val pagina = r.nuevaPagina()
        r.ink.add(pagina, unTrazo)
        val b = r.text.create(pagina, 10, 20, 300)
        r.text.updateContent(b.meta.id, "<p>x</p>")
        val copia = r.pages.duplicate(pagina)
        assertEquals(1, r.ink.observeStrokes(copia.meta.id).first().size)
        assertEquals(listOf("<p>x</p>"), r.text.observeByPage(copia.meta.id).first().map { it.content })
    }
}
