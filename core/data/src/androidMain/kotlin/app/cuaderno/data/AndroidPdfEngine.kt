package app.cuaderno.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import app.cuaderno.domain.PdfEngine
import app.cuaderno.domain.RenderedPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Motor de PDF de Android, con el `PdfRenderer` del sistema (docs/arquitectura.md §8).
 * `PdfRenderer` solo permite una página abierta a la vez, así que se usa de a uno ([lock]).
 */
class AndroidPdfEngine(
    private val files: FileDocumentStore,
    private val dispatcher: CoroutineDispatcher,
) : PdfEngine {
    private val lock = Mutex()

    override suspend fun pageCount(sha256: String): Int = withRenderer(sha256) { it.pageCount }

    override suspend fun renderPage(sha256: String, pageIndex: Int, widthPx: Int): RenderedPage {
        require(widthPx in 1..MAX_WIDTH) { "Ancho inválido: $widthPx" }
        return withRenderer(sha256) { renderer ->
            require(pageIndex in 0 until renderer.pageCount) { "Página fuera de rango: $pageIndex" }
            renderer.openPage(pageIndex).use { page ->
                val height = maxOf(1, (widthPx.toLong() * page.height / maxOf(1, page.width)).toInt())
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                try {
                    // PdfRenderer deja transparente lo que no tiene contenido: el fondo va en blanco.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val pixels = IntArray(widthPx * height)
                    bitmap.getPixels(pixels, 0, widthPx, 0, 0, widthPx, height)
                    RenderedPage(widthPx, height, pixels)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    override suspend fun renderRegion(
        sha256: String, pageIndex: Int, pageWidthPx: Int, left: Int, top: Int, width: Int, height: Int,
    ): RenderedPage {
        require(pageWidthPx in 1..MAX_PAGE_WIDTH) { "Ancho de página inválido: $pageWidthPx" }
        require(width in 1..MAX_WIDTH && height in 1..MAX_WIDTH) { "Tamaño inválido: ${width}x$height" }
        return withRenderer(sha256) { renderer ->
            require(pageIndex in 0 until renderer.pageCount) { "Página fuera de rango: $pageIndex" }
            renderer.openPage(pageIndex).use { page ->
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    // De puntos PDF a píxeles de la página ampliada, y luego se corre hasta el rectángulo pedido.
                    val scale = pageWidthPx.toFloat() / maxOf(1, page.width)
                    val matrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(-left.toFloat(), -top.toFloat())
                    }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    RenderedPage(width, height, pixels)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private suspend fun <T> withRenderer(sha256: String, block: (PdfRenderer) -> T): T =
        withContext(dispatcher) {
            lock.withLock {
                ParcelFileDescriptor.open(files.fileFor(sha256), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val renderer = PdfRenderer(fd)
                    try {
                        block(renderer)
                    } finally {
                        renderer.close()
                    }
                }
            }
        }

    private companion object {
        /** Tope de ancho para no agotar la memoria con un zoom grande. */
        const val MAX_WIDTH = 4096

        /** Con zoom, la página «mide» mucho más que la pantalla, pero solo se dibuja un rectángulo. */
        const val MAX_PAGE_WIDTH = 40_000
    }
}
