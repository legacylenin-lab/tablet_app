package app.cuaderno.notebooks

import app.cuaderno.testing.FakeNotebookRepository
import app.cuaderno.testing.FakePageRepository
import app.cuaderno.testing.testNotebook
import app.cuaderno.testing.testPage
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookPagesViewModelTest {
    private val notebook = testNotebook(1)
    private val id = notebook.meta.id
    private val p1 = testPage(11, id)
    private val p2 = testPage(12, id)
    private val p3 = testPage(13, id)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(pages: FakePageRepository) =
        NotebookPagesViewModel(id, FakeNotebookRepository(listOf(notebook)), pages)

    @Test
    fun unCuadernoSinPaginasRecibeUnaEnBlanco() {
        val pages = FakePageRepository()
        val vm = viewModel(pages)
        assertEquals(1, vm.state.value.pages?.size)
        assertNotNull(vm.state.value.currentPage)
        assertEquals("Página 1 de 1", vm.state.value.nav.label)
    }

    @Test
    fun lasFlechasMuevenSinSalirseDeLaLista() {
        val vm = viewModel(FakePageRepository(listOf(p1, p2)))
        assertEquals(p1, vm.state.value.currentPage)
        vm.onPrevious()
        assertEquals(p1, vm.state.value.currentPage)
        vm.onNext()
        assertEquals(p2, vm.state.value.currentPage)
        vm.onNext()
        assertEquals(p2, vm.state.value.currentPage)
        assertEquals("Página 2 de 2", vm.state.value.nav.label)
    }

    @Test
    fun paginaNuevaSeAgregaDespuesDeLaActualYSePasaAElla() {
        val pages = FakePageRepository(listOf(p1, p2))
        val vm = viewModel(pages)
        vm.onAdd()
        val list = vm.state.value.pages!!
        assertEquals(3, list.size)
        assertEquals(p1, list[0])
        assertEquals(p2, list[2])
        assertEquals(list[1], vm.state.value.currentPage)
        assertEquals("Página 2 de 3", vm.state.value.nav.label)
    }

    @Test
    fun duplicarPasaALaCopia() {
        val vm = viewModel(FakePageRepository(listOf(p1, p2)))
        vm.onNext()
        vm.onDuplicate()
        assertEquals(3, vm.state.value.pages?.size)
        assertEquals(2, vm.state.value.index)
        assertTrue(vm.state.value.currentPage != p2)
    }

    @Test
    fun eliminarPideConfirmacionYQuedaEnLaAnteriorSiEraLaUltima() {
        val pages = FakePageRepository(listOf(p1, p2, p3))
        val vm = viewModel(pages)
        vm.onNext()
        vm.onNext()
        vm.onDeleteClick()
        assertTrue(vm.state.value.confirmDelete)
        vm.onDismissDelete()
        assertEquals(3, vm.state.value.pages?.size)

        vm.onDeleteClick()
        vm.onConfirmDelete()
        assertFalse(vm.state.value.confirmDelete)
        assertEquals(listOf(p1, p2), vm.state.value.pages)
        assertEquals(p2, vm.state.value.currentPage)
        // Borrado suave: la página sigue guardada, solo marcada como borrada.
        assertTrue(pages.items.value.last().meta.isDeleted)
    }

    @Test
    fun eliminarUnaPaginaDelMedioMuestraLaSiguiente() {
        val vm = viewModel(FakePageRepository(listOf(p1, p2, p3)))
        vm.onNext()
        vm.onDeleteClick()
        vm.onConfirmDelete()
        assertEquals(p3, vm.state.value.currentPage)
        assertEquals("Página 2 de 2", vm.state.value.nav.label)
    }

    @Test
    fun conUnaSolaPaginaNoSePuedeEliminar() {
        val vm = viewModel(FakePageRepository(listOf(p1)))
        vm.onDeleteClick()
        assertFalse(vm.state.value.confirmDelete)
    }

    @Test
    fun unErrorAlGuardarSeMuestraComoAviso() {
        val pages = FakePageRepository(listOf(p1))
        pages.failWith = IllegalStateException("disco lleno")
        val vm = viewModel(pages)
        vm.onAdd()
        assertTrue("disco lleno" in assertNotNull(vm.state.value.message))
        assertEquals(p1, vm.state.value.currentPage)
    }
}
