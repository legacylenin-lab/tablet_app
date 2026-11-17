package app.cuaderno.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.model.Hlc
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlNotebookRepositoryTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CuadernoDatabase.Schema.create(it) }
    private val db = CuadernoDatabase(driver)
    private var now = 1_000L
    private val ids = SequentialIdGenerator()

    private fun TestScope.repo() = SqlNotebookRepository(
        DataContext(db, deviceId = testId(1), authorId = testId(2), ids = ids, nowMillis = { now++ }),
        StandardTestDispatcher(testScheduler),
    )

    private fun changes() = db.cuadernoQueries.selectChanges().executeAsList()

    @Test
    fun crearDejaElCuadernoVisibleYEnOrden() = runTest {
        val repo = repo()
        val a = repo.create("Cálculo")
        val b = repo.create("Física")
        val lista = repo.observeAll().first()
        assertEquals(listOf("Cálculo", "Física"), lista.map { it.title })
        assertTrue(a.sortKey < b.sortKey)
        assertEquals(testId(1), a.meta.deviceId)
        assertEquals(testId(2), a.meta.authorId)
    }

    @Test
    fun cadaEscrituraDejaSuFilaEnElRegistroDeCambios() = runTest {
        val repo = repo()
        val a = repo.create("Cálculo")
        repo.rename(a.meta.id, "Cálculo I")
        repo.delete(a.meta.id)
        val log = changes()
        assertEquals(listOf("UPSERT", "UPSERT", "DELETE"), log.map { it.op })
        assertTrue(log.all { it.entity == "notebook" && it.entity_id == a.meta.id.value })
        // Los HLC del registro son crecientes: cada cambio es posterior al anterior.
        val hlcs = log.map { Hlc.parse(it.hlc) }
        assertEquals(hlcs.sorted(), hlcs)
        assertEquals(3, hlcs.toSet().size)
    }

    @Test
    fun renombrarCambiaElTituloYLaMarcaDeActualizacion() = runTest {
        val repo = repo()
        val a = repo.create("Cálculo")
        repo.rename(a.meta.id, "Cálculo I")
        val actual = repo.observeAll().first().single()
        assertEquals("Cálculo I", actual.title)
        assertEquals(a.meta.createdHlc, actual.meta.createdHlc)
        assertTrue(actual.meta.updatedHlc > a.meta.updatedHlc)
    }

    @Test
    fun borrarOcultaElCuadernoPeroConservaLaFila() = runTest {
        val repo = repo()
        val a = repo.create("Cálculo")
        repo.create("Física")
        repo.delete(a.meta.id)
        assertEquals(listOf("Física"), repo.observeAll().first().map { it.title })
        // Borrado suave: la fila sigue en la base de datos con su marca de borrado.
        val fila = db.cuadernoQueries.selectNotebookById(a.meta.id.value).executeAsOneOrNull()
        assertEquals(null, fila)
        val total = driver.executeQuery(null, "SELECT COUNT(*) FROM notebook WHERE deleted_hlc IS NOT NULL", {
            app.cash.sqldelight.db.QueryResult.Value(it.next().value.let { _ -> it.getLong(0) })
        }, 0).value
        assertEquals(1L, total)
    }

    @Test
    fun moverReordenaSinTocarLosDemas() = runTest {
        val repo = repo()
        val a = repo.create("A")
        val b = repo.create("B")
        val c = repo.create("C")
        repo.move(c.meta.id, after = a.meta.id)
        assertEquals(listOf("A", "C", "B"), repo.observeAll().first().map { it.title })
        repo.move(c.meta.id, after = null)
        assertEquals(listOf("C", "A", "B"), repo.observeAll().first().map { it.title })
        // Solo cambió la fila movida: A y B conservan su clave.
        val lista = repo.observeAll().first().associateBy { it.title }
        assertEquals(a.sortKey, lista.getValue("A").sortKey)
        assertEquals(b.sortKey, lista.getValue("B").sortKey)
    }

    @Test
    fun elRelojNoRetrocedeAunqueLaHoraDelSistemaRetroceda() = runTest {
        val repo = repo()
        now = 5_000
        val a = repo.create("A")
        now = 100
        val b = repo.create("B")
        assertTrue(b.meta.createdHlc > a.meta.createdHlc)
    }

    @Test
    fun elRelojContinuaTrasReabrirLaBase() = runTest {
        val primero = repo().create("A")
        now = 0
        // Un repositorio nuevo sobre la misma base lee el último HLC guardado.
        val segundo = repo().create("B")
        assertTrue(segundo.meta.createdHlc > primero.meta.createdHlc)
    }

    @Test
    fun operarSobreUnCuadernoInexistenteFalla() = runTest {
        val repo = repo()
        assertFailsWith<IllegalArgumentException> { repo.rename(unknownId, "x") }
        assertFailsWith<IllegalArgumentException> { repo.delete(unknownId) }
        val a = repo.create("A")
        assertFailsWith<IllegalArgumentException> { repo.move(a.meta.id, after = unknownId) }
        // Un intento fallido no deja nada en el registro de cambios.
        assertEquals(1, changes().size)
    }

    @Test
    fun siUnaEscrituraFallaNoQuedaNadaAMedias() = runTest {
        val repo = repo()
        val a = repo.create("A")
        val antes = changes().size
        runCatching { repo.move(a.meta.id, after = unknownId) }
        assertEquals(antes, changes().size)
    }
}
