package app.cuaderno.ink

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageViewportTest {
    // Hoja A4 ajustada: 700 x 990 dentro de un área de 1000 x 1000.
    private val fw = 700f
    private val fh = 990f
    private val vw = 1000f
    private val vh = 1000f

    private fun fit() = PageViewport.fit(fw, fh, vw, vh)

    private fun PageViewport.gesture(cx: Float = 500f, cy: Float = 500f, dx: Float = 0f, dy: Float = 0f, z: Float = 1f) =
        transformed(cx, cy, dx, dy, z, fw, fh, vw, vh)

    private fun near(a: Float, b: Float) = abs(a - b) < 0.01f

    @Test
    fun alInicioLaHojaEstaCentradaConZoomUno() {
        val v = fit()
        assertEquals(1f, v.zoom)
        assertEquals(150f, v.offsetX)
        assertEquals(5f, v.offsetY)
    }

    @Test
    fun conZoomUnoDesplazarNoMueveLaHoja() {
        val v = fit().gesture(dx = 200f, dy = -300f)
        assertEquals(fit(), v)
    }

    @Test
    fun elZoomTieneUnMinimoYUnMaximo() {
        assertEquals(1f, fit().gesture(z = 0.2f).zoom)
        assertEquals(PageViewport.MAX_ZOOM, fit().gesture(z = 100f).zoom)
    }

    @Test
    fun elPuntoBajoLosDedosSeQuedaBajoLosDedosAlHacerZoom() {
        // El punto de la hoja bajo (500, 300) antes del zoom: ((500-150)/1, (300-5)/1) = (350, 295).
        val v = fit().gesture(cx = 500f, cy = 300f, z = 2f)
        assertEquals(2f, v.zoom)
        assertTrue(near(500f, v.offsetX + 350f * 2f), "x: ${v.offsetX}")
        assertTrue(near(300f, v.offsetY + 295f * 2f), "y: ${v.offsetY}")
    }

    @Test
    fun conZoomSePuedeDesplazarPeroNoSalirDeLaHoja() {
        val zoomed = fit().gesture(z = 2f) // hoja de 1400 x 1980 en un área de 1000 x 1000
        val derecha = zoomed.gesture(dx = 5000f, dy = 5000f)
        assertEquals(0f, derecha.offsetX)
        assertEquals(0f, derecha.offsetY)
        val izquierda = zoomed.gesture(dx = -5000f, dy = -5000f)
        assertEquals(1000f - 1400f, izquierda.offsetX)
        assertEquals(1000f - 1980f, izquierda.offsetY)
    }

    @Test
    fun siLaHojaEsMasChicaQueElAreaQuedaCentrada() {
        val v = fit().gesture(z = 1.2f) // 840 de ancho: cabe a lo ancho, no a lo alto (1188)
        assertTrue(near(80f, v.offsetX), "x: ${v.offsetX}")
        assertTrue(v.offsetY <= 0f)
    }

    @Test
    fun alAlejarElZoomVuelveACentrarLaHoja() {
        val v = fit().gesture(z = 3f).gesture(z = 0.1f)
        assertEquals(fit(), v)
    }
}
