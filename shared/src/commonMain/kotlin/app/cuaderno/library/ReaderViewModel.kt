package app.cuaderno.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuaderno.domain.DocumentRepository
import app.cuaderno.domain.PdfEngine
import app.cuaderno.domain.RenderedPage
import app.cuaderno.ink.PageViewport
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Parte de la página dibujada nítida con zoom: [image] es el rectángulo que empieza en ([left], [top]),
 * en píxeles de la página ampliada a [zoom].
 */
class ZoomDetail(val image: RenderedPage, val zoom: Float, val left: Int, val top: Int)

/** Un rectángulo de la página ampliada: la página mide [pageWidthPx] de ancho y se dibuja desde ([left], [top]). */
data class Region(val pageWidthPx: Int, val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Lo que muestra el lector de un PDF. [index] es la página que se ve (desde 0). [image] es la página
 * entera a lo ancho de la pantalla (zoom 1); mientras llega la de otra página, [loading] está activo.
 * Con zoom, [image] se amplía (borrosa) y encima va [detail], nítida, cuando el usuario suelta los dedos.
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
    val viewport: PageViewport = PageViewport(1f, 0f, 0f),
    val detail: ZoomDetail? = null,
) {
    val nav: PageNav get() = PageNav.at(index, document.pageCount)
}

/**
 * ViewModel del lector de PDF (RF-12): pasar páginas, ir a una página, zoom y recordar la última
 * leída. Abre en la página donde se quedó. La página se guarda cuando el usuario se detiene
 * [saveDelayMillis] en ella, para no escribir en la base de datos con cada página que pasa; ese
 * guardado corre en [saveScope], que vive con la app, para que no se pierda si sale del lector enseguida.
 *
 * El zoom usa [PageViewport], igual que los cuadernos (100% a 500%). Al quedarse quieto
 * [detailDelayMillis], se dibuja nítido solo el rectángulo que se ve: la memoria no crece con el zoom.
 */
class ReaderViewModel(
    document: Document,
    private val pdf: PdfEngine,
    private val documents: DocumentRepository,
    private val saveScope: CoroutineScope,
    private val saveDelayMillis: Long = 1_000,
    private val detailDelayMillis: Long = 200,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ReaderUiState(document, index = PageNav.clampIndex(document.lastReadPage, document.pageCount)),
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var widthPx = 0
    private var heightPx = 0
    private var renderJob: Job? = null
    private var detailJob: Job? = null
    private var saveJob: Job? = null

    /** La pantalla avisa cuánto mide el área de la página; la página se dibuja a ese ancho. */
    fun onSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || (width == widthPx && height == heightPx)) return
        val widthChanged = width != widthPx
        widthPx = width
        heightPx = height
        if (widthChanged) {
            resetZoom()
            render()
        }
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

    /** Los dedos movieron o ampliaron la página. La pantalla ya lo limita con [PageViewport.clamped]. */
    fun onViewport(viewport: PageViewport) {
        _state.value = _state.value.copy(viewport = viewport)
        scheduleDetail()
    }

    /** «Ajustar»: vuelve a la página entera a lo ancho, arriba. */
    fun onFit() {
        resetZoom()
    }

    private fun resetZoom() {
        detailJob?.cancel()
        _state.value = _state.value.copy(viewport = PageViewport(1f, 0f, 0f), detail = null)
    }

    private fun goTo(target: Int) {
        val s = _state.value
        val index = PageNav.clampIndex(target, s.document.pageCount)
        if (index == s.index) return
        _state.value = s.copy(index = index)
        resetZoom()
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

    /** Cuando los dedos se quedan quietos, dibuja nítido lo que se ve. Con zoom 100% no hace falta. */
    private fun scheduleDetail() {
        detailJob?.cancel()
        val s = _state.value
        val image = s.image
        if (s.viewport.zoom <= NO_DETAIL_ZOOM || image == null || s.imageIndex != s.index) {
            if (s.detail != null) _state.value = s.copy(detail = null)
            return
        }
        val viewport = s.viewport
        val region = visibleRegion(viewport, image.width.toFloat(), image.height.toFloat(), widthPx.toFloat(), heightPx.toFloat())
            ?: return
        detailJob = viewModelScope.launch {
            delay(detailDelayMillis)
            try {
                val detail = pdf.renderRegion(
                    s.document.fileHash, s.index, region.pageWidthPx, region.left, region.top, region.width, region.height,
                )
                if (_state.value.index == s.index) {
                    _state.value = _state.value.copy(detail = ZoomDetail(detail, viewport.zoom, region.left, region.top))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin la parte nítida se sigue viendo la página ampliada (algo borrosa): no es grave.
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

    companion object {
        /** Por debajo de este zoom la imagen de la página ya se ve nítida. */
        private const val NO_DETAIL_ZOOM = 1.05f

        /** El número que escribió el usuario, si es una página que existe (de 1 a [total]); si no, `null`. */
        fun parsePageNumber(text: String, total: Int): Int? =
            text.trim().toIntOrNull()?.takeIf { it in 1..total }

        /** Página (desde 1) que corresponde a una posición de la barra deslizable. */
        fun sliderPage(value: Float, total: Int): Int = value.roundToInt().coerceIn(1, maxOf(1, total))

        /**
         * La parte de la página ampliada que se ve en pantalla (sin los bordes fuera de la hoja), o
         * `null` si no se ve nada. [fitWidth] y [fitHeight] miden la página con zoom 1.
         */
        fun visibleRegion(viewport: PageViewport, fitWidth: Float, fitHeight: Float, viewWidth: Float, viewHeight: Float): Region? {
            val pageW = fitWidth * viewport.zoom
            val pageH = fitHeight * viewport.zoom
            val left = floor(max(0f, -viewport.offsetX)).toInt()
            val top = floor(max(0f, -viewport.offsetY)).toInt()
            val right = ceil(min(pageW, viewWidth - viewport.offsetX)).toInt()
            val bottom = ceil(min(pageH, viewHeight - viewport.offsetY)).toInt()
            if (right <= left || bottom <= top) return null
            return Region(pageW.roundToInt(), left, top, right - left, bottom - top)
        }
    }
}
