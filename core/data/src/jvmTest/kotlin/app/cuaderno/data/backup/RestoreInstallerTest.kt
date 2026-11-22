package app.cuaderno.data.backup

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Último paso de la restauración (PQ-17): cambiar los archivos con la base de datos cerrada. */
class RestoreInstallerTest {
    private val root: File = Files.createTempDirectory("restore-test").toFile()
    private val staging = File(root, "restore-staging")
    private val marker = File(root, "restore-pending")
    private val database = File(root, "databases/cuaderno.db")
    private val documents = File(root, "files/documents")
    private val installer = RestoreInstaller(staging, marker, database, documents)

    @AfterTest
    fun limpiar() {
        root.deleteRecursively()
    }

    private fun datosActuales() {
        database.parentFile.mkdirs()
        database.writeText("base vieja")
        File(database.path + "-wal").writeText("wal viejo")
        File(database.path + "-shm").writeText("shm viejo")
        documents.mkdirs()
        File(documents, "viejo.pdf").writeText("pdf viejo")
    }

    private fun preparacion(pdfs: Map<String, String> = mapOf("nuevo.pdf" to "pdf nuevo")) {
        staging.mkdirs()
        File(staging, "database.sqlite").writeText("base del respaldo")
        if (pdfs.isNotEmpty()) {
            val dir = File(staging, "documents").also { it.mkdirs() }
            pdfs.forEach { (name, text) -> File(dir, name).writeText(text) }
        }
    }

    @Test
    fun sinMarcaNoCambiaNadaYBorraUnaPreparacionAMedias() {
        datosActuales()
        preparacion()
        assertFalse(installer.applyIfPending())
        assertEquals("base vieja", database.readText())
        assertEquals(listOf("viejo.pdf"), documents.list()!!.toList())
        assertFalse(staging.exists())
    }

    @Test
    fun conMarcaCambiaLaBaseYLosPdf() {
        datosActuales()
        preparacion()
        marker.writeText("ok")

        assertTrue(installer.applyIfPending())
        assertEquals("base del respaldo", database.readText())
        assertFalse(File(database.path + "-wal").exists())
        assertFalse(File(database.path + "-shm").exists())
        assertEquals(listOf("nuevo.pdf"), documents.list()!!.toList())
        assertFalse(File(documents.path + ".old").exists())
        assertFalse(staging.exists())
        assertFalse(marker.exists())
        // La segunda vez ya no hay nada pendiente.
        assertFalse(installer.applyIfPending())
        assertEquals("base del respaldo", database.readText())
    }

    @Test
    fun unRespaldoSinPdfDejaLaBibliotecaVacia() {
        datosActuales()
        preparacion(pdfs = emptyMap())
        marker.writeText("ok")
        assertTrue(installer.applyIfPending())
        assertTrue(documents.isDirectory)
        assertEquals(0, documents.list()!!.size)
    }

    @Test
    fun siLaAppSeCerroAMitadTerminaSinPerderLoRestaurado() {
        datosActuales()
        preparacion()
        marker.writeText("ok")
        // Simula un cierre después de cambiar la base y los PDF, pero antes de borrar la marca.
        assertTrue(installer.applyIfPending())
        marker.writeText("ok")
        staging.mkdirs()
        File(staging, "documents.done").writeText("ok")

        assertTrue(installer.applyIfPending())
        assertEquals("base del respaldo", database.readText())
        assertEquals(listOf("nuevo.pdf"), documents.list()!!.toList())
    }

    @Test
    fun funcionaAunqueTodaviaNoExistanDatos() {
        preparacion()
        marker.writeText("ok")
        assertTrue(installer.applyIfPending())
        assertEquals("base del respaldo", database.readText())
        assertEquals(listOf("nuevo.pdf"), documents.list()!!.toList())
    }
}
