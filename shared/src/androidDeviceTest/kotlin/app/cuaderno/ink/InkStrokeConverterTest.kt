package app.cuaderno.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import app.cuaderno.model.EntityId
import app.cuaderno.model.Hlc
import app.cuaderno.model.Meta
import app.cuaderno.model.StrokeCodec
import app.cuaderno.model.StrokePoint
import app.cuaderno.model.StrokeTool
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.cuaderno.model.Stroke as ModelStroke

/**
 * Prueba instrumentada: corre en la tablet o en el emulador, con la librería de tinta real.
 * Comprueba que un trazo se puede guardar y reconstruir sin perder lo importante.
 */
class InkStrokeConverterTest {

    private val id = EntityId.parse("00000000-0000-4000-8000-000000000001")
    private val brush = Brush.createWithColorIntArgb(StockBrushes.pressurePen(), 0xFF112233.toInt(), 4f, BRUSH_EPSILON_UNITS)

    /** Un trazo con forma de espiral: coordenadas decimales, 8 ms entre puntos y presión variable. */
    private fun spiral(withPressure: Boolean = true, count: Int = 300): Stroke {
        val batch = MutableStrokeInputBatch()
        for (i in 0 until count) {
            val angle = i * 0.15f
            val radius = 200f + i * 2.3f
            val x = 1000f + radius * cos(angle)
            val y = 1500f + radius * sin(angle)
            if (withPressure) {
                batch.add(InputToolType.STYLUS, x, y, 5_000L + i * 8, InkConversion.STROKE_UNIT_LENGTH_CM, 0.2f + (i % 50) / 70f)
            } else {
                batch.add(InputToolType.STYLUS, x, y, 5_000L + i * 8, InkConversion.STROKE_UNIT_LENGTH_CM)
            }
        }
        return Stroke(brush, batch)
    }

    private fun saved(bytes: ByteArray, color: Int = 0xFF112233.toInt(), width: Float = 4f) =
        ModelStroke(Meta.created(id, Hlc(1, 0, id), id), id, StrokeTool.PEN, color, width, bytes)

    @Test
    fun unTrazoSeGuardaYSeReconstruyeConLoImportanteIntacto() {
        val original = spiral()
        val nuevo = assertNotNull(original.toNewStroke())

        // Lo que se guarda conserva el estilo del pincel.
        assertEquals(StrokeTool.PEN, nuevo.tool)
        assertEquals(0xFF112233.toInt(), nuevo.colorArgb)
        assertEquals(4f, nuevo.baseWidth)
        val puntos = StrokeCodec.decode(nuevo.points)
        assertEquals(original.inputs.size, puntos.size)
        assertEquals(0L, puntos.first().tMs)
        assertEquals((original.inputs.size - 1) * 8L, puntos.last().tMs)

        // Reconstruir da puntos a menos de media unidad (0,05 mm) y la misma presión salvo el redondeo.
        val rebuilt = assertNotNull(saved(nuevo.points).toInkStroke())
        assertEquals(original.inputs.size, rebuilt.inputs.size)
        val a = StrokeInput()
        val b = StrokeInput()
        for (i in 0 until original.inputs.size) {
            original.inputs.populate(i, a)
            rebuilt.inputs.populate(i, b)
            assertTrue(abs(a.x - b.x) <= 0.5f && abs(a.y - b.y) <= 0.5f, "punto $i: (${a.x},${a.y}) vs (${b.x},${b.y})")
            assertTrue(abs(a.pressure - b.pressure) <= 1f / 255f, "presión $i: ${a.pressure} vs ${b.pressure}")
        }
        assertEquals(0xFF112233.toInt(), rebuilt.brush.colorIntArgb)
        assertEquals(4f, rebuilt.brush.size)
    }

    @Test
    fun elTrazoReconstruidoTieneFormaParaDibujarse() {
        val rebuilt = assertNotNull(saved(assertNotNull(spiral().toNewStroke()).points).toInkStroke())
        assertTrue(rebuilt.shape.getRenderGroupCount() > 0)
        assertNotNull(rebuilt.shape.computeBoundingBox())
    }

    @Test
    fun sinPresionSeGuardaConLaPresionMaxima() {
        val nuevo = assertNotNull(spiral(withPressure = false).toNewStroke())
        assertTrue(StrokeCodec.decode(nuevo.points).all { it.pressure == 255 })
    }

    @Test
    fun guardarYReconstruirDosVecesDaLoMismo() {
        val primero = assertNotNull(spiral().toNewStroke())
        val segundo = assertNotNull(assertNotNull(saved(primero.points).toInkStroke()).toNewStroke())
        assertEquals(StrokeCodec.decode(primero.points), StrokeCodec.decode(segundo.points))
    }

    @Test
    fun unTrazoDeUnSoloPuntoSePuedeGuardarYReconstruir() {
        val batch = MutableStrokeInputBatch().add(InputToolType.STYLUS, 100f, 200f, 0L, InkConversion.STROKE_UNIT_LENGTH_CM, 0.5f)
        val nuevo = assertNotNull(Stroke(brush, batch).toNewStroke())
        assertEquals(listOf(StrokePoint(100, 200, 128, 0)), StrokeCodec.decode(nuevo.points))
        assertNotNull(saved(nuevo.points).toInkStroke())
    }

    @Test
    fun datosVaciosODanadosNoRompenLaCarga() {
        assertNull(saved(StrokeCodec.encode(emptyList())).toInkStroke())
        assertNull(saved(byteArrayOf(9, 9, 9)).toInkStroke())
        assertNull(saved(byteArrayOf()).toInkStroke())
        val valido = StrokeCodec.encode(listOf(StrokePoint(1, 2, 3, 0), StrokePoint(4, 5, 6, 8)))
        assertNull(saved(valido.copyOf(valido.size - 1)).toInkStroke())
    }

    @Test
    fun puntosRepetidosEnLosDatosGuardadosSeIgnoran() {
        val conRepetidos = StrokeCodec.encode(
            listOf(StrokePoint(1, 1, 100, 0), StrokePoint(1, 1, 100, 0), StrokePoint(5, 5, 100, 8)),
        )
        assertEquals(2, assertNotNull(saved(conRepetidos).toInkStroke()).inputs.size)
    }
}
