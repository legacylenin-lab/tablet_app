package app.cuaderno.ink

import app.cuaderno.model.StrokeTool
import app.cuaderno.testing.FakeInkRepository
import app.cuaderno.testing.newLine
import app.cuaderno.testing.savedLine
import app.cuaderno.testing.testId
import kotlinx.coroutines.CoroutineScope
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
class InkPageViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val page = testId(50)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repo: FakeInkRepository) = InkPageViewModel(page, repo, CoroutineScope(dispatcher))

    @Test
    fun cargaSoloLosTrazosDeSuPagina() {
        val repo = FakeInkRepository(listOf(savedLine(1, 100, page), savedLine(2, 200, testId(51))))
        val vm = viewModel(repo)
        assertTrue(vm.state.value.loaded)
        assertEquals(1, vm.state.value.visibleCount)
    }

    @Test
    fun unTrazoNuevoSeMuestraYSeGuarda() {
        val repo = FakeInkRepository()
        val vm = viewModel(repo)
        val keys = vm.onStrokesDrawn(listOf(newLine(0, 500, 100, StrokeTool.HIGHLIGHTER)))
        assertEquals(1, keys.size)
        assertEquals(1, vm.state.value.visibleCount)
        assertTrue(vm.state.value.canUndo)
        assertEquals(StrokeTool.HIGHLIGHTER, repo.items.value.single().tool)
    }

    @Test
    fun deshacerYRehacerUnTrazoLoOcultaYLoVuelveAMostrar() {
        val repo = FakeInkRepository()
        val vm = viewModel(repo)
        vm.onStrokesDrawn(listOf(newLine(0, 500, 100)))
        vm.onUndo()
        assertEquals(0, vm.state.value.visibleCount)
        assertTrue(repo.visibleIds.isEmpty())
        assertTrue(vm.state.value.canRedo)
        vm.onRedo()
        assertEquals(1, vm.state.value.visibleCount)
        assertEquals(1, repo.visibleIds.size)
        assertFalse(vm.state.value.canRedo)
    }

    @Test
    fun elBorradorSoloBorraLoQueToca() {
        val repo = FakeInkRepository(listOf(savedLine(1, 100, page), savedLine(2, 900, page)))
        val vm = viewModel(repo)
        vm.onEraseAt(x = 500f, y = 105f, radius = 10f)
        vm.onEraseEnd()
        assertEquals(1, vm.state.value.visibleCount)
        assertEquals(listOf(testId(2)), repo.visibleIds)
    }

    @Test
    fun unGestoDeBorradorSeDeshaceDeUnaVez() {
        val repo = FakeInkRepository(listOf(savedLine(1, 100, page), savedLine(2, 200, page)))
        val vm = viewModel(repo)
        vm.onEraseAt(500f, 100f, 5f)
        vm.onEraseAt(500f, 200f, 5f)
        vm.onEraseEnd()
        assertEquals(0, vm.state.value.visibleCount)
        vm.onUndo()
        assertEquals(2, vm.state.value.visibleCount)
        assertEquals(2, repo.visibleIds.size)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun limpiarBorraTodoYSePuedeDeshacer() {
        val repo = FakeInkRepository(listOf(savedLine(1, 100, page)))
        val vm = viewModel(repo)
        vm.onStrokesDrawn(listOf(newLine(0, 500, 300)))
        vm.onClear()
        assertEquals(0, vm.state.value.visibleCount)
        assertTrue(repo.visibleIds.isEmpty())
        vm.onUndo()
        assertEquals(2, vm.state.value.visibleCount)
        assertEquals(2, repo.visibleIds.size)
    }

    @Test
    fun dibujarDespuesDeDeshacerVaciaRehacer() {
        val vm = viewModel(FakeInkRepository())
        vm.onStrokesDrawn(listOf(newLine(0, 500, 100)))
        vm.onUndo()
        vm.onStrokesDrawn(listOf(newLine(0, 500, 200)))
        assertFalse(vm.state.value.canRedo)
    }

    @Test
    fun deshacerSigueElOrdenDeTrazosYOtrasAcciones() {
        val vm = viewModel(FakeInkRepository())
        val log = mutableListOf<String>()
        val textAction = object : app.cuaderno.UndoableAction {
            override fun undo() { log += "deshacer texto" }
            override fun redo() { log += "rehacer texto" }
        }
        vm.onStrokesDrawn(listOf(newLine(0, 500, 100)))
        vm.onOtherAction(textAction)
        vm.onStrokesDrawn(listOf(newLine(0, 500, 200)))

        vm.onUndo()
        assertEquals(1, vm.state.value.visibleCount)
        assertEquals(emptyList(), log)
        vm.onUndo()
        assertEquals(listOf("deshacer texto"), log)
        assertEquals(1, vm.state.value.visibleCount)
        vm.onUndo()
        assertEquals(0, vm.state.value.visibleCount)
        assertFalse(vm.state.value.canUndo)

        vm.onRedo()
        vm.onRedo()
        assertEquals(listOf("deshacer texto", "rehacer texto"), log)
        assertEquals(1, vm.state.value.visibleCount)
    }

    @Test
    fun elBotonDelLapizAlternaSoloConPulsacionesNuevas() {
        val vm = viewModel(FakeInkRepository())
        val start = PenButton.clicks
        vm.onPenButtonClicks(start)
        assertFalse(vm.state.value.eraserMode)
        vm.onPenButtonClicks(start + 1)
        assertTrue(vm.state.value.eraserMode)
        vm.onPenButtonClicks(start + 2)
        assertFalse(vm.state.value.eraserMode)
    }

    @Test
    fun herramientasYZoom() {
        val vm = viewModel(FakeInkRepository())
        vm.onToolSettings(vm.state.value.tools.withTool(StrokeTool.HIGHLIGHTER))
        assertEquals(StrokeTool.HIGHLIGHTER, vm.state.value.tools.tool)
        vm.onViewport(PageViewport(2f, -10f, -20f))
        assertEquals(2f, vm.state.value.viewport.zoom)
        vm.onFit()
        assertEquals(1f, vm.state.value.viewport.zoom)
    }

    @Test
    fun unErrorAlGuardarSeMuestraComoAviso() {
        val repo = FakeInkRepository()
        repo.failWith = IllegalStateException("disco lleno")
        val vm = viewModel(repo)
        vm.onStrokesDrawn(listOf(newLine(0, 500, 100)))
        assertTrue("disco lleno" in assertNotNull(vm.state.value.message))
        // El trazo sigue en pantalla aunque no se haya guardado.
        assertEquals(1, vm.state.value.visibleCount)
    }
}
