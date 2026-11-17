package app.cuaderno.model

import kotlin.jvm.JvmInline

/**
 * Identificador global de cualquier entidad o dispositivo: un UUID v4 en minúsculas y con guiones
 * (docs/arquitectura.md §3.1). Es una clase de valor sobre `String`: en tiempo de ejecución es solo
 * el texto, pero el compilador impide mezclar, por ejemplo, el id de una página con el de un cuaderno.
 *
 * Solo se crea con [parse] (valida el formato) o con un `IdGenerator`.
 */
@JvmInline
value class EntityId private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        private const val LENGTH = 36

        /** @throws IllegalArgumentException si [text] no es un UUID v4 en minúsculas con guiones. */
        fun parse(text: String): EntityId {
            require(isValid(text)) { "Identificador inválido: \"$text\"" }
            return EntityId(text)
        }

        fun isValid(text: String): Boolean {
            if (text.length != LENGTH) return false
            for (i in 0 until LENGTH) {
                val c = text[i]
                val ok = when (i) {
                    8, 13, 18, 23 -> c == '-'
                    14 -> c == '4'
                    19 -> c == '8' || c == '9' || c == 'a' || c == 'b'
                    else -> c in '0'..'9' || c in 'a'..'f'
                }
                if (!ok) return false
            }
            return true
        }
    }
}
