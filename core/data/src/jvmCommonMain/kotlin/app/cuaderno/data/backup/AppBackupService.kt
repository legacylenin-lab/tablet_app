package app.cuaderno.data.backup

import app.cuaderno.domain.BackupResult
import app.cuaderno.domain.BackupSummary
import app.cuaderno.domain.InspectResult
import app.cuaderno.domain.PlatformFile
import app.cuaderno.domain.RestoreResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import app.cuaderno.domain.BackupService as DomainBackupService

/** Dónde vive cada pieza de la restauración, dentro del almacenamiento privado de la app. */
class RestoreFiles(
    /** Carpeta donde se deja preparado el respaldo verificado. */
    val stagingDir: File,
    /** Marca de «restauración lista»: [RestoreInstaller] la busca al arrancar. */
    val marker: File,
    /** Copia automática de lo que había antes de la última restauración. */
    val previousData: File,
)

/**
 * Conecta el respaldo de `core:data` ([BackupService], con flujos de Java) con la interfaz de
 * `core:domain` que usa la pantalla (PQ-14). Cada plataforma dice cómo leer y escribir los
 * archivos que eligió el usuario ([openInput], [openOutput]).
 */
class AppBackupService(
    private val backup: BackupService,
    private val files: RestoreFiles,
    private val dispatcher: CoroutineDispatcher,
    private val openInput: (PlatformFile) -> InputStream,
    private val openOutput: (PlatformFile) -> OutputStream,
) : DomainBackupService {

    override suspend fun export(destination: PlatformFile): BackupResult = withContext(dispatcher) {
        try {
            val manifest = backup.export { openOutput(destination) }
            BackupResult.Created(manifest.createdAtMillis, manifest.documentCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupResult.Failed("No se pudo crear el respaldo: ${e.message ?: e::class.simpleName}")
        }
    }

    override suspend fun inspect(source: PlatformFile): InspectResult = withContext(dispatcher) {
        inspectStream { openInput(source) }
    }

    override suspend fun prepareRestore(source: PlatformFile): RestoreResult = withContext(dispatcher) {
        prepare { openInput(source) }
    }

    override suspend fun previousData(): BackupSummary? = withContext(dispatcher) {
        if (!files.previousData.exists()) return@withContext null
        (inspectStream { files.previousData.inputStream() } as? InspectResult.Valid)?.summary
    }

    override suspend fun prepareUndoRestore(): RestoreResult = withContext(dispatcher) {
        if (!files.previousData.exists()) RestoreResult.Failed("No hay datos anteriores guardados.")
        else prepare { files.previousData.inputStream() }
    }

    private fun inspectStream(source: StreamSource): InspectResult = try {
        val info = backup.inspect(source)
        InspectResult.Valid(BackupSummary(info.createdAtMillis, info.appVersion, info.documentCount))
    } catch (e: BackupException) {
        InspectResult.Rejected(e.problem.message)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        InspectResult.Rejected("No se pudo leer el archivo: ${e.message ?: e::class.simpleName}")
    }

    /**
     * 1) Verifica el respaldo entero y lo deja en la carpeta de preparación. 2) Guarda una copia
     * automática de los datos actuales. 3) Escribe la marca. Si algo falla, no queda nada a medias.
     * La copia previa se hace **después** de leer el respaldo, porque al deshacer el respaldo es
     * justamente la copia previa.
     */
    private fun prepare(source: StreamSource): RestoreResult {
        files.marker.delete()
        files.stagingDir.deleteRecursively()
        val tempPrevious = File(files.previousData.path + ".tmp")
        return try {
            backup.extractVerified(source, files.stagingDir)
            tempPrevious.delete()
            tempPrevious.parentFile?.mkdirs()
            backup.export { tempPrevious.outputStream() }
            files.previousData.delete()
            check(tempPrevious.renameTo(files.previousData)) { "No se pudo guardar la copia de los datos actuales" }
            files.marker.writeText("listo")
            RestoreResult.ReadyToRestart
        } catch (e: CancellationException) {
            cleanUp(tempPrevious)
            throw e
        } catch (e: BackupException) {
            cleanUp(tempPrevious)
            RestoreResult.Failed(e.problem.message)
        } catch (e: Exception) {
            cleanUp(tempPrevious)
            RestoreResult.Failed("No se pudo preparar la restauración: ${e.message ?: e::class.simpleName}. No se cambió nada.")
        }
    }

    private fun cleanUp(tempPrevious: File) {
        files.marker.delete()
        files.stagingDir.deleteRecursively()
        tempPrevious.delete()
    }
}
