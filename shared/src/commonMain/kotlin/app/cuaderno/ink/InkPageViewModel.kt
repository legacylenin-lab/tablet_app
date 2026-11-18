package app.cuaderno.ink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuaderno.UndoableAction
import app.cuaderno.domain.InkRepository
import app.cuaderno.domain.NewStroke
import app.cuaderno.model.EntityId
import app.cuaderno.model.StrokeCodec
import app.cuaderno.model.StrokeTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Un trazo de la página tal como lo ve la interfaz. [key] lo identifica en pantalla desde que se
 * dibuja, antes de que la base de datos le dé su identificador. [points] va codificado (§5.1), en
 * décimas de milímetro de la página.
 */
class PageStroke(
    val key: Long,
    val tool: StrokeTool,
    val colorArgb: Int,
    val baseWidth: Float,
    val points: ByteArray,
    val visible: Boolean = true,
) {
    /** Puntos para saber si el borrador lo toca. Se decodifican una sola vez. */
    internal val hitPoints: List<InkPoint> by lazy {
        try {
            StrokeCodec.decode(points).map { InkPoint(it.x.toFloat(), it.y.toFloat(), 1f) }
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }

    internal fun withVisible(visible: Boolean) = PageStroke(key, tool, colorArgb, baseWidth, points, visible)
}

/** Lo que muestra la hoja: sus trazos, la herramienta elegida y el zoom. */
data class InkPageUiState(
    val loaded: Boolean = false,
    val strokes: List<PageStroke> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val tools: ToolSettings = ToolSettings(),
    val eraserMode: Boolean = false,
    val viewport: PageViewport = PageViewport(1f, 0f, 0f),
    val message: String? = null,
) {
    val visibleCount: Int get() = strokes.count { it.visible }
}

/**
 * ViewModel de una página con tinta (RF-02, RF-03, PQ-24). Carga los trazos de [pageId], guarda
 * cada trazo nuevo, borra con borrado suave y lleva deshacer/rehacer. La pantalla de Android solo
 * dibuja con la librería de tinta y avisa aquí de cada trazo terminado y de cada toque del borrador.
 *
 * Los guardados corren en [saveScope], que vive con la app: no se cancelan si el usuario cambia de
 * página justo después de escribir.
 */
class InkPageViewModel(
    private val pageId: EntityId,
    private val ink: InkRepository,
    private val saveScope: CoroutineScope,
) : ViewModel() {
    private enum class Kind { DRAW, ERASE }

    /** Algo que hizo el usuario con la tinta y se puede deshacer: dibujar o borrar uno o varios trazos. */
    private inner class Action(val kind: Kind, val keys: List<Long>) : UndoableAction {
        override fun undo() = apply(this, undo = true)

        override fun redo() = apply(this, undo = false)
    }

    private val _state = MutableStateFlow(InkPageUiState())
    val state: StateFlow<InkPageUiState> = _state.asStateFlow()

    /** Historial de la hoja: trazos y, por [onOtherAction], también lo que se hace con los textos. */
    private val undoStack = ArrayDeque<UndoableAction>()
    private val redoStack = ArrayDeque<UndoableAction>()
    private val ids = HashMap<Long, CompletableDeferred<EntityId>>()
    private val erasing = mutableListOf<Long>()
    private var nextKey = 1L

    /** Pulsaciones del botón del lápiz ya atendidas: las de antes de abrir la página no cuentan. */
    private var seenPenClicks = PenButton.clicks

    init {
        viewModelScope.launch {
            try {
                val saved = ink.observeStrokes(pageId).first().map { s ->
                    val key = nextKey++
                    ids[key] = CompletableDeferred(s.meta.id)
                    PageStroke(key, s.tool, s.colorArgb, s.baseWidth, s.points)
                }
                _state.update { it.copy(strokes = saved + it.strokes) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("No se pudo abrir la página: ${e.message}")
            }
            _state.update { it.copy(loaded = true) }
        }
    }

    /** El lápiz terminó uno o varios trazos: se muestran, se pueden deshacer y se guardan. */
    fun onStrokesDrawn(drawn: List<NewStroke>): List<Long> {
        if (drawn.isEmpty()) return emptyList()
        val added = drawn.map { PageStroke(nextKey++, it.tool, it.colorArgb, it.baseWidth, it.points) }
        _state.update { it.copy(strokes = it.strokes + added) }
        push(Action(Kind.DRAW, added.map { it.key }))
        for ((stroke, new) in added.zip(drawn)) {
            val id = CompletableDeferred<EntityId>()
            ids[stroke.key] = id
            saveScope.launch {
                try {
                    id.complete(ink.add(pageId, new).meta.id)
                } catch (e: CancellationException) {
                    id.cancel()
                    throw e
                } catch (e: Exception) {
                    id.completeExceptionally(e)
                    fail("No se pudo guardar un trazo: ${e.message}")
                }
            }
        }
        return added.map { it.key }
    }

    /** El borrador pasa por ([x], [y]) en unidades de la página: oculta los trazos a [radius] o menos. */
    fun onEraseAt(x: Float, y: Float, radius: Float) {
        val hit = _state.value.strokes.filter { it.visible && InkMath.isNear(it.hitPoints, x, y, radius) }.map { it.key }
        if (hit.isEmpty()) return
        erasing += hit
        setVisible(hit, false)
    }

    /** El borrador se levantó: todo lo borrado en ese gesto se deshace de una vez. */
    fun onEraseEnd() {
        if (erasing.isEmpty()) return
        val keys = erasing.toList()
        erasing.clear()
        push(Action(Kind.ERASE, keys))
        persist(keys, visible = false)
    }

    /** Borra todos los trazos visibles (se puede deshacer). */
    fun onClear() {
        val keys = _state.value.strokes.filter { it.visible }.map { it.key }
        if (keys.isEmpty()) return
        setVisible(keys, false)
        push(Action(Kind.ERASE, keys))
        persist(keys, visible = false)
    }

    /**
     * Otra parte de la página hizo algo que se puede deshacer (por ejemplo, eliminar un texto): entra en
     * el mismo historial que los trazos, para que «Deshacer» siga el orden en que el usuario hizo las cosas.
     */
    fun onOtherAction(action: UndoableAction) = push(action)

    fun onUndo() {
        val action = undoStack.removeLastOrNull() ?: return
        action.undo()
        redoStack.addLast(action)
        refreshHistory()
    }

    fun onRedo() {
        val action = redoStack.removeLastOrNull() ?: return
        action.redo()
        undoStack.addLast(action)
        refreshHistory()
    }

    fun onToolSettings(settings: ToolSettings) = _state.update { it.copy(tools = settings) }

    fun onEraserMode(on: Boolean) = _state.update { it.copy(eraserMode = on) }

    /** El botón del lápiz lleva [clicks] pulsaciones en total: cada una nueva alterna lápiz y borrador. */
    fun onPenButtonClicks(clicks: Int) {
        val fresh = clicks - seenPenClicks
        seenPenClicks = clicks
        if (fresh > 0 && fresh % 2 == 1) _state.update { it.copy(eraserMode = !it.eraserMode) }
    }

    fun onViewport(viewport: PageViewport) = _state.update { it.copy(viewport = viewport) }

    /** Vuelve a ver la hoja entera («Ajustar»). */
    fun onFit() = onViewport(PageViewport(1f, 0f, 0f))

    private fun apply(action: Action, undo: Boolean) {
        // Deshacer un dibujo lo oculta; rehacerlo lo muestra. Con un borrado, al revés.
        val show = (action.kind == Kind.DRAW) != undo
        setVisible(action.keys, show)
        persist(action.keys, show)
    }

    private fun push(action: UndoableAction) {
        undoStack.addLast(action)
        redoStack.clear()
        refreshHistory()
    }

    private fun refreshHistory() =
        _state.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }

    private fun setVisible(keys: List<Long>, visible: Boolean) {
        val set = keys.toSet()
        _state.update { s -> s.copy(strokes = s.strokes.map { if (it.key in set) it.withVisible(visible) else it }) }
    }

    private fun persist(keys: List<Long>, visible: Boolean) {
        val pending = keys.mapNotNull { ids[it] }
        saveScope.launch {
            try {
                val saved = pending.mapNotNull { it.idOrNull() }
                if (saved.isEmpty()) return@launch
                if (visible) ink.restore(saved) else ink.erase(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("No se pudo guardar el cambio: ${e.message}")
            }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(message = message) }

    /** El identificador guardado, o `null` si el trazo no se pudo guardar. */
    private suspend fun CompletableDeferred<EntityId>.idOrNull(): EntityId? = try {
        await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
