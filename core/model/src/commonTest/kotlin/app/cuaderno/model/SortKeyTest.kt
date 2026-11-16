package app.cuaderno.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SortKeyTest {

    private fun assertEntre(antes: String?, despues: String?, clave: String) {
        if (antes != null) assertTrue(antes < clave, "$antes < $clave")
        if (despues != null) assertTrue(clave < despues, "$clave < $despues")
        assertTrue(!clave.endsWith('0'), "no termina en 0: $clave")
    }

    @Test
    fun primeraClaveYDespuesDeTodoYAntesDeTodo() {
        val primera = SortKey.first
        assertEntre(null, null, primera)
        assertEntre(primera, null, SortKey.between(primera, null))
        assertEntre(null, primera, SortKey.between(null, primera))
    }

    @Test
    fun siempreDaUnaClaveIntermedia() {
        val pares = listOf("1" to "2", "1" to "11", "Vz" to "W", "a" to "b", "z" to "zz", "01" to "1", "y" to "z")
        for ((a, b) in pares) assertEntre(a, b, SortKey.between(a, b))
    }

    @Test
    fun milInsercionesSeguidasEnElMismoHueco() {
        // Siempre se inserta justo después de la primera clave: el hueco se achica cada vez.
        val inicio = SortKey.first
        val fin = SortKey.between(inicio, null)
        var limiteSuperior = fin
        repeat(1_000) {
            val nueva = SortKey.between(inicio, limiteSuperior)
            assertEntre(inicio, limiteSuperior, nueva)
            limiteSuperior = nueva
        }
    }

    @Test
    fun milInsercionesAlFinalYAlPrincipio() {
        var ultima = SortKey.first
        repeat(1_000) {
            val nueva = SortKey.between(ultima, null)
            assertEntre(ultima, null, nueva)
            ultima = nueva
        }
        var primera = SortKey.first
        repeat(1_000) {
            val nueva = SortKey.between(null, primera)
            assertEntre(null, primera, nueva)
            primera = nueva
        }
    }

    @Test
    fun insercionesAleatoriasMantienenLaListaOrdenada() {
        val random = Random(99)
        val lista = mutableListOf(SortKey.first)
        repeat(2_000) {
            val posicion = random.nextInt(0, lista.size + 1)
            val antes = lista.getOrNull(posicion - 1)
            val despues = lista.getOrNull(posicion)
            val nueva = SortKey.between(antes, despues)
            lista.add(posicion, nueva)
        }
        assertEquals(lista.sorted(), lista)
        assertEquals(lista.size, lista.toSet().size)
    }

    @Test
    fun rechazaClavesInvalidas() {
        assertFailsWith<IllegalArgumentException> { SortKey.between("", null) }
        assertFailsWith<IllegalArgumentException> { SortKey.between("a0", null) }
        assertFailsWith<IllegalArgumentException> { SortKey.between("a-b", null) }
        assertFailsWith<IllegalArgumentException> { SortKey.between("b", "a") }
        assertFailsWith<IllegalArgumentException> { SortKey.between("a", "a") }
    }
}
