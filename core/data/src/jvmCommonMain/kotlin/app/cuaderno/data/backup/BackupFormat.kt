package app.cuaderno.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Formato del respaldo (docs/arquitectura.md §5.3): un ZIP con extensión `.cuaderno-backup`.
 *
 * ```
 * manifest.json              va primero: se puede leer sin recorrer todo el archivo
 * database.sqlite            copia consistente de la base de datos
 * documents/<sha256>.pdf     los PDF, por su hash
 * ```
 */
object BackupFormat {
    const val FORMAT_VERSION = 1
    const val MANIFEST = "manifest.json"
    const val DATABASE = "database.sqlite"
    const val DOCUMENTS_DIR = "documents/"

    private val documentName = Regex("documents/[0-9a-f]{64}\\.pdf")

    fun documentEntry(sha256: String) = "$DOCUMENTS_DIR$sha256.pdf"

    /** Solo se aceptan estas entradas: evita que un ZIP malicioso escriba fuera de la carpeta de destino. */
    fun isAllowedEntry(name: String): Boolean =
        name == MANIFEST || name == DATABASE || documentName.matches(name)

    fun isDocumentFile(file: File): Boolean = documentName.matches("$DOCUMENTS_DIR${file.name}")

    val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
}

/** Contenido de `manifest.json`. [checksums] asocia cada entrada del ZIP (menos el propio manifiesto) con su SHA-256. */
@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val createdAtMillis: Long,
    val deviceId: String,
    val schemaVersion: Int,
    val documentCount: Int,
    val checksums: Map<String, String>,
)

/** De dónde se lee un respaldo. La tablet lo implementa con el selector de archivos de Android; Windows, con un `File`. */
fun interface StreamSource {
    fun openInput(): InputStream
}

/** Adónde se escribe un respaldo. */
fun interface StreamSink {
    fun openOutput(): OutputStream
}

/** Hace una copia consistente de la base de datos en un archivo (`VACUUM INTO`). */
fun interface DatabaseSnapshotter {
    fun snapshotTo(target: File)
}

/** Lo que se puede mostrar al usuario antes de restaurar (RF-17). */
data class BackupInfo(
    val formatVersion: Int,
    val appVersion: String,
    val createdAtMillis: Long,
    val schemaVersion: Int,
    val documentCount: Int,
)

/** Por qué un respaldo se rechaza. Cada caso tiene un mensaje claro para el usuario. */
enum class BackupProblem(val message: String) {
    NOT_A_BACKUP("El archivo no es un respaldo de la app."),
    NEWER_FORMAT("Este respaldo es de una versión más nueva de la app. Actualiza la app para poder restaurarlo."),
    NEWER_SCHEMA("Este respaldo tiene datos de una versión más nueva de la app. Actualiza la app para poder restaurarlo."),
    CORRUPT("El respaldo está dañado o incompleto. No se cambió nada."),
}

class BackupException(val problem: BackupProblem, detail: String? = null) :
    Exception(problem.message + (detail?.let { " ($it)" } ?: ""))
