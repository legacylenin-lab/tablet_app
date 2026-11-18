package app.cuaderno.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuaderno.domain.DocumentRepository
import app.cuaderno.domain.PdfEngine
import app.cuaderno.domain.RenderedPage
import app.cuaderno.model.Document
import app.cuaderno.notebooks.PageNav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lo que muestra el lector de un PDF. [index] es la página que se ve (desde 0). [image] es la
 * última imagen dibujada; mientras llega la de otra página, [loading] está activo.
 */
data class ReaderUiState(
    val document: Document,
    val index: Int,
    val image: RenderedPage? = null,
    val imageIndex: Int? = null,
    val loading: Boolean = false,
    val message: String? = null,
    /** Está abierta la ventanita «Ir a la página». */
    val goToDialog: Boolean = false,
) {
    val nav: PageNav get() = PageNav.at(index, document.pageCount)
}

/**
 * ViewModel del lector de PDF (RF-12): pasar páginas y recordar la última leída. Abre en la página
 * donde se quedó. La página se guarda cuando el usuario se detiene [saveDelayMillis] en ella, para
 * no escribir en la base de datos con cada página que pasa; ese guardado corre en [saveScope], que
 * vive con la app, para que no se pierda si sale del lector enseguida.
 */
class ReaderViewModel(
    document: Document,
    private val pdf: PdfEngine,
    private val documents: DocumentRepository,
    private val saveScope: CoroutineScope,
    private val saveDelayMillis: Long = 1_000,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ReaderUiState(document, index = PageNav.clampIndex(document.lastReadPage, document.pageCount)),
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var widthPx = 0
    private var renderJob: Job? = null
    private var saveJob: Job? = null

    /** La pantalla avisa cuánto mide de ancho; la página se dibuja a ese tamaño. */
    fun onWidth(px: Int) {
        if (px <= 0 || px == widthPx) return
        widthPx = px
        render()
    }

    fun onPrevious() = goTo(_state.value.index - 1)

    fun onNext() = goTo(_state.value.index + 1)

    /** Salta a la página [number] (desde 1), por ejemplo al soltar la barra deslizable. */
    fun onGoToPage(number: Int) = goTo(number - 1)

    fun onGoToClick() {
        _state.value = _state.value.copy(goToDialog = true)
    }

    fun onDismissGoTo() {
        _state.value = _state.value.copy(goToDialog = false)
    }

    /** Confirma la ventanita «Ir a la página». Un número inválido no hace nada (el botón ya está apagado). */
    fun onConfirmGoTo(text: String) {
        val number = parsePageNumber(text, _state.value.document.pageCount) ?: return
        _state.value = _state.value.copy(goToDialog = false)
        onGoToPage(number)
    }

    companion object {
        /** El número que escribió el usuario, si es una página que existe (de 1 a [total]); si no, `null`. */
        fun parsePageNumber(text: String, total: Int): Int? =
            text.trim().toIntOrNull()?.takeIf { it in 1..total }

        /** Página (desde 1) que corresponde a una posición de la barra deslizable. */
        fun sliderPage(value: Float, total: Int): Int = value.roundToInt().coerceIn(1, maxOf(1, total))
    }

    private fun goTo(target: Int) {
        val s = _state.value
        val index = PageNav.clampIndex(target, s.document.pageCount)
        if (index == s.index) return
        _state.value = s.copy(index = index)
        render()
        scheduleSave(index)
    }

    private fun render() {
        if (widthPx <= 0) return
        renderJob?.cancel()
        val s = _state.value
        _state.value = s.copy(loading = true)
        renderJob = viewModelScope.launch {
            try {
                val image = pdf.renderPage(s.document.fileHash, s.index, widthPx)
                _state.value = _state.value.copy(image = image, imageIndex = s.index, loading = false, message = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, message = "No se pudo mostrar la página: ${e.message}")
            }
        }
    }

    private fun scheduleSave(index: Int) {
        saveJob?.cancel()
        val id = _state.value.document.meta.id
        saveJob = saveScope.launch {
            delay(saveDelayMillis)
            try {
                documents.setLastReadPage(id, index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Recordar la página no es crítico: si falla, el lector sigue funcionando.
            }
        }
    }
}
