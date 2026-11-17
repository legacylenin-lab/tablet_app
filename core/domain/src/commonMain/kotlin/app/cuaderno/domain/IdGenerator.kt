package app.cuaderno.domain

import app.cuaderno.model.EntityId
import kotlin.random.Random

/** Fabrica identificadores nuevos. Es una interfaz para poder usar identificadores predecibles en las pruebas. */
interface IdGenerator {
    fun newId(): EntityId
}

/** UUID v4 aleatorio (122 bits al azar: las colisiones son prácticamente imposibles). */
class RandomIdGenerator(private val random: Random = Random.Default) : IdGenerator {
    override fun newId(): EntityId {
        val b = random.nextBytes(16)
        b[6] = ((b[6].toInt() and 0x0F) or 0x40).toByte() // versión 4
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte() // variante
        val hex = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return EntityId.parse(
            "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}",
        )
    }
}
