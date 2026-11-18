package app.cuaderno.testing

import app.cuaderno.domain.PdfEngine
import app.cuaderno.domain.RenderedPage

/** Un rectángulo que se pidió dibujar con zoom. */
data class RegionRequest(val page: Int, val pageWidthPx: Int, val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Motor de PDF de prueba: anota qué se pidió dibujar. Cada página mide [pageHeightRatio] veces su
 * ancho (A4 ≈ 1,414). Con [failWith], dibujar falla.
 */
class FakePdfEngine(var pages: Int = 10, private val pageHeightRatio: Float = 1.5f) : PdfEngine {
    val rendered = mutableListOf<Pair<Int, Int>>()
    val regions = mutableListOf<RegionRequest>()
    var failWith: Exception? = null

    override suspend fun pageCount(sha256: String): Int = pages

    override suspend fun renderPage(sha256: String, pageIndex: Int, widthPx: Int): RenderedPage {
        failWith?.let { throw it }
        rendered += pageIndex to widthPx
        val height = (widthPx * pageHeightRatio).toInt()
        return RenderedPage(widthPx, height, IntArray(widthPx * height) { -1 })
    }

    override suspend fun renderRegion(
        sha256: String, pageIndex: Int, pageWidthPx: Int, left: Int, top: Int, width: Int, height: Int,
    ): RenderedPage {
        failWith?.let { throw it }
        regions += RegionRequest(pageIndex, pageWidthPx, left, top, width, height)
        return RenderedPage(width, height, IntArray(width * height) { -1 })
    }
}
