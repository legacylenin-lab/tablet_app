package app.cuaderno.data.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuaderno.data.BytesFile
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.data.testId
import app.cuaderno.domain.BackupResult
import app.cuaderno.domain.InspectResult
import app.cuaderno.domain.PlatformFile
import app.cuaderno.domain.RestoreResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La interfaz delgada del respaldo (PQ-14): crear, revisar y preparar restauraciones. */
class AppBackupServiceTest {
    private val root: File = Files.createTempDirectory("app-backup-test").toFile()
    private val documentsDir = File(root, "documents").also { it.mkdirs() }
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CuadernoDatabase.Schema.create(it) }
    private val restore = RestoreFiles(
        stagingDir = File(root, "restore-staging"),
        marker = File(root, "restore-pending"),
        previousData = File(root, "backups/antes-de-restaurar.cuaderno-backup"),
    )
    private var now = 1_700_000_000_000L

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
        nowMillis = { now },
    )

    /** Los archivos «del usuario» son [BytesFile] cuyo nombre es una ruta dentro de [root]. */
    private fun TestScope.service(failWriting: Boolean = false) = AppBackupService(
        core(), restore, StandardTestDispatcher(testScheduler),
        openInput = { File(root, it.name).inputStream() },
        openOutput = { if (failWriting) throw IOException("sin espacio") else File(root, it.name).outputStream() },
    )

    private fun file(name: String): PlatformFile = BytesFile(name, ByteArray(0))

    private fun pdf(letter: Char, text: String) = File(documentsDir, letter.toString().repeat(64) + ".pdf").writeText(text)

    @Test
    fun creaElRespaldoEnElArchivoElegido() = runTest {
        pdf('a', "%PDF-1 uno")
        pdf('b', "%PDF-1 dos")
        val result = assertIs<BackupResult.Created>(service().export(file("mi-respaldo.cuaderno-backup")))
        assertEquals(2, result.documentCount)
        assertEquals(now, result.createdAtMillis)
        val summary = assertIs<InspectResult.Valid>(service().inspect(file("mi-respaldo.cuaderno-backup"))).summary
        assertEquals(2, summary.documentCount)
        assertEquals("1.0", summary.appVersion)
    }

    @Test
    fun siNoSePuedeEscribirDevuelveUnAvisoClaro() = runTest {
        val result = assertIs<BackupResult.Failed>(service(failWriting = true).export(file("x")))
        assertTrue("sin espacio" in result.message)
    }

    @Test
    fun unArchivoQueNoEsRespaldoSeRechazaConUnMensajeClaro() = runTest {
        File(root, "foto.jpg").writeText("no es un zip")
        val result = assertIs<InspectResult.Rejected>(service().inspect(file("foto.jpg")))
        assertEquals(BackupProblem.NOT_A_BACKUP.message, result.message)
    }

    @Test
    fun prepararDejaTodoListoYGuardaUnaCopiaDeLoQueHabia() = runTest {
        pdf('a', "%PDF-1 del respaldo")
        assertIs<BackupResult.Created>(service().export(file("respaldo.cuaderno-backup")))
        // Después del respaldo, los datos cambian: otro PDF.
        File(documentsDir, "a".repeat(64) + ".pdf").delete()
        pdf('c', "%PDF-1 posterior")
        now += 60_000

        assertEquals(RestoreResult.ReadyToRestart, service().prepareRestore(file("respaldo.cuaderno-backup")))
        assertTrue(restore.marker.exists())
        assertTrue(File(restore.stagingDir, "database.sqlite").exists())
        assertTrue(File(restore.stagingDir, "documents/" + "a".repeat(64) + ".pdf").exists())
        // La copia previa tiene lo que había al restaurar (el PDF «c»).
        val previous = assertNotNull(service().previousData())
        assertEquals(now, previous.createdAtMillis)
        assertEquals(1, previous.documentCount)
    }

    @Test
    fun unRespaldoDanadoNoCambiaNada() = runTest {
        pdf('a', "%PDF-1 uno")
        assertIs<BackupResult.Created>(service().export(file("r.cuaderno-backup")))
        val bytes = File(root, "r.cuaderno-backup").readBytes()
        // Un byte cambiado a mitad del archivo (dentro de los datos, no en el índice del ZIP del final).
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        File(root, "roto.cuaderno-backup").writeBytes(bytes)

        val result = assertIs<RestoreResult.Failed>(service().prepareRestore(file("roto.cuaderno-backup")))
        assertTrue("No se cambió nada" in result.message)
        assertFalse(restore.marker.exists())
        assertFalse(restore.stagingDir.exists())
        assertNull(service().previousData())
    }

    @Test
    fun deshacerUsaLaCopiaPrevia() = runTest {
        assertEquals(RestoreResult.Failed("No hay datos anteriores guardados."), service().prepareUndoRestore())
        pdf('a', "%PDF-1 uno")
        assertIs<BackupResult.Created>(service().export(file("r.cuaderno-backup")))
        assertEquals(RestoreResult.ReadyToRestart, service().prepareRestore(file("r.cuaderno-backup")))
        RestoreInstaller(restore.stagingDir, restore.marker, File(root, "db/cuaderno.db"), documentsDir).applyIfPending()

        assertEquals(RestoreResult.ReadyToRestart, service().prepareUndoRestore())
        assertTrue(restore.marker.exists())
        assertTrue(File(restore.stagingDir, "database.sqlite").exists())
    }
}
