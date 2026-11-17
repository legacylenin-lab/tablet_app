package app.cuaderno.domain

import app.cuaderno.model.EntityId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// CU-06: generación de identificadores
class RandomIdGeneratorTest {

    @Test
    fun generaIdentificadoresValidosYUnicos() {
        val generador = RandomIdGenerator()
        val ids = List(10_000) { generador.newId() }
        assertEquals(10_000, ids.toSet().size)
        assertTrue(ids.all { EntityId.isValid(it.value) })
    }

    @Test
    fun conLaMismaSemillaDaLosMismosIdentificadores() {
        val a = RandomIdGenerator(Random(7))
        val b = RandomIdGenerator(Random(7))
        assertEquals(List(5) { a.newId() }, List(5) { b.newId() })
    }
}
