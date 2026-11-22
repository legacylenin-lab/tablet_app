package app.cuaderno

import android.content.Context
import app.cuaderno.data.AndroidPdfEngine
import app.cuaderno.data.AndroidStorage
import app.cuaderno.data.FileDocumentStore
import app.cuaderno.data.SqlDocumentRepository
import app.cuaderno.data.SqlInkRepository
import app.cuaderno.data.SqlNotebookRepository
import app.cuaderno.data.SqlPageRepository
import app.cuaderno.data.SqlTextBlockRepository
import app.cuaderno.data.backup.AppBackupService
import app.cuaderno.data.backup.RestoreFiles
import app.cuaderno.data.backup.RestoreInstaller
import java.io.File
import app.cuaderno.domain.RandomIdGenerator
import app.cuaderno.library.AndroidPlatformFile
import app.cuaderno.model.EntityId
import kotlinx.coroutines.Dispatchers

/**
 * Arma los repositorios de la tablet (inyección de dependencias manual, docs/arquitectura.md §2).
 * El identificador del dispositivo y el del autor se crean la primera vez y se guardan para siempre.
 */
class AppContainer(context: Context) {
    val services: AppServices

    init {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("cuaderno", Context.MODE_PRIVATE)
        val ids = RandomIdGenerator()

        fun stableId(key: String): EntityId {
            val saved = prefs.getString(key, null)?.let { runCatching { EntityId.parse(it) }.getOrNull() }
            if (saved != null) return saved
            val created = ids.newId()
            prefs.edit().putString(key, created.value).apply()
            return created
        }

        val appVersion = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull() ?: "?"

        // Restauración en dos tiempos (arquitectura §5.3): si se dejó una preparada, se termina
        // ahora, antes de abrir la base de datos.
        val restoreFiles = RestoreFiles(
            stagingDir = File(appContext.filesDir, "restore-staging"),
            marker = File(appContext.filesDir, "restore-pending"),
            previousData = File(appContext.filesDir, "backups/antes-de-restaurar.cuaderno-backup"),
        )
        val restored = runCatching {
            RestoreInstaller(
                restoreFiles.stagingDir,
                restoreFiles.marker,
                appContext.getDatabasePath(AndroidStorage.DATABASE_NAME),
                File(appContext.filesDir, "documents"),
            ).applyIfPending()
        }

        val storage = AndroidStorage(appContext, stableId("device_id"), stableId("author_id"), ids, appVersion)
        val data = storage.data
        val files = FileDocumentStore(storage.documentsDir, Dispatchers.IO) { file ->
            (file as AndroidPlatformFile).open(appContext)
        }
        val pdf = AndroidPdfEngine(files, Dispatchers.IO)
        services = AppServices(
            notebooks = SqlNotebookRepository(data, Dispatchers.IO),
            pages = SqlPageRepository(data, Dispatchers.IO),
            ink = SqlInkRepository(data, Dispatchers.IO),
            text = SqlTextBlockRepository(data, Dispatchers.IO),
            documents = SqlDocumentRepository(data, Dispatchers.IO, files, pdf),
            pdf = pdf,
            backup = AppBackupService(
                storage.backup,
                restoreFiles,
                Dispatchers.IO,
                openInput = { file -> (file as AndroidPlatformFile).open(appContext) },
                openOutput = { file -> (file as AndroidPlatformFile).openForWriting(appContext) },
            ),
            startupMessage = when {
                restored.getOrNull() == true -> "Se restauró el respaldo: tus cuadernos y tu biblioteca son los del archivo elegido."
                restored.isFailure -> "No se pudo terminar la restauración: ${restored.exceptionOrNull()?.message}. Avísale a Leyni."
                else -> null
            },
        )
    }
}
