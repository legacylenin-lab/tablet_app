package app.cuaderno.library

import app.cuaderno.testing.FakeDocumentRepository
import app.cuaderno.testing.FakePdfEngine
import app.cuaderno.testing.testDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val engine = FakePdfEngine()
    private val repo = FakeDocumentRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.reader(pages: Int = 5, lastRead: Int = 0) =
        ReaderViewModel(testDocument(1, pages = pages).copy(lastReadPage = lastRead), engine, repo, saveScope = backgroundScope)

    @Test
    fun abreEnLaUltimaPaginaLeidaYLaDibujaAlConocerElAncho() = runTest {
        val vm = reader(lastRead = 3)
        assertEquals("Página 4 de 5", vm.state.value.nav.label)
        assertTrue(engine.rendered.isEmpty())
        vm.onWidth(800)
        assertEquals(listOf(3 to 800), engine.rendered)
        assertNotNull(vm.state.value.image)
        assertEquals(3, vm.state.value.imageIndex)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun lasFlechasPasanPaginaSinSalirseDelDocumento() = runTest {
        val vm = reader(pages = 2)
        vm.onWidth(500)
        vm.onPrevious()
        assertEquals(0, vm.state.value.index)
        vm.onNext()
        vm.onNext()
        assertEquals(1, vm.state.value.index)
        assertEquals(listOf(0 to 500, 1 to 500), engine.rendered)
    }

    @Test
    fun cambiarElAnchoVuelveADibujar() = runTest {
        val vm = reader()
        vm.onWidth(500)
        vm.onWidth(500)
        vm.onWidth(900)
        assertEquals(listOf(0 to 500, 0 to 900), engine.rendered)
    }

    @Test
    fun recuerdaLaPaginaSoloCuandoElUsuarioSeDetiene() = runTest {
        val vm = reader()
        vm.onWidth(500)
        vm.onNext()
        vm.onNext()
        advanceTimeBy(500)
        vm.onNext()
        advanceTimeBy(500)
        assertTrue(repo.savedPages.isEmpty())
        // advanceUntilIdle no espera a las tareas de fondo: se adelanta el reloj a mano.
        advanceTimeBy(600)
        runCurrent()
        assertEquals(listOf(3), repo.savedPages)
    }

    @Test
    fun siNoSePuedeDibujarLoAvisa() = runTest {
        engine.failWith = IllegalStateException("archivo borrado")
        val vm = reader()
        vm.onWidth(500)
        assertTrue("archivo borrado" in assertNotNull(vm.state.value.message))
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun unaPaginaGuardadaFueraDeRangoSeAjusta() = runTest {
        val vm = reader(pages = 3, lastRead = 9)
        assertEquals("Página 3 de 3", vm.state.value.nav.label)
    }
}
