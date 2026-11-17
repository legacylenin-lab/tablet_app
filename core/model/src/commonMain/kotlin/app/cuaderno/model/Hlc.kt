package app.cuaderno.model

/**
 * Reloj lógico híbrido (HLC). Ordena cambios de dos dispositivos aunque sus
 * relojes no coincidan. Ver docs/arquitectura.md §4.
 *
 * Orden: primero [wallMillis], luego [counter], luego [deviceId]. Nunca hay empates.
 * [deviceId] es el identificador del dispositivo que emitió el valor.
 */
data class Hlc(
    val wallMillis: Long,
    val counter: Int,
    val deviceId: EntityId,
) : Comparable<Hlc> {

    init {
        require(wallMillis in 0..MAX_WALL) { "wallMillis fuera de rango: $wallMillis" }
        require(counter in 0..MAX_COUNTER) { "counter fuera de rango: $counter" }
    }

    override fun compareTo(other: Hlc): Int {
        if (wallMillis != other.wallMillis) return wallMillis.compareTo(other.wallMillis)
        if (counter != other.counter) return counter.compareTo(other.counter)
        return deviceId.value.compareTo(other.deviceId.value)
    }

    /**
     * Forma textual ordenable: 15 dígitos de wallMillis, '-', contador en
     * hexadecimal de 4 dígitos, '-', deviceId. Comparar estos textos da el mismo
     * orden que comparar los [Hlc].
     */
    fun toText(): String =
        wallMillis.toString().padStart(WALL_DIGITS, '0') + "-" +
            counter.toString(16).padStart(COUNTER_DIGITS, '0') + "-" + deviceId.value

    companion object {
        const val WALL_DIGITS = 15
        const val COUNTER_DIGITS = 4
        const val MAX_WALL = 999_999_999_999_999L
        const val MAX_COUNTER = 0xFFFF

        /** Lee la forma textual de [toText]. Lanza [IllegalArgumentException] si no es válida. */
        fun parse(text: String): Hlc {
            val minLength = WALL_DIGITS + 1 + COUNTER_DIGITS + 1 + 1
            require(text.length >= minLength) { "HLC inválido: $text" }
            require(text[WALL_DIGITS] == '-' && text[WALL_DIGITS + 1 + COUNTER_DIGITS] == '-') {
                "HLC inválido: $text"
            }
            val wall = text.substring(0, WALL_DIGITS).toLongOrNull()
            val counter = text.substring(WALL_DIGITS + 1, WALL_DIGITS + 1 + COUNTER_DIGITS).toIntOrNull(16)
            require(wall != null && counter != null) { "HLC inválido: $text" }
            return Hlc(wall, counter, EntityId.parse(text.substring(WALL_DIGITS + 1 + COUNTER_DIGITS + 1)))
        }
    }
}

/**
 * Emite valores [Hlc] para un dispositivo. [last] es el último valor emitido;
 * el que usa la app se guarda en la base de datos para no retroceder tras un reinicio.
 *
 * Esta clase no lee el reloj: recibe la hora actual como parámetro, para poder probarla.
 */
class HlcClock(
    private val deviceId: EntityId,
    initial: Hlc? = null,
) {
    var last: Hlc = initial ?: Hlc(0, 0, deviceId)
        private set

    /** Evento local (el usuario cambia algo). */
    fun next(nowMillis: Long): Hlc {
        val wall = maxOf(nowMillis, last.wallMillis)
        val counter = if (wall == last.wallMillis) last.counter + 1 else 0
        return emit(wall, counter)
    }

    /** Se recibió un valor de otro dispositivo (v2). Algoritmo HLC estándar. */
    fun receive(remote: Hlc, nowMillis: Long): Hlc {
        val wall = maxOf(nowMillis, last.wallMillis, remote.wallMillis)
        val counter = when {
            wall == last.wallMillis && wall == remote.wallMillis -> maxOf(last.counter, remote.counter) + 1
            wall == last.wallMillis -> last.counter + 1
            wall == remote.wallMillis -> remote.counter + 1
            else -> 0
        }
        return emit(wall, counter)
    }

    private fun emit(wall: Long, counter: Int): Hlc {
        check(counter <= Hlc.MAX_COUNTER) { "Contador del HLC desbordado" }
        last = Hlc(wall, counter, deviceId)
        return last
    }

    companion object {
        /** Más de 24 h de adelanto se considera sospechoso (se avisa, no se rechaza). */
        const val SUSPICIOUS_SKEW_MILLIS = 24L * 60 * 60 * 1000

        fun isSuspiciousSkew(remote: Hlc, nowMillis: Long): Boolean =
            remote.wallMillis - nowMillis > SUSPICIOUS_SKEW_MILLIS
    }
}
