package app.cuaderno.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cuaderno.data.DataContext
import app.cuaderno.data.SqlNotebookRepository
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.domain.RandomIdGenerator
import app.cuaderno.model.EntityId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CU-05b (PQ-10): el respaldo sobre el SQLite **real de Android**. En las pruebas de escritorio
 * `VACUUM INTO` corre sobre el SQLite de Java; aquí corre sobre el que trae el teléfono o la tablet.
 */
class AndroidBackupTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var driver: AndroidSqliteDriver
    private lateinit var db: CuadernoDatabase
    private val dbName = "backup-test-${System.nanoTime()}.db"
    private val ids = RandomIdGenerator()
    private val deviceId: EntityId = ids.newId()
    private var now = 1_000L

    @BeforeTest
    fun preparar() {
        root = File(context.cacheDir, "backup-test-${System.nanoTime()}").also { it.mkdirs() }
        File(root, "documents").mkdirs()
        driver = AndroidSqliteDriver(CuadernoDatabase.Schema, context, dbName)
        db = CuadernoDatabase(driver)
    }

    @AfterTest
    fun limpiar() {
        driver.close()
        context.deleteDatabase(dbName)
        root.deleteRecursively()
    }

    private fun servicio() = BackupService(
        snapshotter = SqlDriverSnapshotter(driver),
        documentsDir = File(root, "documents"),
        workDir = File(root, "work"),
        deviceId = deviceId,
        appVersion = "test",
        schemaVersion = CuadernoDatabase.Schema.version.toInt(),
        nowMillis = { now++ },
    )

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun exportaEInspeccionaUnRespaldoSobreElSqliteDeAndroid() {
        val repo = SqlNotebookRepository(DataContext(db, deviceId, ids.newId(), ids) { now++ }, Dispatchers.Default)
        runBlocking {
            repo.create("Cálculo")
            repo.create("Física")
        }
        val pdf = "contenido de prueba".encodeToByteArray()
        File(root, "documents/${sha(pdf)}.pdf").writeBytes(pdf)

        val servicio = servicio()
        val salida = ByteArrayOutputStream()
        val manifiesto = servicio.export { salida }
        val zip = salida.toByteArray()

        // El manifiesto y los checksums describen lo que se escribió.
        assertEquals(1, manifiesto.documentCount)
        assertEquals(setOf("database.sqlite", "documents/${sha(pdf)}.pdf"), manifiesto.checksums.keys)
        val info = servicio.inspect { ByteArrayInputStream(zip) }
        assertEquals(1, info.documentCount)
        assertEquals(deviceId.value, manifiesto.deviceId)

        // Se puede verificar todo y la base copiada se abre con el SQLite de Android y trae los datos.
        val staging = File(root, "staging")
        servicio.extractVerified({ ByteArrayInputStream(zip) }, staging)
        val copia = SQLiteDatabase.openDatabase(File(staging, "database.sqlite").path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            copia.rawQuery("SELECT title FROM notebook ORDER BY sort_key", null).use { c ->
                val titulos = buildList { while (c.moveToNext()) add(c.getString(0)) }
                assertEquals(listOf("Cálculo", "Física"), titulos)
            }
            copia.rawQuery("PRAGMA integrity_check", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("ok", c.getString(0))
            }
        } finally {
            copia.close()
        }
        assertEquals(pdf.toList(), File(staging, "documents/${sha(pdf)}.pdf").readBytes().toList())
    }
}
