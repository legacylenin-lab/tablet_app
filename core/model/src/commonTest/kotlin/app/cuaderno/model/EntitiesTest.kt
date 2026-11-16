package app.cuaderno.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntitiesTest {

    private val device = "dev-1"
    private val author = "autor-1"

    private fun meta(wall: Long = 100) =
        Meta.created(newId(), Hlc(wall, 0, device), author)

    @Test
    fun losIdentificadoresSonUnicosYConFormaDeUuid() {
        val ids = List(1000) { newId() }
        assertEquals(1000, ids.toSet().size)
        assertTrue(ids.all { it.length == 36 && it[8] == '-' && it[13] == '-' })
    }

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
        val u = meta().updated(Hlc(200, 0, "dev-2"), deviceId = "dev-2")
        assertEquals("dev-2", u.deviceId)
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
        val p = Page(meta(), notebookId = "n", sortKey = "a")
        assertEquals(210, p.widthMm)
        assertEquals(297, p.heightMm)
        assertEquals(PageTemplate.BLANK, p.template)
    }

    @Test
    fun trazosConLosMismosPuntosSonIguales() {
        val m = meta()
        val a = Stroke(m, "p", StrokeTool.PEN, 0xFF000000.toInt(), 3, byteArrayOf(1, 2, 3))
        val b = Stroke(m, "p", StrokeTool.PEN, 0xFF000000.toInt(), 3, byteArrayOf(1, 2, 3))
        val c = Stroke(m, "p", StrokeTool.PEN, 0xFF000000.toInt(), 3, byteArrayOf(1, 2, 4))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun anotacionesConLaMismaGeometriaSonIguales() {
        val m = meta()
        val a = Annotation(m, "d", 0, AnnotationKind.HIGHLIGHT, 0xFFFFFF00.toInt(), byteArrayOf(9))
        val b = Annotation(m, "d", 0, AnnotationKind.HIGHLIGHT, 0xFFFFFF00.toInt(), byteArrayOf(9))
        assertEquals(a, b)
        assertNotEquals(a, Annotation(m, "d", 1, AnnotationKind.HIGHLIGHT, 0xFFFFFF00.toInt(), byteArrayOf(9)))
    }
}
