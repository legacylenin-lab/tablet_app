package app.cuaderno.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// CU-07: clave de orden por título
class TitleSortTest {

    private fun ordenados(vararg titulos: String) = titulos.sortedBy { TitleSort.of(it) }

    @Test
    fun laTildeNoCambiaElOrden() {
        assertEquals(listOf("Álgebra", "Zebra"), ordenados("Zebra", "Álgebra"))
        assertEquals("algebra lineal", TitleSort.of("Álgebra Lineal"))
        assertEquals("aeiou", TitleSort.of("áéíóú"))
        assertEquals("aeiou", TitleSort.of("äëïöü"))
        assertEquals("aeiou", TitleSort.of("àèìòù"))
    }

    @Test
    fun laEnyeEsUnaLetraPropiaEntreLaNYLaO() {
        assertEquals(listOf("nube", "ñandú", "oso"), ordenados("oso", "ñandú", "nube"))
        assertEquals(listOf("nz", "ñ", "o"), ordenados("o", "ñ", "nz"))
        assertEquals("n${TitleSort.ENYE_MARK}andu", TitleSort.of("ÑANDÚ"))
    }

    @Test
    fun mayusculasYMinusculasDanElMismoOrden() {
        assertEquals(TitleSort.of("cálculo"), TitleSort.of("CÁLCULO"))
        assertEquals(TitleSort.of("Física II"), TitleSort.of("fÍsica ii"))
    }

    @Test
    fun losEspaciosSeColapsanYSeRecortan() {
        assertEquals("a b", TitleSort.of("  a    b  "))
        assertEquals("a b", TitleSort.of("a\t\n b"))
        assertEquals("", TitleSort.of("   "))
        assertEquals("", TitleSort.of(""))
    }

    @Test
    fun textoDescompuestoDaElMismoResultadoQueElCompuesto() {
        // "á" como "a" + acento combinado, y "ñ" como "n" + tilde combinada (así vienen algunos PDF).
        assertEquals(TitleSort.of("álgebra"), TitleSort.of("álgebra"))
        assertEquals(TitleSort.of("ñandú"), TitleSort.of("ñandú"))
        // Una tilde combinada sobre otra letra no inventa una ñ.
        assertEquals("a", TitleSort.of("ã"))
    }

    @Test
    fun numerosYSimbolosSeConservan() {
        assertEquals("calculo 2", TitleSort.of("Cálculo 2"))
        assertEquals("tema-1 (parcial)", TitleSort.of("Tema-1 (Parcial)"))
        assertTrue(TitleSort.of("Cálculo 1") < TitleSort.of("Cálculo 2"))
    }

    @Test
    fun otrasLetrasLatinasSeSimplifican() {
        assertEquals("c", TitleSort.of("Ç"))
        assertEquals("y", TitleSort.of("ÿ"))
    }

    @Test
    fun esEstableYNoCambiaAlAplicarseDosVeces() {
        for (t in listOf("Álgebra Lineal", "ñandú", "  Física   II ", "ÇÀ", "á")) {
            assertEquals(TitleSort.of(t), TitleSort.of(TitleSort.of(t)))
        }
    }

    @Test
    fun unaListaRealSeOrdenaComoEnUnDiccionarioEspanol() {
        val lista = listOf(
            "Zoología", "Ñoño", "Álgebra", "Biología", "cálculo", "Ecuaciones diferenciales",
            "Estadística", "Física", "Nutrición", "Óptica", "Química",
        )
        assertEquals(
            listOf(
                "Álgebra", "Biología", "cálculo", "Ecuaciones diferenciales", "Estadística", "Física",
                "Nutrición", "Ñoño", "Óptica", "Química", "Zoología",
            ),
            ordenados(*lista.shuffled().toTypedArray()),
        )
    }
}
