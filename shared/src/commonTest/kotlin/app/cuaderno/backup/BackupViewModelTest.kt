package app.cuaderno.backup

import app.cuaderno.domain.BackupResult
import app.cuaderno.domain.BackupService
import app.cuaderno.domain.PlatformFile
import app.cuaderno.testing.TestFile
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Respaldo de prueba: devuelve [result]; con [gate], espera hasta que se complete. */
private class FakeBackupService(var result: BackupResult = BackupResult.Created(1L, 2)) : BackupService {
    val exported = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun export(destination: PlatformFile): BackupResult {
        exported += destination.name
        gate?.await()
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun crearRespaldoAvisaQueTerminoYConCuantosPdf() {
        val vm = BackupViewModel(FakeBackupService())
        vm.onDestinationPicked(TestFile("r.cuaderno-backup"))
        assertEquals("Listo: se guardó «r.cuaderno-backup» con todos tus cuadernos y 2 PDF de la biblioteca.", vm.state.value.message)
        assertFalse(vm.state.value.failed)
        vm.onDismissMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun mientrasTrabajaNoEmpiezaOtroRespaldo() {
        val backup = FakeBackupService()
        backup.gate = CompletableDeferred()
        val vm = BackupViewModel(backup)
        vm.onDestinationPicked(TestFile("a"))
        assertTrue(vm.state.value.working)
        vm.onDestinationPicked(TestFile("b"))
        assertEquals(listOf("a"), backup.exported)
        backup.gate!!.complete(Unit)
        assertFalse(vm.state.value.working)
    }

    @Test
    fun unFalloSeMuestraComoError() {
        val vm = BackupViewModel(FakeBackupService(BackupResult.Failed("No se pudo crear el respaldo: sin espacio")))
        vm.onDestinationPicked(TestFile("a"))
        assertTrue(vm.state.value.failed)
        assertEquals("No se pudo crear el respaldo: sin espacio", vm.state.value.message)
    }

    @Test
    fun mensajesSegunLaBiblioteca() {
        assertEquals(
            "Listo: se guardó «x» con todos tus cuadernos (la biblioteca está vacía).",
            BackupViewModel.createdMessage("x", 0),
        )
        assertEquals("Listo: se guardó «x» con todos tus cuadernos y 1 PDF de la biblioteca.", BackupViewModel.createdMessage("x", 1))
    }
}
