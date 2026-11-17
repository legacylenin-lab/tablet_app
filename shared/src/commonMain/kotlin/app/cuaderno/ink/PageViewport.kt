package app.cuaderno.ink

import kotlin.math.max
import kotlin.math.min

/**
 * Qué parte de la hoja se ve en pantalla: el zoom y dónde queda la esquina de la hoja (RF-03: el dedo
 * desplaza y hace zoom, nunca escribe). Lógica pura, sin Android, para poder probarla.
 *
 * [zoom] 1 = la hoja entera ajustada a la pantalla. [offsetX] y [offsetY] son la posición, en píxeles,
 * de la esquina superior izquierda de la hoja dentro del área visible.
 */
data class PageViewport(val zoom: Float, val offsetX: Float, val offsetY: Float) {

    /**
     * Aplica un gesto de dos dedos o de uno: [centroidX], [centroidY] es el centro actual de los dedos
     * (en píxeles del área visible), [panX], [panY] lo que se movió desde el último evento y [zoomFactor]
     * cuánto cambió la distancia entre los dedos (1 = nada). El punto de la hoja bajo los dedos se queda bajo ellos.
     * [fitWidth] y [fitHeight] son el tamaño de la hoja con zoom 1; [viewWidth] y [viewHeight] el del área visible.
     */
    fun transformed(
        centroidX: Float, centroidY: Float, panX: Float, panY: Float, zoomFactor: Float,
        fitWidth: Float, fitHeight: Float, viewWidth: Float, viewHeight: Float,
    ): PageViewport {
        val newZoom = (zoom * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val f = newZoom / zoom
        val x = centroidX - (centroidX - panX - offsetX) * f
        val y = centroidY - (centroidY - panY - offsetY) * f
        return PageViewport(newZoom, x, y).clamped(fitWidth, fitHeight, viewWidth, viewHeight)
    }

    /** Evita perder la hoja: si es más chica que el área, queda centrada; si es más grande, no deja huecos en los bordes. */
    fun clamped(fitWidth: Float, fitHeight: Float, viewWidth: Float, viewHeight: Float): PageViewport {
        val pageW = fitWidth * zoom
        val pageH = fitHeight * zoom
        return copy(offsetX = clampAxis(offsetX, pageW, viewWidth), offsetY = clampAxis(offsetY, pageH, viewHeight))
    }

    private fun clampAxis(offset: Float, page: Float, view: Float): Float =
        if (page <= view) (view - page) / 2f else min(0f, max(view - page, offset))

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f

        /** La hoja entera, centrada. */
        fun fit(fitWidth: Float, fitHeight: Float, viewWidth: Float, viewHeight: Float) =
            PageViewport(1f, 0f, 0f).clamped(fitWidth, fitHeight, viewWidth, viewHeight)
    }
}
