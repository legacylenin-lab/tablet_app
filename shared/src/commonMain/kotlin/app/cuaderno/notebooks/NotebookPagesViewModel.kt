package app.cuaderno.notebooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuaderno.domain.EnsureFirstPage
import app.cuaderno.domain.NotebookRepository
import app.cuaderno.domain.PageRepository
import app.cuaderno.model.EntityId
import app.cuaderno.model.Page
import app.cuaderno.model.PageTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lo que muestra un cuaderno abierto. [pages] es `null` mientras se lee la base de datos.
 * [index] es la página que se ve (desde 0), siempre válida para la lista.
 */
data class NotebookPagesUiState(
    val pages: List<Page>? = null,
    val index: Int = 0,
    /** Página que la vista sigue aunque cambie de lugar (al moverla); manda sobre [index]. */
    val follow: EntityId? = null,
    val confirmDelete: Boolean = false,
    val message: String? = null,
) {
    /** La página que se ve, o `null` si todavía no hay ninguna. */
    val currentPage: Page? get() = pages?.getOrNull(index)

    val nav: PageNav get() = PageNav.at(index, pages?.size ?: 0)
}

/**
 * ViewModel de un cuaderno abierto (RF-01, RF-08, PQ-24): qué página se ve y las acciones sobre
 * las páginas (nueva, duplicar, eliminar con borrado suave). Un cuaderno sin páginas recibe una
 * en blanco al abrirse.
 */
class NotebookPagesViewModel(
    private val notebookId: EntityId,
    notebooks: NotebookRepository,
    private val pages: PageRepository,
) : ViewModel() {
    private val ensureFirstPage = EnsureFirstPage(notebooks, pages)
    private val local = MutableStateFlow(NotebookPagesUiState())

    val state: StateFlow<NotebookPagesUiState> =
        combine(
            pages.observeByNotebook(notebookId).onEach { if (it.isEmpty()) save { ensureFirstPage.forNotebook(notebookId) } },
            local,
        ) { list, ui ->
            val followed = ui.follow?.let { id -> list.indexOfFirst { it.meta.id == id } }?.takeIf { it >= 0 }
            ui.copy(pages = list, index = PageNav.clampIndex(followed ?: ui.index, list.size))
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, NotebookPagesUiState())

    fun onPrevious() = goTo(state.value.index - 1)

    fun onNext() = goTo(state.value.index + 1)

    /** Agrega una página en blanco justo después de la actual y pasa a ella. */
    fun onAdd() {
        val current = state.value.currentPage ?: return
        val index = state.value.index
        save {
            pages.add(notebookId, PageTemplate.BLANK, after = current.meta.id)
            goTo(index + 1)
        }
    }

    /** Copia la página actual (con su contenido) justo después y pasa a la copia. */
    fun onDuplicate() {
        val current = state.value.currentPage ?: return
        val index = state.value.index
        save {
            pages.duplicate(current.meta.id)
            goTo(index + 1)
        }
    }

    /** Mueve la página actual un lugar hacia el principio; la vista la sigue. */
    fun onMoveEarlier() {
        val s = state.value
        val list = s.pages ?: return
        if (s.index < 1) return
        move(list[s.index], after = list.getOrNull(s.index - 2))
    }

    /** Mueve la página actual un lugar hacia el final; la vista la sigue. */
    fun onMoveLater() {
        val s = state.value
        val list = s.pages ?: return
        if (s.index >= list.size - 1) return
        move(list[s.index], after = list[s.index + 1])
    }

    private fun move(page: Page, after: Page?) {
        // La vista sigue a la página por su id: así no muestra otra mientras llega la lista nueva.
        local.value = local.value.copy(index = state.value.index, follow = page.meta.id)
        save { pages.move(page.meta.id, after = after?.meta?.id) }
    }

    /** Pide confirmación para eliminar. Con una sola página no se puede eliminar. */
    fun onDeleteClick() {
        if ((state.value.pages?.size ?: 0) > 1) local.value = local.value.copy(confirmDelete = true)
    }

    fun onDismissDelete() {
        local.value = local.value.copy(confirmDelete = false)
    }

    /** Elimina la página actual (borrado suave) y se queda en la misma posición, o en la anterior si era la última. */
    fun onConfirmDelete() {
        val s = state.value
        val current = s.currentPage
        local.value = local.value.copy(confirmDelete = false)
        if (current == null || (s.pages?.size ?: 0) <= 1) return
        goTo(PageNav.indexAfterDelete(s.index, s.pages!!.size))
        save { pages.delete(current.meta.id) }
    }

    private fun goTo(index: Int) {
        local.value = local.value.copy(index = index.coerceAtLeast(0), follow = null)
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
