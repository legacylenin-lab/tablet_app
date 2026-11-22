package app.cuaderno.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuaderno.domain.BackupResult
import app.cuaderno.domain.BackupService
import app.cuaderno.domain.BackupSummary
import app.cuaderno.domain.InspectResult
import app.cuaderno.domain.PlatformFile
import app.cuaderno.domain.RestoreResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Qué está pasando con una restauración. */
sealed interface RestoreStep {
    /** Esperando la confirmación del usuario. [undo] = volver a los datos de antes de la última restauración. */
    data class Confirm(val summary: BackupSummary, val fileName: String?, val undo: Boolean) : RestoreStep

    /** Todo preparado: falta reiniciar la app. */
    data object ReadyToRestart : RestoreStep
}

/**
 * Lo que muestra la pantalla de respaldo. [working] describe lo que se está haciendo (crear,
 * revisar o preparar), o es `null` si no se hace nada. [previous] es la copia automática de lo que
 * había antes de la última restauración, si existe.
 */
data class BackupUiState(
    val working: String? = null,
    val message: String? = null,
    val failed: Boolean = false,
    val restore: RestoreStep? = null,
    val previous: BackupSummary? = null,
)

/** ViewModel de la pantalla de respaldo (RF-17): crear un respaldo, restaurarlo y deshacer la restauración. */
class BackupViewModel(private val backup: BackupService) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()
    private var pendingSource: PlatformFile? = null

    init {
        viewModelScope.launch { _state.value = _state.value.copy(previous = safely { backup.previousData() }) }
    }

    /** El usuario eligió dónde guardar el respaldo. */
    fun onDestinationPicked(file: PlatformFile) = work("Creando el respaldo…") {
        when (val result = backup.export(file)) {
            is BackupResult.Created -> done(createdMessage(file.name, result.documentCount))
            is BackupResult.Failed -> failed(result.message)
        }
    }

    /** El usuario eligió un respaldo para restaurar: se revisa y se pide confirmación. */
    fun onSourcePicked(file: PlatformFile) = work("Revisando el respaldo…") {
        when (val result = backup.inspect(file)) {
            is InspectResult.Valid -> {
                pendingSource = file
                _state.value = _state.value.copy(working = null, restore = RestoreStep.Confirm(result.summary, file.name, undo = false))
            }
            is InspectResult.Rejected -> failed(result.message)
        }
    }

    /** «Volver a como estaba»: pide confirmación para deshacer la última restauración. */
    fun onUndoClick() {
        val previous = _state.value.previous ?: return
        pendingSource = null
        _state.value = _state.value.copy(message = null, restore = RestoreStep.Confirm(previous, null, undo = true))
    }

    fun onCancelRestore() {
        pendingSource = null
        _state.value = _state.value.copy(restore = null)
    }

    /** Confirmado: verifica todo el respaldo, guarda la copia previa y deja listo el cambio. */
    fun onConfirmRestore() {
        val step = _state.value.restore as? RestoreStep.Confirm ?: return
        val source = pendingSource
        if (!step.undo && source == null) return
        _state.value = _state.value.copy(restore = null)
        work("Preparando la restauración… (puede tardar un poco)") {
            val result = if (step.undo) backup.prepareUndoRestore() else backup.prepareRestore(source!!)
            when (result) {
                RestoreResult.ReadyToRestart -> _state.value = _state.value.copy(working = null, restore = RestoreStep.ReadyToRestart)
                is RestoreResult.Failed -> failed(result.message)
            }
        }
    }

    fun onDismissMessage() {
        _state.value = _state.value.copy(message = null, failed = false)
    }

    private fun work(label: String, block: suspend () -> Unit) {
        if (_state.value.working != null) return
        _state.value = _state.value.copy(working = label, message = null, failed = false)
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed("Algo salió mal: ${e.message}. No se cambió nada.")
            }
        }
    }

    private fun done(message: String) {
        _state.value = _state.value.copy(working = null, message = message, failed = false)
    }

    private fun failed(message: String) {
        _state.value = _state.value.copy(working = null, message = message, failed = true)
    }

    private suspend fun <T> safely(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        fun createdMessage(fileName: String, documentCount: Int): String {
            val library = when (documentCount) {
                0 -> "la biblioteca está vacía"
                1 -> "y 1 PDF de la biblioteca"
                else -> "y $documentCount PDF de la biblioteca"
            }
            return if (documentCount == 0) {
                "Listo: se guardó «$fileName» con todos tus cuadernos ($library)."
            } else {
                "Listo: se guardó «$fileName» con todos tus cuadernos $library."
            }
        }

        fun pdfCount(count: Int): String = if (count == 1) "1 PDF" else "$count PDF"
    }
}
