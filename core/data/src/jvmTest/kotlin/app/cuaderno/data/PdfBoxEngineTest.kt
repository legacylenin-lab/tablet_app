package app.cuaderno.data

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Motor de PDF de Windows con PDFBox (prototipo P-1). Los PDF de prueba se crean aquí mismo. */
class PdfBoxEngineTest {
    private val root: File = Files.createTempDirectory("pdfbox-test").toFile()
    private val documentsDir = File(root, "documents").also { it.mkdirs() }
    private var engine: PdfBoxEngine? = null

    @AfterTest
    fun tearDown() {
        engine?.close()
        root.deleteRecursively()
    }

    private fun TestScope.engine(): PdfBoxEngine {
        val files = FileDocumentStore(documentsDir, StandardTestDispatcher(testScheduler)) { error("no se usa") }
        return PdfBoxEngine(files, StandardTestDispatcher(testScheduler)).also { engine = it }
    }

    /** Crea un PDF A4 de [pages] páginas, con un cuadrado negro arriba a la izquierda, y lo guarda por su hash. */
    private fun storePdf(pages: Int, rotation: Int = 0): String {
        val bytes = PDDocument().use { doc ->
            repeat(pages) { n ->
                val page = PDPage(PDRectangle.A4).also { it.rotation = rotation }
                doc.addPage(page)
                PDPageContentStream(doc, page).use { content ->
                    content.addRect(50f, PDRectangle.A4.height - 150f, 100f, 100f) // 100 × 100 puntos
                    content.fill()
                    content.beginText()
                    content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 14f)
                    content.newLineAtOffset(50f, 400f)
                    content.showText("Pagina ${n + 1}")
                    content.endText()
                }
            }
            java.io.ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        File(documentsDir, "$hash.pdf").writeBytes(bytes)
        return hash
    }

    private fun isDark(argb: Int) = (argb and 0xFF) < 64 && ((argb shr 8) and 0xFF) < 64 && ((argb shr 16) and 0xFF) < 64

    @Test
    fun cuentaLasPaginas() = runTest {
        assertEquals(500, engine().pageCount(storePdf(500)))
    }

    @Test
    fun dibujaLaPaginaConLaProporcionA4YFondoBlanco() = runTest {
        val page = engine().renderPage(storePdf(1), 0, widthPx = 595)
        assertEquals(595, page.width)
        assertEquals(841, page.height) // 595 × 842 puntos
        assertEquals(-1, page.pixels[page.width * 600 + 500]) // blanco opaco
        // El cuadrado negro: de x = 50 a 150, de y = 50 a 150 (desde arriba).
        assertTrue(isDark(page.pixels[page.width * 100 + 100]))
        assertEquals(-1, page.pixels[page.width * 100 + 300])
    }

    @Test
    fun unRectanguloConZoomCoincideConLaPaginaEnteraAEseTamano() = runTest {
        val hash = storePdf(1)
        val e = engine()
        val full = e.renderPage(hash, 0, widthPx = 1190) // zoom 2
        val region = e.renderRegion(hash, 0, pageWidthPx = 1190, left = 80, top = 80, width = 300, height = 200)
        assertEquals(300, region.width)
        assertEquals(200, region.height)
        val expected = IntArray(300 * 200) { i -> full.pixels[(80 + i / 300) * full.width + 80 + i % 300] }
        assertContentEquals(expected, region.pixels)
        assertTrue(isDark(region.pixels[100 * 300 + 100])) // (180, 180) cae dentro del cuadrado (100..300)
        assertEquals(-1, region.pixels[199 * 300 + 299]) // (379, 279) queda fuera: blanco
    }

    @Test
    fun unaPaginaGiradaSeDibujaApaisada() = runTest {
        val page = engine().renderPage(storePdf(1, rotation = 90), 0, widthPx = 842)
        assertEquals(842, page.width)
        assertEquals(595, page.height)
    }

    @Test
    fun pasaDeUnDocumentoAOtro() = runTest {
        val e = engine()
        val a = storePdf(3)
        val b = storePdf(7)
        assertEquals(3, e.pageCount(a))
        assertEquals(7, e.pageCount(b))
        assertEquals(3, e.pageCount(a))
    }

    @Test
    fun unaPaginaQueNoExisteFalla() = runTest {
        val hash = storePdf(2)
        assertFailsWith<IllegalArgumentException> { engine().renderPage(hash, 2, 100) }
    }

    @Test
    fun unArchivoDanadoFalla() = runTest {
        val hash = "a".repeat(64)
        File(documentsDir, "$hash.pdf").writeText("%PDF-1.7 esto no es un PDF de verdad")
        assertFailsWith<Exception> { engine().pageCount(hash) }
    }
}
