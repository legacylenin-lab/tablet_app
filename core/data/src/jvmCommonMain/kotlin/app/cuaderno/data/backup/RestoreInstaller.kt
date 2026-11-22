package app.cuaderno.data.backup

import java.io.File

/**
 * Último paso de la restauración (docs/arquitectura.md §5.3, PQ-17): con la base de datos todavía
 * **cerrada** (al arrancar la app), cambia los datos en uso por los que dejó preparados
 * [BackupService.extractVerified] en [stagingDir].
 *
 * Solo actúa si existe [marker], que se escribe cuando la preparación terminó bien. Cada paso se
 * puede repetir: si la app se cierra a mitad, al arrancar de nuevo termina lo que faltaba.
 *
 * @param databaseFile el archivo de la base de datos (`cuaderno.db`); se borran también sus
 *   archivos auxiliares de SQLite (`-wal`, `-shm`, `-journal`).
 * @param documentsDir la carpeta de los PDF en uso.
 */
class RestoreInstaller(
    private val stagingDir: File,
    private val marker: File,
    private val databaseFile: File,
    private val documentsDir: File,
) {
    /** `true` si había una restauración pendiente y se completó. */
    fun applyIfPending(): Boolean {
        if (!marker.exists()) {
            // Una preparación que no llegó a terminar (la app se cerró a mitad): no vale, se borra.
            stagingDir.deleteRecursively()
            return false
        }
        val stagedDatabase = File(stagingDir, BackupFormat.DATABASE)
        val stagedDocuments = File(stagingDir, BackupFormat.DOCUMENTS_DIR.trimEnd('/'))

        // 1. La base de datos: se borra la actual (con sus auxiliares) y se pone la del respaldo.
        if (stagedDatabase.exists()) {
            databaseFile.parentFile?.mkdirs()
            for (suffix in SQLITE_SIDE_FILES) File(databaseFile.path + suffix).delete()
            check(!databaseFile.exists() || databaseFile.delete()) { "No se pudo borrar la base de datos anterior" }
            move(stagedDatabase, databaseFile)
        }

        // 2. Los PDF: la carpeta en uso se reemplaza entera por la del respaldo (puede no tener PDF).
        val oldDocuments = File(documentsDir.path + ".old")
        if (stagedDocuments.exists() || !stagingDocumentsDone()) {
            oldDocuments.deleteRecursively()
            if (documentsDir.exists()) move(documentsDir, oldDocuments)
            if (stagedDocuments.exists()) move(stagedDocuments, documentsDir) else documentsDir.mkdirs()
            File(stagingDir, DOCUMENTS_DONE).apply { parentFile?.mkdirs() }.writeText("ok")
        }
        oldDocuments.deleteRecursively()

        // 3. Listo: primero se borra la marca (así no se repite nada) y luego lo que sobró de la preparación.
        marker.delete()
        stagingDir.deleteRecursively()
        return true
    }

    private fun stagingDocumentsDone() = File(stagingDir, DOCUMENTS_DONE).exists()

    /** Mueve un archivo o carpeta; si renombrar no funciona (otro disco), copia y borra. */
    private fun move(from: File, to: File) {
        if (from.renameTo(to)) return
        if (from.isDirectory) from.copyRecursively(to, overwrite = true) else from.copyTo(to, overwrite = true)
        from.deleteRecursively()
    }

    companion object {
        private val SQLITE_SIDE_FILES = listOf("-wal", "-shm", "-journal")

        /** Se anota dentro de la preparación cuando la carpeta de PDF ya se cambió. */
        private const val DOCUMENTS_DONE = "documents.done"
    }
}
