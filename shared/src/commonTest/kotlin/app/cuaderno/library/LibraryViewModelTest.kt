package app.cuaderno.library

import app.cuaderno.domain.ImportResult
import app.cuaderno.testing.FakeDocumentRepository
import app.cuaderno.testing.TestFile
import app.cuaderno.testing.testDocument
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun muestraLosDocumentos() {
        val vm = LibraryViewModel(FakeDocumentRepository(listOf(testDocument(1), testDocument(2))))
        assertEquals(2, vm.state.value.documents?.size)
    }

    @Test
    fun importarAgregaElDocumentoYAvisa() {
        val repo = FakeDocumentRepository()
        val vm = LibraryViewModel(repo)
        vm.onFilePicked(TestFile("Física.pdf"))
        assertEquals(listOf("Física"), vm.state.value.documents?.map { it.title })
        assertEquals("Se agregó «Física» (10 páginas).", vm.state.value.message)
        assertFalse(vm.state.value.importing)
        vm.onDismissMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun mientrasImportaNoAceptaOtroArchivo() {
        val repo = FakeDocumentRepository()
        repo.gate = CompletableDeferred()
        val vm = LibraryViewModel(repo)
        vm.onFilePicked(TestFile("a.pdf"))
        assertTrue(vm.state.value.importing)
        vm.onFilePicked(TestFile("b.pdf"))
        assertEquals(listOf("a.pdf"), repo.imported)
        repo.gate!!.complete(Unit)
        assertFalse(vm.state.value.importing)
    }

    @Test
    fun unErrorSeMuestraComoAviso() {
        val repo = FakeDocumentRepository()
        repo.failWith = IllegalStateException("disco lleno")
        val vm = LibraryViewModel(repo)
        vm.onFilePicked(TestFile("a.pdf"))
        assertTrue("disco lleno" in assertNotNull(vm.state.value.message))
        assertFalse(vm.state.value.importing)
    }

    @Test
    fun avisosDeCadaResultado() {
        assertEquals(
            "«Libro» ya estaba en la biblioteca: no se duplicó.",
            LibraryViewModel.messageFor(ImportResult.AlreadyExists(testDocument(1, "Libro"))),
        )
        assertTrue("Guardar como → PDF" in LibraryViewModel.messageFor(ImportResult.NotPdf(isWord = true)))
        assertEquals("Ese archivo no es un PDF.", LibraryViewModel.messageFor(ImportResult.NotPdf(isWord = false)))
        assertTrue("dañado" in LibraryViewModel.messageFor(ImportResult.Corrupt))
        assertEquals("Se agregó «Uno» (1 página).", LibraryViewModel.messageFor(ImportResult.Imported(testDocument(1, "Uno", pages = 1))))
    }

    @Test
    fun eliminarPideConfirmacionYEsBorradoSuave() {
        val repo = FakeDocumentRepository(listOf(testDocument(1, "Álgebra"), testDocument(2, "Física")))
        val vm = LibraryViewModel(repo)
        val algebra = vm.state.value.documents!!.first()
        vm.onDeleteClick(algebra)
        assertEquals(algebra, vm.state.value.confirmDelete)
        vm.onDismissDelete()
        assertNull(vm.state.value.confirmDelete)
        assertEquals(2, vm.state.value.documents?.size)

        vm.onDeleteClick(algebra)
        vm.onConfirmDelete()
        assertNull(vm.state.value.confirmDelete)
        assertEquals(listOf("Física"), vm.state.value.documents?.map { it.title })
        // Borrado suave: sigue guardado, solo marcado como borrado.
        assertTrue(repo.items.value.first().meta.isDeleted)
    }

    @Test
    fun unErrorAlEliminarSeMuestraComoAviso() {
        val repo = FakeDocumentRepository(listOf(testDocument(1, "Álgebra")))
        val vm = LibraryViewModel(repo)
        repo.failWith = IllegalStateException("disco lleno")
        vm.onDeleteClick(vm.state.value.documents!!.first())
        vm.onConfirmDelete()
        assertTrue("disco lleno" in assertNotNull(vm.state.value.message))
        assertEquals(1, vm.state.value.documents?.size)
    }

    @Test
    fun tamanosLegibles() {
        assertEquals("1 KB", LibraryViewModel.sizeLabel(10))
        assertEquals("850 KB", LibraryViewModel.sizeLabel(850 * 1024))
        assertEquals("1,0 MB", LibraryViewModel.sizeLabel(1024 * 1024))
        assertEquals("12,4 MB", LibraryViewModel.sizeLabel(12 * 1024 * 1024 + 410 * 1024))
    }
}
