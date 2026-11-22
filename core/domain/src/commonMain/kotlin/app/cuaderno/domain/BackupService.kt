package app.cuaderno.domain

/** Resultado de crear un respaldo (RF-17). */
sealed interface BackupResult {
    data class Created(val createdAtMillis: Long, val documentCount: Int) : BackupResult

    /** No se pudo crear; [message] se puede mostrar al usuario. */
    data class Failed(val message: String) : BackupResult
}

/** Lo que se muestra de un respaldo antes de restaurarlo. */
data class BackupSummary(val createdAtMillis: Long, val appVersion: String, val documentCount: Int)

/** Resultado de revisar un archivo de respaldo (sin recorrerlo entero). */
sealed interface InspectResult {
    data class Valid(val summary: BackupSummary) : InspectResult

    /** No se puede restaurar; [message] explica por qué con palabras simples. */
    data class Rejected(val message: String) : InspectResult
}

/** Resultado de preparar una restauración. */
sealed interface RestoreResult {
    /** Todo verificado y preparado: al reiniciar la app se cambian los datos. */
    data object ReadyToRestart : RestoreResult

    /** No se cambió nada; [message] explica por qué. */
    data class Failed(val message: String) : RestoreResult
}

/**
 * Respaldo y restauración (RF-17; docs/arquitectura.md §5.3 y §7). Interfaz delgada sobre el
 * respaldo de `core:data` (PQ-14), para que la interfaz de usuario no dependa de él.
 *
 * Restaurar va en dos tiempos: [prepareRestore] verifica el respaldo entero, lo deja listo y guarda
 * antes una copia automática de lo que había; los datos se cambian al reiniciar la app.
 */
interface BackupService {
    /** Guarda todos los datos (base de datos y PDF) en [destination], un archivo que eligió el usuario. */
    suspend fun export(destination: PlatformFile): BackupResult

    /** Lee solo el resumen de un respaldo y comprueba que esta app pueda restaurarlo. */
    suspend fun inspect(source: PlatformFile): InspectResult

    /** Verifica y prepara [source] para reemplazar los datos actuales al reiniciar. */
    suspend fun prepareRestore(source: PlatformFile): RestoreResult

    /** La copia automática que se guardó antes de la última restauración, o `null` si no hay. */
    suspend fun previousData(): BackupSummary?

    /** Prepara volver a los datos de antes de la última restauración (deshacerla). */
    suspend fun prepareUndoRestore(): RestoreResult
}
