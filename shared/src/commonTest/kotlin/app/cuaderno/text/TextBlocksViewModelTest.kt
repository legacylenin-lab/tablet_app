package app.cuaderno.text

import app.cuaderno.testing.FakeTextBlockRepository
import app.cuaderno.testing.testId
import app.cuaderno.testing.testTextBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TextBlocksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val page = testId(60)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repo: FakeTextBlockRepository) = TextBlocksViewModel(page, repo, CoroutineScope(dispatcher))

    @Test
    fun cargaSoloLosTextosDeSuPagina() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p>Hola</p>"), testTextBlock(2, testId(61), "<p>Otra</p>")))
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertTrue(vm.state.value.loaded)
        assertEquals(listOf("<p>Hola</p>"), vm.state.value.blocks.map { it.html })
        assertNull(vm.state.value.editingId)
    }

    @Test
    fun crearDejaElCuadroListoParaEscribir() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository()
        val vm = viewModel(repo)
        vm.onAddText(x = 150, y = 200, width = 1200)
        advanceUntilIdle()
        val editing = assertNotNull(vm.state.value.editing)
        assertEquals(150, editing.x)
        assertEquals(1200, editing.width)
        assertEquals(1, repo.visible.size)
    }

    @Test
    fun unCuadroNoSeSaleDeLaHoja() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository()
        val vm = viewModel(repo)
        vm.onAddText(x = 2000, y = -50, width = 1500)
        advanceUntilIdle()
        val b = repo.visible.single()
        assertTrue(b.x + b.width <= 2100)
        assertEquals(0, b.y)
        assertTrue(b.width >= TextBlocksViewModel.MIN_WIDTH)
    }

    @Test
    fun unTextoNuevoNoSeEncimaConOtro() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p>Uno</p>", x = 100, y = 100, width = 1200)))
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.onMeasured(testId(1), 250f)
        vm.onAddText(x = 100, y = 100, width = 1200)
        advanceUntilIdle()
        val nuevo = assertNotNull(vm.state.value.editing)
        assertEquals(100 + 250 + TextBlocksViewModel.GAP, nuevo.y)

        // Un tercero va debajo de los dos (el segundo aún sin medir cuenta como una línea).
        // Al segundo se le escribe algo: si quedara vacío, se borraría solo al crear el tercero.
        vm.onContentChanged(nuevo.id, "<p>Dos</p>")
        vm.onAddText(x = 100, y = 100, width = 1200)
        advanceUntilIdle()
        val tercero = assertNotNull(vm.state.value.editing)
        assertEquals(nuevo.y + TextBlocksViewModel.LINE_HEIGHT.toInt() + TextBlocksViewModel.GAP, tercero.y)
    }

    @Test
    fun unTextoAlLadoDeOtroNoSeMueve() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p>Uno</p>", x = 100, y = 100, width = 800)))
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.onMeasured(testId(1), 250f)
        vm.onAddText(x = 1000, y = 100, width = 800)
        advanceUntilIdle()
        assertEquals(100, assertNotNull(vm.state.value.editing).y)
    }

    @Test
    fun moverSeVeEnseguidaYSeGuardaAlSoltar() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p>Uno</p>", x = 100, y = 100, width = 1000)))
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.onGeometryChanged(testId(1), x = 300, y = 500, width = 1000)
        runCurrent()
        assertEquals(300, vm.state.value.blocks.single().x)
        assertEquals(100, repo.visible.single().x)
        vm.onGeometryDone(testId(1))
        runCurrent()
        val saved = repo.visible.single()
        assertEquals(300, saved.x)
        assertEquals(500, saved.y)
        // Mover no cuenta como editar el texto.
        assertEquals(0, repo.contentSaves)
    }

    @Test
    fun elCuadroNoSeSaleDeLaHojaNiQuedaDemasiadoAngosto() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p>Uno</p>")))
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.onGeometryChanged(testId(1), x = -50, y = 5000, width = 10)
        val b = vm.state.value.blocks.single()
        assertEquals(0, b.x)
        assertEquals(TextBlocksViewModel.MIN_WIDTH, b.width)
        assertTrue(b.y < 2970)

        vm.onGeometryChanged(testId(1), x = 1800, y = 100, width = 800)
        val c = vm.state.value.blocks.single()
        assertEquals(2100, c.x + c.width)
    }

    @Test
    fun loEscritoSeGuardaUnaVezAlDejarDeTeclear() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository()
        val vm = viewModel(repo)
        vm.onAddText(100, 100, 1000)
        advanceUntilIdle()
        val id = vm.state.value.editingId!!
        vm.onContentChanged(id, "<p>H</p>")
        advanceTimeBy(300)
        vm.onContentChanged(id, "<p>Ho</p>")
        advanceTimeBy(300)
        vm.onContentChanged(id, "<p>Hola</p>")
        runCurrent()
        assertEquals(0, repo.contentSaves)
        advanceTimeBy(TextBlocksViewModel.SAVE_DELAY_MS + 1)
        runCurrent()
        assertEquals(1, repo.contentSaves)
        assertEquals("<p>Hola</p>", repo.visible.single().content)
    }

    @Test
    fun terminarGuardaEnseguida() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p>Viejo</p>")))
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.onEdit(testId(1))
        vm.onContentChanged(testId(1), "<p><b>Nuevo</b></p>")
        vm.onDoneEditing()
        runCurrent()
        assertNull(vm.state.value.editingId)
        assertEquals("<p><b>Nuevo</b></p>", repo.visible.single().content)
    }

    @Test
    fun elHtmlSeGuardaEnElFormatoAcordado() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p>a</p>")))
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.onEdit(testId(1))
        vm.onContentChanged(testId(1), """<p><span style="font-size: 2.0em;">Tema</span></p><script>x</script>""")
        vm.onDoneEditing()
        runCurrent()
        assertEquals("<h1>Tema</h1>", repo.visible.single().content)
    }

    @Test
    fun unCuadroVacioSeEliminaAlTerminar() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository()
        val vm = viewModel(repo)
        vm.onAddText(100, 100, 1000)
        advanceUntilIdle()
        vm.onContentChanged(vm.state.value.editingId!!, "<p>&nbsp; </p>")
        vm.onDoneEditing()
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.blocks)
        assertEquals(emptyList(), repo.visible)
        // Borrado suave: sigue guardado, marcado como borrado.
        assertTrue(repo.items.value.single().meta.isDeleted)
    }

    @Test
    fun editarOtroCuadroTerminaElAnterior() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p>Uno</p>"), testTextBlock(2, page, "<p>Dos</p>")))
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.onEdit(testId(1))
        vm.onContentChanged(testId(1), "<p>Uno más</p>")
        vm.onEdit(testId(2))
        runCurrent()
        assertEquals(testId(2), vm.state.value.editingId)
        assertEquals("<p>Uno más</p>", repo.visible.first { it.meta.id == testId(1) }.content)
    }

    @Test
    fun eliminarQuitaElCuadro() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p>Uno</p>")))
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.onEdit(testId(1))
        vm.onDelete(testId(1))
        advanceUntilIdle()
        assertNull(vm.state.value.editingId)
        assertEquals(emptyList(), repo.visible)
    }

    @Test
    fun deshacerDevuelveUnTextoEliminadoYRehacerLoVuelveABorrar() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p><b>Importante</b></p>", x = 300, y = 400, width = 900)))
        val vm = viewModel(repo)
        advanceUntilIdle()
        val undo = assertNotNull(vm.onDelete(testId(1)))
        advanceUntilIdle()
        assertEquals(emptyList(), repo.visible)

        undo.undo()
        advanceUntilIdle()
        val back = repo.visible.single()
        assertEquals("<p><b>Importante</b></p>", back.content)
        assertEquals(300, back.x)
        assertEquals(400, back.y)
        assertEquals(900, back.width)
        assertEquals(1, vm.state.value.blocks.size)

        undo.redo()
        advanceUntilIdle()
        assertEquals(emptyList(), repo.visible)
        assertEquals(emptyList(), vm.state.value.blocks)

        // Se puede deshacer otra vez después de rehacer.
        undo.undo()
        advanceUntilIdle()
        assertEquals(1, repo.visible.size)
    }

    @Test
    fun borrarTextosEliminaTodosYSeDeshaceDeUnaVez() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository(listOf(testTextBlock(1, page, "<p>Uno</p>"), testTextBlock(2, page, "<p>Dos</p>", y = 600)))
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.onEdit(testId(1))
        val undo = assertNotNull(vm.onDeleteAll())
        advanceUntilIdle()
        assertNull(vm.state.value.editingId)
        assertEquals(emptyList(), repo.visible)

        undo.undo()
        advanceUntilIdle()
        assertEquals(listOf("<p>Uno</p>", "<p>Dos</p>"), repo.visible.map { it.content })
    }

    @Test
    fun sinTextosNoHayNadaQueBorrar() = runTest(dispatcher) {
        val vm = viewModel(FakeTextBlockRepository())
        advanceUntilIdle()
        assertNull(vm.onDeleteAll())
        assertNull(vm.onDelete(testId(1)))
    }

    @Test
    fun unErrorAlCrearSeMuestraComoAviso() = runTest(dispatcher) {
        val repo = FakeTextBlockRepository()
        repo.failWith = IllegalStateException("disco lleno")
        val vm = viewModel(repo)
        vm.onAddText(100, 100, 1000)
        advanceUntilIdle()
        assertTrue("disco lleno" in assertNotNull(vm.state.value.message))
        assertNull(vm.state.value.editingId)
    }
}
