package app.cuaderno.domain

import app.cuaderno.model.Document

/**
 * Archivo que el usuario eligió fuera de la app (en Android, un `content://`; en Windows, una ruta).
 * Solo la plataforma sabe abrirlo; el resto de la app lo pasa de mano en mano.
 */
interface PlatformFile {
    /** Nombre del archivo tal como lo ve el usuario, por ejemplo `Apuntes.pdf`. */
    val name: String
}

/** Resultado de importar un archivo a la biblioteca (RF-10, RF-11; docs/arquitectura.md §7). */
sealed interface ImportResult {
    data class Imported(val document: Document) : ImportResult

    /** El mismo PDF ya estaba en la biblioteca: no se duplica (CS-13). */
    data class AlreadyExists(val document: Document) : ImportResult

    /** No es un PDF. Con [isWord], se puede mostrar la guía para convertirlo (RF-11). */
    data class NotPdf(val isWord: Boolean) : ImportResult

    /** Parece un PDF, pero no se puede abrir (dañado o protegido con contraseña). */
    data object Corrupt : ImportResult
}

/** Un archivo ya copiado a la carpeta privada de la app como `documents/<sha256>.pdf` (§5.2). */
class StoredFile(
    /** SHA-256 del contenido, en hexadecimal y minúsculas. */
    val sha256: String,
    val sizeBytes: Long,
    /** El archivo empieza como un PDF (`%PDF-`). Si no, no es un PDF aunque lo diga su nombre. */
    val startsLikePdf: Boolean,
    /** `false` si ya había un archivo con el mismo contenido (se dejó como estaba). */
    val isNew: Boolean,
)

/** Carpeta privada de los PDF (§5.2). La implementa cada plataforma. El original del usuario nunca se toca. */
interface DocumentFileStore {
    /** Copia [source] a `documents/<sha256>.pdf`. Si ya existía un archivo con ese contenido, no lo reemplaza. */
    suspend fun store(source: PlatformFile): StoredFile

    /** Borra `documents/<sha256>.pdf`. Solo sirve para no dejar restos de una importación fallida. */
    suspend fun discard(sha256: String)
}

/**
 * Una página de PDF convertida en imagen: [pixels] tiene `width × height` colores ARGB, fila por
 * fila, con fondo blanco.
 */
class RenderedPage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "Imagen inválida: ${width}x$height" }
    }
}

/** Motor de PDF de cada plataforma (§8). Lee los PDF ya guardados en `documents/`. */
interface PdfEngine {
    /** Número de páginas del PDF guardado con ese hash. Lanza una excepción si no se puede abrir. */
    suspend fun pageCount(sha256: String): Int

    /**
     * Dibuja la página [pageIndex] (desde 0) con [widthPx] píxeles de ancho; el alto sale de la
     * proporción de la página. Lanza una excepción si no se puede.
     */
    suspend fun renderPage(sha256: String, pageIndex: Int, widthPx: Int): RenderedPage
}
