package app.cuaderno.data.backup

import app.cuaderno.domain.BackupResult
import app.cuaderno.domain.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.OutputStream
import app.cuaderno.domain.BackupService as DomainBackupService

/**
 * Conecta el respaldo de `core:data` ([BackupService], con flujos de Java) con la interfaz de
 * `core:domain` que usa la pantalla (PQ-14). Cada plataforma dice cómo escribir en el archivo
 * que eligió el usuario ([openOutput]).
 */
class AppBackupService(
    private val backup: BackupService,
    private val dispatcher: CoroutineDispatcher,
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
}
