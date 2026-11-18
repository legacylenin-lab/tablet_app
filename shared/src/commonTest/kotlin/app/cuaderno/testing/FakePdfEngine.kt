package app.cuaderno.testing

import app.cuaderno.domain.PdfEngine
import app.cuaderno.domain.RenderedPage

/** Motor de PDF de prueba: anota qué se pidió dibujar. Con [failWith], dibujar falla. */
class FakePdfEngine(var pages: Int = 10) : PdfEngine {
    val rendered = mutableListOf<Pair<Int, Int>>()
    var failWith: Exception? = null

    override suspend fun pageCount(sha256: String): Int = pages

    override suspend fun renderPage(sha256: String, pageIndex: Int, widthPx: Int): RenderedPage {
        failWith?.let { throw it }
        rendered += pageIndex to widthPx
        return RenderedPage(widthPx, 2, IntArray(widthPx * 2) { -1 })
    }
}
