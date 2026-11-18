package app.cuaderno.notebooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuaderno.domain.NotebookRepository
import app.cuaderno.model.Notebook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Qué diálogo está abierto en la lista de cuadernos. */
sealed interface NotebookDialog {
    data object Create : NotebookDialog
    data class Rename(val notebook: Notebook) : NotebookDialog
    data class Delete(val notebook: Notebook) : NotebookDialog
}

/**
 * Lo que muestra la lista de cuadernos. [notebooks] es `null` mientras se lee la base de datos.
 * [message] es un aviso de error para el usuario.
 */
data class NotebookListUiState(
    val notebooks: List<Notebook>? = null,
    val dialog: NotebookDialog? = null,
    val message: String? = null,
)

/**
 * ViewModel de la lista de cuadernos (RF-01, PQ-24). La pantalla solo dibuja [state] y avisa de lo
 * que toca el usuario; aquí se decide qué pasa y se habla con el repositorio
 * (flujo unidireccional, docs/arquitectura.md §2).
 */
class NotebookListViewModel(private val notebooks: NotebookRepository) : ViewModel() {
    private val local = MutableStateFlow(NotebookListUiState())

    val state: StateFlow<NotebookListUiState> =
        combine(notebooks.observeAll(), local) { list, ui -> ui.copy(notebooks = list) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, NotebookListUiState())

    fun onCreateClick() = openDialog(NotebookDialog.Create)

    fun onRenameClick(notebook: Notebook) = openDialog(NotebookDialog.Rename(notebook))

    fun onDeleteClick(notebook: Notebook) = openDialog(NotebookDialog.Delete(notebook))

    fun onDismissDialog() = openDialog(null)

    /** Confirma el diálogo de crear o renombrar. Un título vacío no hace nada (el botón ya está apagado). */
    fun onConfirmTitle(raw: String) {
        val title = NotebookTitle.normalize(raw) ?: return
        when (val d = local.value.dialog) {
            NotebookDialog.Create -> save { notebooks.create(title) }
            is NotebookDialog.Rename -> save { notebooks.rename(d.notebook.meta.id, title) }
            else -> return
        }
        openDialog(null)
    }

    /** Confirma el borrado (suave: el cuaderno deja de verse, sus datos siguen guardados). */
    fun onConfirmDelete() {
        val d = local.value.dialog as? NotebookDialog.Delete ?: return
        save { notebooks.delete(d.notebook.meta.id) }
        openDialog(null)
    }

    private fun openDialog(dialog: NotebookDialog?) {
        local.value = local.value.copy(dialog = dialog)
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                local.value = local.value.copy(message = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.value = local.value.copy(message = "No se pudo guardar el cambio: ${e.message}")
            }
        }
    }
}
