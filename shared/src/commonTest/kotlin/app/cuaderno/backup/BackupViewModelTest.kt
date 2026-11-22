package app.cuaderno.backup

import app.cuaderno.domain.BackupResult
import app.cuaderno.domain.BackupService
import app.cuaderno.domain.BackupSummary
import app.cuaderno.domain.InspectResult
import app.cuaderno.domain.PlatformFile
import app.cuaderno.domain.RestoreResult
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

private val SUMMARY = BackupSummary(createdAtMillis = 1_000L, appVersion = "1.0", documentCount = 3)

/** Respaldo de prueba: anota lo que se pidió y devuelve los resultados configurados. */
private class FakeBackupService(
    var exportResult: BackupResult = BackupResult.Created(1L, 2),
    var inspectResult: InspectResult = InspectResult.Valid(SUMMARY),
    var restoreResult: RestoreResult = RestoreResult.ReadyToRestart,
    var previous: BackupSummary? = null,
) : BackupService {
    val calls = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun export(destination: PlatformFile): BackupResult {
        calls += "export ${destination.name}"
        gate?.await()
        return exportResult
    }

    override suspend fun inspect(source: PlatformFile): InspectResult = inspectResult.also { calls += "inspect ${source.name}" }

    override suspend fun prepareRestore(source: PlatformFile): RestoreResult = restoreResult.also { calls += "restore ${source.name}" }

    override suspend fun previousData(): BackupSummary? = previous

    override suspend fun prepareUndoRestore(): RestoreResult = restoreResult.also { calls += "undo" }
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
    fun mientrasTrabajaNoEmpiezaOtraCosa() {
        val backup = FakeBackupService()
        backup.gate = CompletableDeferred()
        val vm = BackupViewModel(backup)
        vm.onDestinationPicked(TestFile("a"))
        assertEquals("Creando el respaldo…", vm.state.value.working)
        vm.onDestinationPicked(TestFile("b"))
        vm.onSourcePicked(TestFile("c"))
        assertEquals(listOf("export a"), backup.calls)
        backup.gate!!.complete(Unit)
        assertNull(vm.state.value.working)
    }

    @Test
    fun unFalloAlCrearSeMuestraComoError() {
        val vm = BackupViewModel(FakeBackupService(exportResult = BackupResult.Failed("No se pudo crear el respaldo: sin espacio")))
        vm.onDestinationPicked(TestFile("a"))
        assertTrue(vm.state.value.failed)
        assertEquals("No se pudo crear el respaldo: sin espacio", vm.state.value.message)
    }

    @Test
    fun restaurarRevisaPideConfirmacionYQuedaListoParaReiniciar() {
        val backup = FakeBackupService()
        val vm = BackupViewModel(backup)
        vm.onSourcePicked(TestFile("viejo.cuaderno-backup"))
        assertEquals(RestoreStep.Confirm(SUMMARY, "viejo.cuaderno-backup", undo = false), vm.state.value.restore)
        assertEquals(listOf("inspect viejo.cuaderno-backup"), backup.calls) // todavía no se cambió nada

        vm.onConfirmRestore()
        assertEquals(RestoreStep.ReadyToRestart, vm.state.value.restore)
        assertEquals(listOf("inspect viejo.cuaderno-backup", "restore viejo.cuaderno-backup"), backup.calls)
    }

    @Test
    fun cancelarNoRestaura() {
        val backup = FakeBackupService()
        val vm = BackupViewModel(backup)
        vm.onSourcePicked(TestFile("x"))
        vm.onCancelRestore()
        vm.onConfirmRestore()
        assertNull(vm.state.value.restore)
        assertEquals(listOf("inspect x"), backup.calls)
    }

    @Test
    fun unArchivoQueNoEsRespaldoSeRechazaSinPedirConfirmacion() {
        val vm = BackupViewModel(FakeBackupService(inspectResult = InspectResult.Rejected("El archivo no es un respaldo de la app.")))
        vm.onSourcePicked(TestFile("foto.jpg"))
        assertNull(vm.state.value.restore)
        assertTrue(vm.state.value.failed)
        assertEquals("El archivo no es un respaldo de la app.", vm.state.value.message)
    }

    @Test
    fun siLaPreparacionFallaSeAvisaYNoSePideReiniciar() {
        val vm = BackupViewModel(FakeBackupService(restoreResult = RestoreResult.Failed("El respaldo está dañado o incompleto. No se cambió nada.")))
        vm.onSourcePicked(TestFile("roto"))
        vm.onConfirmRestore()
        assertNull(vm.state.value.restore)
        assertEquals("El respaldo está dañado o incompleto. No se cambió nada.", vm.state.value.message)
    }

    @Test
    fun volverAComoEstabaUsaLaCopiaPrevia() {
        val backup = FakeBackupService(previous = SUMMARY)
        val vm = BackupViewModel(backup)
        assertEquals(SUMMARY, vm.state.value.previous)
        vm.onUndoClick()
        assertEquals(RestoreStep.Confirm(SUMMARY, null, undo = true), vm.state.value.restore)
        vm.onConfirmRestore()
        assertEquals(RestoreStep.ReadyToRestart, vm.state.value.restore)
        assertEquals(listOf("undo"), backup.calls)
    }

    @Test
    fun sinCopiaPreviaNoSeOfreceVolver() {
        val vm = BackupViewModel(FakeBackupService(previous = null))
        assertNull(vm.state.value.previous)
        vm.onUndoClick()
        assertNull(vm.state.value.restore)
    }

    @Test
    fun mensajesSegunLaBiblioteca() {
        assertEquals("Listo: se guardó «x» con todos tus cuadernos (la biblioteca está vacía).", BackupViewModel.createdMessage("x", 0))
        assertEquals("Listo: se guardó «x» con todos tus cuadernos y 1 PDF de la biblioteca.", BackupViewModel.createdMessage("x", 1))
        assertEquals("1 PDF", BackupViewModel.pdfCount(1))
        assertEquals("0 PDF", BackupViewModel.pdfCount(0))
    }
}
