package app.cuaderno.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.domain.EnsureFirstPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EnsureFirstPageTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CuadernoDatabase.Schema.create(it) }
    private val db = CuadernoDatabase(driver)
    private var now = 1_000L
    private val ids = SequentialIdGenerator()

    private class Env(val notebooks: SqlNotebookRepository, val pages: SqlPageRepository, val ensure: EnsureFirstPage)

    private fun TestScope.env(): Env {
        val ctx = DataContext(db, testId(1), testId(2), ids) { now++ }
        val d = StandardTestDispatcher(testScheduler)
        val notebooks = SqlNotebookRepository(ctx, d)
        val pages = SqlPageRepository(ctx, d)
        return Env(notebooks, pages, EnsureFirstPage(notebooks, pages))
    }

    @Test
    fun conLaAppRecienInstaladaCreaUnCuadernoYUnaPagina() = runTest {
        val e = env()
        val page = e.ensure()
        val cuadernos = e.notebooks.observeAll().first()
        assertEquals(listOf(EnsureFirstPage.FIRST_NOTEBOOK_TITLE), cuadernos.map { it.title })
        assertEquals(cuadernos.single().meta.id, page.notebookId)
        assertEquals(listOf(page.meta.id), e.pages.observeByNotebook(page.notebookId).first().map { it.meta.id })
    }

    @Test
    fun alReabrirDevuelveLaMismaPaginaSinCrearOtra() = runTest {
        val e = env()
        val primera = e.ensure()
        val segunda = e.ensure()
        assertEquals(primera.meta.id, segunda.meta.id)
        assertEquals(1, e.notebooks.observeAll().first().size)
        assertEquals(1, e.pages.observeByNotebook(primera.notebookId).first().size)
    }

    @Test
    fun siElCuadernoNoTienePaginasLeAgregaUna() = runTest {
        val e = env()
        val cuaderno = e.notebooks.create("Mío")
        val page = e.ensure()
        assertEquals(cuaderno.meta.id, page.notebookId)
        assertEquals(1, e.notebooks.observeAll().first().size)
    }

    @Test
    fun siTodoSeBorroCreaUnCuadernoNuevo() = runTest {
        val e = env()
        val vieja = e.ensure()
        e.notebooks.delete(vieja.notebookId)
        val nueva = e.ensure()
        assertEquals(false, nueva.notebookId == vieja.notebookId)
        assertEquals(1, e.notebooks.observeAll().first().size)
    }

    @Test
    fun conVariosCuadernosUsaElPrimeroEnElOrdenDelUsuario() = runTest {
        val e = env()
        val a = e.notebooks.create("A")
        e.notebooks.create("B")
        assertEquals(a.meta.id, e.ensure().notebookId)
    }
}
