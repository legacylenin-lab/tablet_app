package app.cuaderno.data.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuaderno.data.BytesFile
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.data.testId
import app.cuaderno.domain.BackupResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** La interfaz delgada del respaldo (PQ-14): crear un respaldo en un archivo que eligió el usuario. */
class AppBackupServiceTest {
    private val root: File = Files.createTempDirectory("app-backup-test").toFile()
    private val documentsDir = File(root, "documents").also { it.mkdirs() }
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CuadernoDatabase.Schema.create(it) }

    @AfterTest
    fun limpiar() {
        root.deleteRecursively()
    }

    private fun core() = BackupService(
        snapshotter = SqlDriverSnapshotter(driver),
        documentsDir = documentsDir,
        workDir = File(root, "work"),
        deviceId = testId(1),
        appVersion = "1.0",
        schemaVersion = CuadernoDatabase.Schema.version.toInt(),
        nowMillis = { 1_700_000_000_000L },
    )

    @Test
    fun creaElRespaldoEnElArchivoElegido() = runTest {
        File(documentsDir, "a".repeat(64) + ".pdf").writeText("%PDF-1 uno")
        File(documentsDir, "b".repeat(64) + ".pdf").writeText("%PDF-1 dos")
        val destination = File(root, "mi-respaldo.cuaderno-backup")
        val service = AppBackupService(core(), StandardTestDispatcher(testScheduler)) { destination.outputStream() }

        val result = assertIs<BackupResult.Created>(service.export(BytesFile("mi-respaldo.cuaderno-backup", ByteArray(0))))
        assertEquals(2, result.documentCount)
        assertEquals(1_700_000_000_000L, result.createdAtMillis)
        // El archivo es un respaldo válido: se puede inspeccionar.
        assertEquals(2, core().inspect { destination.inputStream() }.documentCount)
    }

    @Test
    fun siNoSePuedeEscribirDevuelveUnAvisoClaro() = runTest {
        val service = AppBackupService(core(), StandardTestDispatcher(testScheduler)) { throw IOException("sin espacio") }
        val result = assertIs<BackupResult.Failed>(service.export(BytesFile("x", ByteArray(0))))
        assertTrue("sin espacio" in result.message)
    }
}
