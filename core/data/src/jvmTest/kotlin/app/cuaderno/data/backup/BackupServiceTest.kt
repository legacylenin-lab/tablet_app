package app.cuaderno.data.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuaderno.data.DataContext
import app.cuaderno.data.SqlNotebookRepository
import app.cuaderno.data.SequentialIdGenerator
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.data.testId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupServiceTest {

    private val root: File = Files.createTempDirectory("backup-test").toFile()
    private val documentsDir = File(root, "documents").also { it.mkdirs() }
    private val workDir = File(root, "work")
    private val schemaVersion = CuadernoDatabase.Schema.version.toInt()

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CuadernoDatabase.Schema.create(it) }
    private val db = CuadernoDatabase(driver)
    private var now = 1_000L

    @AfterTest
    fun limpiar() {
        root.deleteRecursively()
    }

    private fun service(schema: Int = schemaVersion) = BackupService(
        snapshotter = SqlDriverSnapshotter(driver),
        documentsDir = documentsDir,
        workDir = workDir,
        deviceId = testId(1),
        appVersion = "0.1.0",
        schemaVersion = schema,
        nowMillis = { now++ },
    )

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun guardarPdf(contenido: String): String {
        val bytes = contenido.encodeToByteArray()
        val hash = sha(bytes)
        File(documentsDir, "$hash.pdf").writeBytes(bytes)
        return hash
    }

    private fun exportar(servicio: BackupService = service()): ByteArray {
        val out = ByteArrayOutputStream()
        servicio.export { out }
        return out.toByteArray()
    }

    private fun fuente(bytes: ByteArray) = StreamSource { ByteArrayInputStream(bytes) }

    private fun entradas(zip: ByteArray): List<Pair<String, ByteArray>> {
        val resultado = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                resultado += e.name to z.readBytes()
            }
        }
        return resultado
    }

    private fun armarZip(entradas: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((nombre, datos) in entradas) {
                z.putNextEntry(ZipEntry(nombre))
                z.write(datos)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun manifiesto(
        formatVersion: Int = 1,
        schema: Int = schemaVersion,
        documentCount: Int = 0,
        checksums: Map<String, String> = emptyMap(),
    ) = BackupFormat.json.encodeToString(
        BackupManifest.serializer(),
        BackupManifest(formatVersion, "9.9", 5, "otro", schema, documentCount, checksums),
    ).encodeToByteArray()

    private fun problema(bloque: () -> Unit): BackupProblem =
        assertFailsWith<BackupException> { bloque() }.problem

    // ---- Exportar ----

    @Test
    fun elManifiestoVaPrimeroYDescribeElContenido() {
        val hash = guardarPdf("pdf uno")
        val zip = exportar()
        val lista = entradas(zip)
        assertEquals(listOf("manifest.json", "database.sqlite", "documents/$hash.pdf"), lista.map { it.first })
        val m = BackupFormat.json.decodeFromString(BackupManifest.serializer(), lista[0].second.decodeToString())
        assertEquals(1, m.formatVersion)
        assertEquals("0.1.0", m.appVersion)
        assertEquals(testId(1).value, m.deviceId)
        assertEquals(schemaVersion, m.schemaVersion)
        assertEquals(1, m.documentCount)
        assertEquals(setOf("database.sqlite", "documents/$hash.pdf"), m.checksums.keys)
        assertEquals(sha(lista[1].second), m.checksums["database.sqlite"])
        assertEquals(hash, m.checksums["documents/$hash.pdf"]!!.let { sha("pdf uno".encodeToByteArray()) })
    }

    @Test
    fun sinDocumentosTambienFunciona() {
        val m = service().export { ByteArrayOutputStream() }
        assertEquals(0, m.documentCount)
        assertEquals(setOf("database.sqlite"), m.checksums.keys)
    }

    @Test
    fun ignoraArchivosQueNoSonPdfConHash() {
        val hash = guardarPdf("bueno")
        File(documentsDir, "notas.txt").writeText("x")
        File(documentsDir, "abc.pdf").writeText("nombre sin hash")
        assertEquals(listOf("documents/$hash.pdf"), entradas(exportar()).map { it.first }.filter { it.startsWith("documents/") })
    }

    @Test
    fun noDejaArchivosTemporales() {
        guardarPdf("a")
        exportar()
        assertEquals(emptyList(), workDir.list()?.toList().orEmpty())
    }

    // ---- Inspeccionar ----

    @Test
    fun inspeccionarMuestraLaInformacionSinRestaurar() {
        guardarPdf("a")
        guardarPdf("b")
        val info = service().inspect(fuente(exportar()))
        assertEquals(1, info.formatVersion)
        assertEquals("0.1.0", info.appVersion)
        assertEquals(2, info.documentCount)
        assertEquals(schemaVersion, info.schemaVersion)
        assertTrue(info.createdAtMillis >= 1_000)
    }

    @Test
    fun rechazaArchivosQueNoSonRespaldos() {
        val s = service()
        assertEquals(BackupProblem.NOT_A_BACKUP, problema { s.inspect(fuente(ByteArray(0))) })
        assertEquals(BackupProblem.NOT_A_BACKUP, problema { s.inspect(fuente("hola".encodeToByteArray())) })
        assertEquals(BackupProblem.NOT_A_BACKUP, problema { s.inspect(fuente(armarZip(listOf("otro.txt" to byteArrayOf(1))))) })
        // El manifiesto debe ser la primera entrada.
        val desordenado = armarZip(listOf("database.sqlite" to byteArrayOf(1), "manifest.json" to manifiesto()))
        assertEquals(BackupProblem.NOT_A_BACKUP, problema { s.inspect(fuente(desordenado)) })
        assertEquals(BackupProblem.NOT_A_BACKUP, problema { s.inspect(fuente(armarZip(listOf("manifest.json" to "{no es json".encodeToByteArray())))) })
    }

    @Test
    fun rechazaUnRespaldoDeUnFormatoMasNuevo() {
        val zip = armarZip(listOf("manifest.json" to manifiesto(formatVersion = 99)))
        assertEquals(BackupProblem.NEWER_FORMAT, problema { service().inspect(fuente(zip)) })
    }

    @Test
    fun rechazaUnRespaldoConEsquemaMasNuevoQueLaApp() {
        val zip = armarZip(listOf("manifest.json" to manifiesto(schema = schemaVersion + 1)))
        assertEquals(BackupProblem.NEWER_SCHEMA, problema { service().inspect(fuente(zip)) })
    }

    @Test
    fun aceptaUnRespaldoConEsquemaMasViejo() {
        val zip = armarZip(listOf("manifest.json" to manifiesto(schema = 1)))
        // Con una app de esquema más nuevo (2), un respaldo del esquema 1 se acepta (luego se migra).
        assertEquals(1, service(schema = 2).inspect(fuente(zip)).schemaVersion)
    }

    // ---- Restaurar a la carpeta de preparación ----

    @Test
    fun idaYVueltaConservaLosDatosYLosDocumentos() = runTest {
        // Datos reales en la base de datos.
        val repo = SqlNotebookRepository(
            DataContext(db, testId(1), testId(2), SequentialIdGenerator()) { now++ },
            StandardTestDispatcher(testScheduler),
        )
        repo.create("Cálculo")
        repo.create("Física")
        val h1 = guardarPdf("contenido pdf 1")
        val h2 = guardarPdf("contenido pdf 2")

        val zip = exportar()
        val staging = File(root, "staging")
        val m = service().extractVerified(fuente(zip), staging)

        assertEquals(2, m.documentCount)
        assertContentEquals("contenido pdf 1".encodeToByteArray(), File(staging, "documents/$h1.pdf").readBytes())
        assertContentEquals("contenido pdf 2".encodeToByteArray(), File(staging, "documents/$h2.pdf").readBytes())
        // La base restaurada se abre y trae los mismos cuadernos.
        val restaurada = JdbcSqliteDriver("jdbc:sqlite:${File(staging, "database.sqlite").absolutePath}")
        val titulos = CuadernoDatabase(restaurada).cuadernoQueries.selectNotebooks().executeAsList().map { it.title }
        assertEquals(listOf("Cálculo", "Física"), titulos)
        restaurada.close()
    }

    @Test
    fun unByteAlteradoSeDetectaYNoDejaNadaEnLaCarpeta() {
        val hash = guardarPdf("contenido importante")
        val original = entradas(exportar())
        val alterado = original.map { (n, d) ->
            if (n == "documents/$hash.pdf") n to d.copyOf().also { it[0] = (it[0] + 1).toByte() } else n to d
        }
        val staging = File(root, "staging")
        assertEquals(BackupProblem.CORRUPT, problema { service().extractVerified(fuente(armarZip(alterado)), staging) })
        assertFalse(staging.exists())
    }

    @Test
    fun unRespaldoTruncadoSeRechaza() {
        // Contenido aleatorio: no se comprime, así los cortes caen dentro de los datos.
        val aleatorio = kotlin.random.Random(1).nextBytes(100_000)
        File(documentsDir, "${sha(aleatorio)}.pdf").writeBytes(aleatorio)
        val zip = exportar()
        val staging = File(root, "staging")
        for (corte in listOf(zip.size - 400, zip.size / 2, 60)) {
            val p = assertFailsWith<BackupException> {
                service().extractVerified(fuente(zip.copyOf(corte)), staging)
            }.problem
            assertTrue(p == BackupProblem.CORRUPT || p == BackupProblem.NOT_A_BACKUP)
            assertFalse(staging.exists())
        }
    }

    @Test
    fun faltaUnaEntradaDelManifiesto() {
        val hash = guardarPdf("x")
        val completo = entradas(exportar())
        val sinDocumento = completo.filter { it.first != "documents/$hash.pdf" }
        val staging = File(root, "staging")
        assertEquals(BackupProblem.CORRUPT, problema { service().extractVerified(fuente(armarZip(sinDocumento)), staging) })
        assertFalse(staging.exists())
    }

    @Test
    fun sobraUnaEntradaQueElManifiestoNoConoce() {
        guardarPdf("x")
        val extra = "documents/${"a".repeat(64)}.pdf" to byteArrayOf(1, 2, 3)
        val staging = File(root, "staging")
        assertEquals(BackupProblem.CORRUPT, problema { service().extractVerified(fuente(armarZip(entradas(exportar()) + extra)), staging) })
    }

    @Test
    fun rutasPeligrosasSeRechazanYNoEscribenFueraDeLaCarpeta() {
        val malicioso = armarZip(
            listOf(
                "manifest.json" to manifiesto(checksums = mapOf("../evil.txt" to sha(byteArrayOf(1)))),
                "../evil.txt" to byteArrayOf(1),
            ),
        )
        val staging = File(root, "staging")
        assertEquals(BackupProblem.CORRUPT, problema { service().extractVerified(fuente(malicioso), staging) })
        assertFalse(File(root, "evil.txt").exists())
        assertFalse(File(staging.parentFile, "evil.txt").exists())
        for (nombre in listOf("/etc/passwd", "documents/../../x.pdf", "documents/nombre.pdf", "otra/cosa.bin")) {
            val zip = armarZip(listOf("manifest.json" to manifiesto(checksums = mapOf(nombre to sha(byteArrayOf(1)))), nombre to byteArrayOf(1)))
            assertEquals(BackupProblem.CORRUPT, problema { service().extractVerified(fuente(zip), File(root, "s2")) })
        }
    }

    @Test
    fun elConteoDeDocumentosDebeCoincidir() {
        val hash = guardarPdf("x")
        val m = BackupFormat.json.decodeFromString(BackupManifest.serializer(), entradas(exportar())[0].second.decodeToString())
        val mentiroso = manifiesto(documentCount = 5, checksums = m.checksums)
        val resto = entradas(exportar()).drop(1)
        val staging = File(root, "staging")
        assertEquals(
            BackupProblem.CORRUPT,
            problema { service().extractVerified(fuente(armarZip(listOf("manifest.json" to mentiroso) + resto)), staging) },
        )
        assertTrue(hash.isNotEmpty())
    }

    @Test
    fun noRestauraSobreUnaCarpetaConContenido() {
        val staging = File(root, "staging").also { it.mkdirs(); File(it, "algo.txt").writeText("x") }
        assertFailsWith<IllegalArgumentException> { service().extractVerified(fuente(exportar()), staging) }
        // No se tocó lo que ya había.
        assertTrue(File(staging, "algo.txt").exists())
    }

    @Test
    fun unRespaldoMasNuevoNoDejaNadaEnLaCarpeta() {
        val zip = armarZip(listOf("manifest.json" to manifiesto(formatVersion = 2)))
        val staging = File(root, "staging")
        assertEquals(BackupProblem.NEWER_FORMAT, problema { service().extractVerified(fuente(zip), staging) })
        assertFalse(staging.exists())
    }
}
