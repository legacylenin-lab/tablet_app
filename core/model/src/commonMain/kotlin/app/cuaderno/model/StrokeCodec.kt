package app.cuaderno.model

/**
 * Un punto de un trazo. Coordenadas en décimas de milímetro (notas) o de punto PDF
 * (anotaciones); [pressure] de 0 a 255; [tMs] son milisegundos desde el primer punto.
 */
data class StrokePoint(val x: Int, val y: Int, val pressure: Int, val tMs: Long)

/**
 * Codifica y decodifica trazos como bytes compactos (docs/arquitectura.md §5.1).
 *
 * ```
 * byte 0    versión del formato (= 1)
 * varint    número de puntos N
 * punto 0   x, y absolutos (varint zigzag), presión (1 byte)       [t = 0, no se guarda]
 * punto i   dx, dy (varint zigzag), presión (1 byte), dt en ms (varint)
 * ```
 *
 * El formato está versionado: uno nuevo usará otro número y el lector seguirá
 * entendiendo los anteriores.
 */
object StrokeCodec {
    const val VERSION_1 = 1

    /**
     * @throws IllegalArgumentException si el primer punto no tiene t = 0, si el tiempo
     * retrocede o si la presión no está entre 0 y 255.
     */
    fun encode(points: List<StrokePoint>): ByteArray {
        val out = ByteSink()
        out.byte(VERSION_1)
        out.varint(points.size.toLong())
        var prev: StrokePoint? = null
        for (p in points) {
            require(p.pressure in 0..255) { "Presión fuera de rango: ${p.pressure}" }
            if (prev == null) {
                require(p.tMs == 0L) { "El primer punto debe tener t = 0" }
                out.varint(zigzag(p.x.toLong()))
                out.varint(zigzag(p.y.toLong()))
                out.byte(p.pressure)
            } else {
                val dt = p.tMs - prev.tMs
                require(dt >= 0) { "El tiempo no puede retroceder" }
                out.varint(zigzag(p.x.toLong() - prev.x))
                out.varint(zigzag(p.y.toLong() - prev.y))
                out.byte(p.pressure)
                out.varint(dt)
            }
            prev = p
        }
        return out.toByteArray()
    }

    /** @throws IllegalArgumentException si los bytes están truncados, corruptos o son de una versión desconocida. */
    fun decode(bytes: ByteArray): List<StrokePoint> {
        val src = ByteSource(bytes)
        val version = src.byte()
        require(version == VERSION_1) { "Versión de trazo no soportada: $version" }
        val count = src.varint()
        // Cada punto ocupa al menos 3 bytes: evita reservar memoria por un número falso.
        require(count in 0..(src.remaining / 3L)) { "Número de puntos inválido: $count" }
        val points = ArrayList<StrokePoint>(count.toInt())
        var x = 0L
        var y = 0L
        var t = 0L
        for (i in 0 until count.toInt()) {
            if (i == 0) {
                x = unzigzag(src.varint())
                y = unzigzag(src.varint())
            } else {
                x += unzigzag(src.varint())
                y += unzigzag(src.varint())
            }
            val pressure = src.byte()
            if (i > 0) t += src.varint()
            require(x in Int.MIN_VALUE..Int.MAX_VALUE && y in Int.MIN_VALUE..Int.MAX_VALUE) {
                "Coordenada fuera de rango"
            }
            points += StrokePoint(x.toInt(), y.toInt(), pressure, t)
        }
        require(src.remaining == 0) { "Sobran bytes al final del trazo" }
        return points
    }

    private fun zigzag(v: Long): Long = (v shl 1) xor (v shr 63)
    private fun unzigzag(v: Long): Long = (v ushr 1) xor -(v and 1)
}

private class ByteSink {
    private var buf = ByteArray(64)
    private var size = 0

    fun byte(b: Int) {
        if (size == buf.size) buf = buf.copyOf(size * 2)
        buf[size++] = b.toByte()
    }

    /** Entero sin signo de 64 bits en bloques de 7 bits (LEB128). */
    fun varint(value: Long) {
        var v = value
        while ((v and 0x7F.inv().toLong()) != 0L) {
            byte(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        byte(v.toInt())
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

private class ByteSource(private val bytes: ByteArray) {
    private var pos = 0
    val remaining: Int get() = bytes.size - pos

    fun byte(): Int {
        require(pos < bytes.size) { "Trazo truncado" }
        return bytes[pos++].toInt() and 0xFF
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(shift < 70) { "Entero demasiado largo" }
            val b = byte()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
}
