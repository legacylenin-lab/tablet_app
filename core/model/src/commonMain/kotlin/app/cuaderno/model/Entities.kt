package app.cuaderno.model

/**
 * Metadatos comunes de todas las entidades sincronizables.
 * Ver docs/arquitectura.md §3.1.
 */
data class Meta(
    val id: EntityId,
    val createdHlc: Hlc,
    val updatedHlc: Hlc,
    val deviceId: EntityId,
    val authorId: EntityId,
    val deletedHlc: Hlc? = null,
) {
    /** Borrado suave: si hay [deletedHlc], el registro está borrado (nunca se purga en la v1). */
    val isDeleted: Boolean get() = deletedHlc != null

    /** Metadatos tras un cambio hecho en este instante (por este dispositivo y autor). */
    fun updated(hlc: Hlc, deviceId: EntityId = this.deviceId, authorId: EntityId = this.authorId) =
        copy(updatedHlc = hlc, deviceId = deviceId, authorId = authorId)

    /** Metadatos tras borrar: el borrado cuenta también como cambio. */
    fun deleted(hlc: Hlc, deviceId: EntityId = this.deviceId, authorId: EntityId = this.authorId) =
        copy(updatedHlc = hlc, deletedHlc = hlc, deviceId = deviceId, authorId = authorId)

    companion object {
        /** Metadatos de una entidad recién creada. */
        fun created(id: EntityId, hlc: Hlc, authorId: EntityId) =
            Meta(id = id, createdHlc = hlc, updatedHlc = hlc, deviceId = hlc.deviceId, authorId = authorId)
    }
}

enum class PageTemplate { BLANK, LINED, GRID }

enum class StrokeTool { PEN, HIGHLIGHTER }

enum class AnnotationKind { INK, HIGHLIGHT, UNDERLINE }

/** Cuaderno. [sortKey] es texto ordenable para reordenar sin reescribir los demás. */
data class Notebook(
    val meta: Meta,
    val title: String,
    val sortKey: String,
    val semester: String? = null,
    val color: Int? = null,
)

/** Página A4 fija (RF-08): 210 × 297 mm. */
data class Page(
    val meta: Meta,
    val notebookId: EntityId,
    val sortKey: String,
    val template: PageTemplate = PageTemplate.BLANK,
    val widthMm: Int = A4_WIDTH_MM,
    val heightMm: Int = A4_HEIGHT_MM,
) {
    companion object {
        const val A4_WIDTH_MM = 210
        const val A4_HEIGHT_MM = 297
    }
}

/** Trazo de tinta. Inmutable: borrar con el borrador es un borrado suave. [points] va codificado (§5.1). */
class Stroke(
    val meta: Meta,
    val pageId: EntityId,
    val tool: StrokeTool,
    val colorArgb: Int,
    val baseWidth: Float,
    val points: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is Stroke && meta == other.meta && pageId == other.pageId && tool == other.tool &&
            colorArgb == other.colorArgb && baseWidth == other.baseWidth && points.contentEquals(other.points)

    override fun hashCode(): Int = 31 * meta.hashCode() + points.contentHashCode()
}

/** Bloque de texto: único elemento editable en sitio y unidad de conflicto real (RF-24). */
data class TextBlock(
    val meta: Meta,
    val pageId: EntityId,
    val x: Int,
    val y: Int,
    val width: Int,
    val content: String,
    val contentHlc: Hlc,
)

/** Documento PDF de la biblioteca. El archivo se guarda aparte, con su hash como nombre. */
data class Document(
    val meta: Meta,
    val title: String,
    val fileHash: String,
    val pageCount: Int,
    val lastReadPage: Int = 0,
    val sizeBytes: Long,
)

/** Anotación sobre un PDF. Inmutable, igual que el trazo. [geometry] va codificado (§5.1). */
class Annotation(
    val meta: Meta,
    val documentId: EntityId,
    val pageIndex: Int,
    val kind: AnnotationKind,
    val colorArgb: Int,
    val geometry: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is Annotation && meta == other.meta && documentId == other.documentId &&
            pageIndex == other.pageIndex && kind == other.kind && colorArgb == other.colorArgb &&
            geometry.contentEquals(other.geometry)

    override fun hashCode(): Int = 31 * meta.hashCode() + geometry.contentHashCode()
}

/** Dispositivo: el propio y, desde la v2, los emparejados. */
data class Device(
    val id: EntityId,
    val name: String,
    val isLocal: Boolean,
)
