package app.cuaderno.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuaderno.domain.BackupResult
import app.cuaderno.domain.BackupService
import app.cuaderno.domain.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lo que muestra la pantalla de respaldo. [working] está activo mientras se crea el archivo. */
data class BackupUiState(
    val working: Boolean = false,
    val message: String? = null,
    val failed: Boolean = false,
)

/** ViewModel de la pantalla de respaldo (RF-17). */
class BackupViewModel(private val backup: BackupService) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** El usuario eligió dónde guardar el respaldo. */
    fun onDestinationPicked(file: PlatformFile) {
        if (_state.value.working) return
        _state.value = BackupUiState(working = true)
        viewModelScope.launch {
            val result = try {
                backup.export(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupResult.Failed("No se pudo crear el respaldo: ${e.message}")
            }
            _state.value = when (result) {
                is BackupResult.Created -> BackupUiState(message = createdMessage(file.name, result.documentCount))
                is BackupResult.Failed -> BackupUiState(message = result.message, failed = true)
            }
        }
    }

    fun onDismissMessage() {
        _state.value = _state.value.copy(message = null, failed = false)
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
    }
}
