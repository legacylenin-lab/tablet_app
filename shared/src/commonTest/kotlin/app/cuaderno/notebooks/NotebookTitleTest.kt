package app.cuaderno.notebooks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotebookTitleTest {
    @Test
    fun quitaEspaciosDeLosExtremosYJuntaLosRepetidos() {
        assertEquals("Cálculo 1", NotebookTitle.normalize("   Cálculo    1  "))
    }

    @Test
    fun unTituloVacioOSoloEspaciosNoSePermite() {
        assertNull(NotebookTitle.normalize(""))
        assertNull(NotebookTitle.normalize("   \n\t "))
    }

    @Test
    fun unTituloLargoSeCortaAlMaximo() {
        val result = NotebookTitle.normalize("a".repeat(200))!!
        assertEquals(NotebookTitle.MAX_LENGTH, result.length)
    }

    @Test
    fun alCortarNoDejaUnEspacioAlFinal() {
        val raw = "a".repeat(NotebookTitle.MAX_LENGTH - 1) + " bbb"
        assertEquals("a".repeat(NotebookTitle.MAX_LENGTH - 1), NotebookTitle.normalize(raw))
    }

    @Test
    fun conservaAcentosYEnies() {
        assertEquals("Álgebra lineal — año 2", NotebookTitle.normalize(" Álgebra lineal — año 2 "))
    }
}
