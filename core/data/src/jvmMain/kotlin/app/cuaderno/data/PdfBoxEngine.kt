package app.cuaderno.data

import app.cuaderno.domain.PdfEngine
import app.cuaderno.domain.RenderedPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.rendering.RenderDestination
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Motor de PDF de Windows, con Apache PDFBox (docs/arquitectura.md §8; prototipo P-1).
 *
 * Abrir un PDF grande cuesta, así que se deja abierto el último documento usado y solo se cambia
 * cuando se pide otro. PDFBox no permite usar un documento desde dos hilos a la vez ([lock]).
 */
class PdfBoxEngine(
    private val files: FileDocumentStore,
    private val dispatcher: CoroutineDispatcher,
) : PdfEngine, AutoCloseable {
    private val lock = Mutex()
    private var openHash: String? = null
    private var openDocument: PDDocument? = null

    override suspend fun pageCount(sha256: String): Int = withDocument(sha256) { it.numberOfPages }

    override suspend fun renderPage(sha256: String, pageIndex: Int, widthPx: Int): RenderedPage {
        require(widthPx in 1..MAX_WIDTH) { "Ancho inválido: $widthPx" }
        return withDocument(sha256) { document ->
            val (pageW, pageH) = pageSize(document, pageIndex)
            val height = maxOf(1, (widthPx.toDouble() * pageH / pageW).toInt())
            draw(document, pageIndex, scale = widthPx / pageW, left = 0, top = 0, width = widthPx, height = height)
        }
    }

    override suspend fun renderRegion(
        sha256: String, pageIndex: Int, pageWidthPx: Int, left: Int, top: Int, width: Int, height: Int,
    ): RenderedPage {
        require(pageWidthPx in 1..MAX_PAGE_WIDTH) { "Ancho de página inválido: $pageWidthPx" }
        require(width in 1..MAX_WIDTH && height in 1..MAX_WIDTH) { "Tamaño inválido: ${width}x$height" }
        return withDocument(sha256) { document ->
            val (pageW, _) = pageSize(document, pageIndex)
            draw(document, pageIndex, scale = pageWidthPx / pageW, left = left, top = top, width = width, height = height)
        }
    }

    /** Cierra el documento abierto. */
    override fun close() {
        openDocument?.close()
        openDocument = null
        openHash = null
    }

    /** Tamaño visible de la página en puntos PDF, ya girada si el PDF lo pide. */
    private fun pageSize(document: PDDocument, pageIndex: Int): Pair<Double, Double> {
        require(pageIndex in 0 until document.numberOfPages) { "Página fuera de rango: $pageIndex" }
        val page = document.getPage(pageIndex)
        val box = page.cropBox
        val turned = page.rotation % 180 != 0
        val w = (if (turned) box.height else box.width).toDouble()
        val h = (if (turned) box.width else box.height).toDouble()
        return maxOf(w, 1.0) to maxOf(h, 1.0)
    }

    /** Dibuja la página a [scale] píxeles por punto, recortada al rectángulo pedido, con fondo blanco. */
    private fun draw(document: PDDocument, pageIndex: Int, scale: Double, left: Int, top: Int, width: Int, height: Int): RenderedPage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            // PDFBox «limpia» la página con el color de fondo antes de dibujar; sin esto saldría negra.
            g.background = Color.WHITE
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.translate(-left.toDouble(), -top.toDouble())
            PDFRenderer(document).renderPageToGraphics(pageIndex, g, scale.toFloat(), scale.toFloat(), RenderDestination.VIEW)
        } finally {
            g.dispose()
        }
        return RenderedPage(width, height, image.getRGB(0, 0, width, height, null, 0, width))
    }

    private suspend fun <T> withDocument(sha256: String, block: (PDDocument) -> T): T =
        withContext(dispatcher) {
            lock.withLock {
                val document = openDocument?.takeIf { openHash == sha256 } ?: run {
                    close()
                    Loader.loadPDF(files.fileFor(sha256)).also {
                        openDocument = it
                        openHash = sha256
                    }
                }
                block(document)
            }
        }

    private companion object {
        const val MAX_WIDTH = 4096
        const val MAX_PAGE_WIDTH = 40_000
    }
}
