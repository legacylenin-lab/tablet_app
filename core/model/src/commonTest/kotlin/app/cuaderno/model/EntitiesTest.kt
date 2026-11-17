package app.cuaderno.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntitiesTest {

    private val device = testId(1)
    private val author = testId(2)

    private fun meta(wall: Long = 100) =
        Meta.created(testId(100 + wall.toInt()), Hlc(wall, 0, device), author)

    @Test
    fun entidadNuevaTieneCreacionIgualActualizacionYNoEstaBorrada() {
        val m = meta()
        assertEquals(m.createdHlc, m.updatedHlc)
        assertEquals(device, m.deviceId)
        assertNull(m.deletedHlc)
        assertFalse(m.isDeleted)
    }

    @Test
    fun actualizarCambiaSoloLaActualizacion() {
        val m = meta(100)
        val u = m.updated(Hlc(200, 0, device))
        assertEquals(m.createdHlc, u.createdHlc)
        assertEquals(Hlc(200, 0, device), u.updatedHlc)
        assertFalse(u.isDeleted)
    }

    @Test
    fun actualizarDesdeOtroDispositivoRegistraQuienLoHizo() {
        val u = meta().updated(Hlc(200, 0, testId(3)), deviceId = testId(3))
        assertEquals(testId(3), u.deviceId)
        assertEquals(author, u.authorId)
    }

    @Test
    fun borrarEsSuaveYCuentaComoCambio() {
        val h = Hlc(300, 0, device)
        val d = meta().deleted(h)
        assertTrue(d.isDeleted)
        assertEquals(h, d.deletedHlc)
        assertEquals(h, d.updatedHlc)
    }

    @Test
    fun paginaPorDefectoEsA4Blanca() {
        val p = Page(meta(), notebookId = testId(10), sortKey = "a")
        assertEquals(210, p.widthMm)
        assertEquals(297, p.heightMm)
        assertEquals(PageTemplate.BLANK, p.template)
    }

    @Test
    fun trazosConLosMismosPuntosSonIguales() {
        val m = meta()
        val a = Stroke(m, testId(11), StrokeTool.PEN, 0xFF000000.toInt(), 3f, byteArrayOf(1, 2, 3))
        val b = Stroke(m, testId(11), StrokeTool.PEN, 0xFF000000.toInt(), 3f, byteArrayOf(1, 2, 3))
        val c = Stroke(m, testId(11), StrokeTool.PEN, 0xFF000000.toInt(), 3f, byteArrayOf(1, 2, 4))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun anotacionesConLaMismaGeometriaSonIguales() {
        val m = meta()
        val a = Annotation(m, testId(12), 0, AnnotationKind.HIGHLIGHT, 0xFFFFFF00.toInt(), byteArrayOf(9))
        val b = Annotation(m, testId(12), 0, AnnotationKind.HIGHLIGHT, 0xFFFFFF00.toInt(), byteArrayOf(9))
        assertEquals(a, b)
        assertNotEquals(a, Annotation(m, testId(12), 1, AnnotationKind.HIGHLIGHT, 0xFFFFFF00.toInt(), byteArrayOf(9)))
    }
}
