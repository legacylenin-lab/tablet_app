package app.cuaderno.domain

/** Resultado de crear un respaldo (RF-17). */
sealed interface BackupResult {
    data class Created(val createdAtMillis: Long, val documentCount: Int) : BackupResult

    /** No se pudo crear; [message] se puede mostrar al usuario. */
    data class Failed(val message: String) : BackupResult
}

/**
 * Respaldo y restauración (RF-17; docs/arquitectura.md §5.3 y §7). Interfaz delgada sobre el
 * respaldo de `core:data` (PQ-14), para que la interfaz de usuario no dependa de él.
 */
interface BackupService {
    /** Guarda todos los datos (base de datos y PDF) en [destination], un archivo que eligió el usuario. */
    suspend fun export(destination: PlatformFile): BackupResult
}
