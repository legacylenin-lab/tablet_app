package app.cuaderno.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// CU-06: identificadores
class EntityIdTest {

    private val valido = "6f1c2a4e-9b3d-4c7a-8e51-0d2f3a4b5c6d"

    @Test
    fun aceptaUnUuidV4EnMinusculasConGuiones() {
        val id = EntityId.parse(valido)
        assertEquals(valido, id.value)
        assertEquals(valido, id.toString())
    }

    @Test
    fun aceptaLasCuatroVariantesPermitidas() {
        for (v in listOf('8', '9', 'a', 'b')) {
            assertTrue(EntityId.isValid("00000000-0000-4000-${v}000-000000000000"), "variante $v")
        }
    }

    @Test
    fun rechazaFormatosInvalidos() {
        val invalidos = listOf(
            "",
            "dev-1",
            valido.uppercase(),                       // mayúsculas
            valido.replace("-", ""),                  // sin guiones
            valido + "0",                             // largo de más
            valido.dropLast(1),                       // largo de menos
            valido.replaceRange(14, 15, "1"),         // versión distinta de 4
            valido.replaceRange(19, 20, "c"),         // variante inválida
            valido.replaceRange(0, 1, "g"),           // carácter no hexadecimal
            valido.replaceRange(8, 9, "_"),           // guion cambiado
            " $valido",                               // espacio al inicio
        )
        for (texto in invalidos) {
            assertFalse(EntityId.isValid(texto), "debería ser inválido: \"$texto\"")
            assertFailsWith<IllegalArgumentException> { EntityId.parse(texto) }
        }
    }

    @Test
    fun dosIdentificadoresConElMismoTextoSonIguales() {
        assertEquals(EntityId.parse(valido), EntityId.parse(valido))
        assertEquals(EntityId.parse(valido).hashCode(), EntityId.parse(valido).hashCode())
    }

    @Test
    fun elTextoDelHlcConservaElIdentificadorDelDispositivo() {
        val hlc = Hlc(5, 1, EntityId.parse(valido))
        assertEquals(hlc, Hlc.parse(hlc.toText()))
        assertTrue(hlc.toText().endsWith(valido))
    }
}
