package app.cuaderno.text

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuaderno.domain.TextBlockRepository
import app.cuaderno.model.EntityId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un cuadro de texto sobre la hoja. Posición y ancho en décimas de milímetro (docs/arquitectura.md §3.4). */
data class TextBlockUi(val id: EntityId, val x: Int, val y: Int, val width: Int, val html: String)

/** Los cuadros de texto de una página y cuál se está editando (con el teclado abierto). */
data class TextBlocksUiState(
    val loaded: Boolean = false,
    val blocks: List<TextBlockUi> = emptyList(),
    val editingId: EntityId? = null,
    val message: String? = null,
) {
    val editing: TextBlockUi? get() = blocks.firstOrNull { it.id == editingId }
}

/**
 * ViewModel de los cuadros de texto de una página (RF-04, RF-05, PC-01). Crea cuadros, guarda lo
 * que se escribe (poco después de dejar de teclear, o al terminar) y borra con borrado suave.
 * Un cuadro que queda vacío al terminar de editarlo se elimina solo.
 *
 * Los guardados corren en [saveScope], que vive con la app: lo escrito no se pierde aunque el
 * usuario cambie de página justo después de teclear.
 */
class TextBlocksViewModel(
    private val pageId: EntityId,
    private val repo: TextBlockRepository,
    private val saveScope: CoroutineScope,
) : ViewModel() {
    private val _state = MutableStateFlow(TextBlocksUiState())
    val state: StateFlow<TextBlocksUiState> = _state.asStateFlow()

    /** Texto escrito que aún no se guardó, por cuadro. */
    private val pending = HashMap<EntityId, String>()
    private val saveJobs = HashMap<EntityId, Job>()

    /** Alto de cada cuadro tal como se ve, en unidades de la página. */
    private val heights = HashMap<EntityId, Float>()

    init {
        viewModelScope.launch {
            try {
                val saved = repo.observeByPage(pageId).first().map { TextBlockUi(it.meta.id, it.x, it.y, it.width, it.content) }
                _state.update { s -> s.copy(blocks = saved + s.blocks.filter { b -> saved.none { it.id == b.id } }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("No se pudieron abrir los textos: ${e.message}")
            }
            _state.update { it.copy(loaded = true) }
        }
    }

    /** La pantalla midió el cuadro [id]: mide [height] de alto, en unidades de la página. */
    fun onMeasured(id: EntityId, height: Float) {
        heights[id] = height
    }

    /**
     * Crea un cuadro vacío en ([x], [y]) con el ancho [width] y lo deja listo para escribir. Si ahí ya
     * hay otro texto, el nuevo se coloca justo debajo, para que no se encimen.
     */
    fun onAddText(x: Int, y: Int, width: Int) {
        onDoneEditing()
        val safeX = x.coerceIn(0, PAGE_WIDTH - MIN_WIDTH)
        val safeWidth = width.coerceIn(MIN_WIDTH, PAGE_WIDTH - safeX)
        val safeY = freeY(safeX, y.coerceIn(0, PAGE_HEIGHT - MIN_WIDTH), safeWidth).coerceIn(0, PAGE_HEIGHT - MIN_WIDTH)
        saveScope.launch {
            try {
                val block = repo.create(pageId, safeX, safeY, safeWidth)
                val ui = TextBlockUi(block.meta.id, block.x, block.y, block.width, block.content)
                _state.update { it.copy(blocks = it.blocks + ui, editingId = ui.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("No se pudo crear el texto: ${e.message}")
            }
        }
    }

    /** Empieza a editar el cuadro [id] (termina antes el que se estuviera editando). */
    fun onEdit(id: EntityId) {
        if (_state.value.editingId == id) return
        onDoneEditing()
        if (_state.value.blocks.any { it.id == id }) _state.update { it.copy(editingId = id) }
    }

    /** El usuario escribió en el cuadro [id]: se guarda un momento después de que deje de teclear. */
    fun onContentChanged(id: EntityId, rawHtml: String) {
        val html = RichTextHtml.normalize(rawHtml)
        val current = _state.value.blocks.firstOrNull { it.id == id } ?: return
        if (current.html == html) return
        _state.update { s -> s.copy(blocks = s.blocks.map { if (it.id == id) it.copy(html = html) else it }) }
        pending[id] = html
        saveJobs.remove(id)?.cancel()
        saveJobs[id] = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            flush(id)
        }
    }

    /** Termina de editar: guarda ya lo pendiente y, si el cuadro quedó vacío, lo elimina. */
    fun onDoneEditing() {
        val editing = _state.value.editing ?: return
        _state.update { it.copy(editingId = null) }
        if (RichTextHtml.isBlank(editing.html)) delete(editing.id) else flush(editing.id)
    }

    /** Elimina el cuadro [id] (borrado suave). */
    fun onDelete(id: EntityId) {
        if (_state.value.editingId == id) _state.update { it.copy(editingId = null) }
        delete(id)
    }

    override fun onCleared() {
        // Al salir de la página, lo escrito y aún no guardado se guarda igual.
        for (id in pending.keys.toList()) flush(id)
    }

    private fun delete(id: EntityId) {
        saveJobs.remove(id)?.cancel()
        pending.remove(id)
        _state.update { s -> s.copy(blocks = s.blocks.filterNot { it.id == id }) }
        saveScope.launch {
            try {
                repo.delete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("No se pudo eliminar el texto: ${e.message}")
            }
        }
    }

    private fun flush(id: EntityId) {
        saveJobs.remove(id)?.cancel()
        val html = pending.remove(id) ?: return
        saveScope.launch {
            try {
                repo.updateContent(id, html)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("No se pudo guardar el texto: ${e.message}")
            }
        }
    }

    /**
     * La primera altura desde [y] hacia abajo donde un cuadro en [x] de ancho [width] no se encima con
     * ningún otro (dejando [GAP] entre ellos). Un cuadro aún sin medir se supone de una línea.
     */
    private fun freeY(x: Int, y: Int, width: Int): Int {
        var top = y
        while (true) {
            val blocking = _state.value.blocks.filter { b ->
                val bottom = b.y + (heights[b.id] ?: LINE_HEIGHT).toInt()
                b.x < x + width && x < b.x + b.width && b.y < top + LINE_HEIGHT.toInt() + GAP && top < bottom + GAP
            }
            if (blocking.isEmpty()) return top
            top = blocking.maxOf { it.y + (heights[it.id] ?: LINE_HEIGHT).toInt() } + GAP
        }
    }

    private fun fail(message: String) = _state.update { it.copy(message = message) }

    companion object {
        /** Espera tras la última tecla antes de guardar. */
        const val SAVE_DELAY_MS = 800L

        /** Ancho mínimo de un cuadro: 2 cm. */
        const val MIN_WIDTH = 200

        /** Espacio entre un cuadro y el siguiente: 3 mm. */
        const val GAP = 30

        /** Alto supuesto de un cuadro que todavía no se midió: una línea, 6 mm. */
        const val LINE_HEIGHT = 60f

        private const val PAGE_WIDTH = 2100
        private const val PAGE_HEIGHT = 2970
    }
}
