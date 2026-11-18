package app.cuaderno.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuaderno.domain.DocumentRepository
import app.cuaderno.domain.ImportResult
import app.cuaderno.domain.PlatformFile
import app.cuaderno.model.Document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lo que muestra la biblioteca. [documents] es `null` mientras se lee la base de datos.
 * [importing] está activo mientras se copia un PDF; [message] es el aviso del último intento.
 */
data class LibraryUiState(
    val documents: List<Document>? = null,
    val importing: Boolean = false,
    val message: String? = null,
)

/** ViewModel de la biblioteca (RF-10): lista de PDF e importar uno nuevo. */
class LibraryViewModel(private val documents: DocumentRepository) : ViewModel() {
    private val local = MutableStateFlow(LibraryUiState())

    val state: StateFlow<LibraryUiState> =
        combine(documents.observeAll(), local) { list, ui -> ui.copy(documents = list) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    /** El usuario eligió un archivo en el selector del sistema. */
    fun onFilePicked(file: PlatformFile) {
        if (local.value.importing) return
        local.value = local.value.copy(importing = true, message = null)
        viewModelScope.launch {
            val message = try {
                messageFor(documents.import(file))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo importar «${file.name}»: ${e.message}"
            }
            local.value = local.value.copy(importing = false, message = message)
        }
    }

    fun onDismissMessage() {
        local.value = local.value.copy(message = null)
    }

    companion object {
        fun messageFor(result: ImportResult): String = when (result) {
            is ImportResult.Imported ->
                "Se agregó «${result.document.title}» (${pagesLabel(result.document.pageCount)})."
            is ImportResult.AlreadyExists ->
                "«${result.document.title}» ya estaba en la biblioteca: no se duplicó."
            is ImportResult.NotPdf -> if (result.isWord) {
                "Solo se pueden importar PDF. En Word, usa Archivo → Guardar como → PDF, y luego importa ese PDF."
            } else {
                "Ese archivo no es un PDF."
            }
            ImportResult.Corrupt -> "No se pudo abrir el PDF: puede estar dañado o protegido con contraseña."
        }

        fun pagesLabel(count: Int): String = if (count == 1) "1 página" else "$count páginas"

        /** Tamaño legible: «850 KB», «12,4 MB». */
        fun sizeLabel(bytes: Long): String = when {
            bytes < 1024 * 1024 -> "${maxOf(1, (bytes + 512) / 1024)} KB"
            else -> {
                val tenths = (bytes * 10 + 512 * 1024) / (1024 * 1024)
                "${tenths / 10},${tenths % 10} MB"
            }
        }
    }
}
