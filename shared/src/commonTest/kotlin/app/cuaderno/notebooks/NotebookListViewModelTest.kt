package app.cuaderno.notebooks

import app.cuaderno.testing.FakeNotebookRepository
import app.cuaderno.testing.testNotebook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookListViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun muestraLosCuadernosDelRepositorio() {
        val repo = FakeNotebookRepository(listOf(testNotebook(1, "Álgebra"), testNotebook(2, "Física")))
        val vm = NotebookListViewModel(repo)
        assertEquals(listOf("Álgebra", "Física"), vm.state.value.notebooks?.map { it.title })
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun crearAbreElDialogoYGuardaElTituloLimpio() {
        val repo = FakeNotebookRepository()
        val vm = NotebookListViewModel(repo)
        vm.onCreateClick()
        assertEquals(NotebookDialog.Create, vm.state.value.dialog)
        vm.onConfirmTitle("  Química   orgánica ")
        assertNull(vm.state.value.dialog)
        assertEquals(listOf("Química orgánica"), vm.state.value.notebooks?.map { it.title })
    }

    @Test
    fun unTituloVacioNoCreaNadaNiCierraElDialogo() {
        val repo = FakeNotebookRepository()
        val vm = NotebookListViewModel(repo)
        vm.onCreateClick()
        vm.onConfirmTitle("   ")
        assertEquals(NotebookDialog.Create, vm.state.value.dialog)
        assertEquals(emptyList(), vm.state.value.notebooks)
    }

    @Test
    fun renombraElCuadernoElegido() {
        val a = testNotebook(1, "Viejo")
        val repo = FakeNotebookRepository(listOf(a, testNotebook(2, "Otro")))
        val vm = NotebookListViewModel(repo)
        vm.onRenameClick(a)
        assertEquals(NotebookDialog.Rename(a), vm.state.value.dialog)
        vm.onConfirmTitle("Nuevo")
        assertEquals(listOf("Nuevo", "Otro"), vm.state.value.notebooks?.map { it.title })
    }

    @Test
    fun eliminarSoloOcultaTrasConfirmar() {
        val a = testNotebook(1)
        val repo = FakeNotebookRepository(listOf(a))
        val vm = NotebookListViewModel(repo)
        vm.onDeleteClick(a)
        vm.onDismissDialog()
        assertEquals(1, vm.state.value.notebooks?.size)

        vm.onDeleteClick(a)
        vm.onConfirmDelete()
        assertNull(vm.state.value.dialog)
        assertEquals(emptyList(), vm.state.value.notebooks)
        // Borrado suave: el dato sigue guardado, solo marcado como borrado.
        assertTrue(repo.items.value.single().meta.isDeleted)
    }

    @Test
    fun unErrorAlGuardarSeMuestraComoAviso() {
        val repo = FakeNotebookRepository()
        repo.failWith = IllegalStateException("disco lleno")
        val vm = NotebookListViewModel(repo)
        vm.onCreateClick()
        vm.onConfirmTitle("Historia")
        val message = assertNotNull(vm.state.value.message)
        assertTrue("disco lleno" in message)

        repo.failWith = null
        vm.onCreateClick()
        vm.onConfirmTitle("Historia")
        assertNull(vm.state.value.message)
    }
}
