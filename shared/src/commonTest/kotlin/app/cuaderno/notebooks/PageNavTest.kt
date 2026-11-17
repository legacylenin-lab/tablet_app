package app.cuaderno.notebooks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageNavTest {
    @Test
    fun laPrimeraPaginaNoPuedeRetroceder() {
        val nav = PageNav.at(0, 3)
        assertEquals("Página 1 de 3", nav.label)
        assertFalse(nav.canGoBack)
        assertTrue(nav.canGoForward)
    }

    @Test
    fun laUltimaPaginaNoPuedeAvanzar() {
        val nav = PageNav.at(2, 3)
        assertEquals("Página 3 de 3", nav.label)
        assertTrue(nav.canGoBack)
        assertFalse(nav.canGoForward)
    }

    @Test
    fun unaPosicionFueraDeLaListaSeAjusta() {
        assertEquals(2, PageNav.clampIndex(9, 3))
        assertEquals(0, PageNav.clampIndex(-4, 3))
        assertEquals(0, PageNav.clampIndex(5, 0))
        assertEquals("Página 1 de 1", PageNav.at(5, 0).label)
    }

    @Test
    fun conUnaSolaPaginaNoSePuedeIrANingunLado() {
        val nav = PageNav.at(0, 1)
        assertFalse(nav.canGoBack)
        assertFalse(nav.canGoForward)
    }

    @Test
    fun alEliminarSeQuedaEnLaMismaPosicionOEnLaAnteriorSiEraLaUltima() {
        assertEquals(1, PageNav.indexAfterDelete(1, 4)) // la siguiente ocupa su lugar
        assertEquals(2, PageNav.indexAfterDelete(3, 4)) // era la última
        assertEquals(0, PageNav.indexAfterDelete(0, 2))
        assertEquals(0, PageNav.indexAfterDelete(0, 1))
    }
}
