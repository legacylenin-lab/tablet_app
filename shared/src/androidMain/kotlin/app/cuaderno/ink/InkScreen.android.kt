package app.cuaderno.ink

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import app.cuaderno.text.PageTextBlock
import app.cuaderno.text.TEXT_MOVE_BAR_HEIGHT
import app.cuaderno.text.TEXT_RESIZE_HANDLE_WIDTH
import androidx.compose.ui.platform.LocalDensity
import app.cuaderno.text.RichTextToolbar
import app.cuaderno.text.TextBlockUi
import app.cuaderno.text.TextBlocksViewModel
import com.mohamedrejeb.richeditor.model.RichTextState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cuaderno.AppServices
import app.cuaderno.ScopedViewModels
import app.cuaderno.model.EntityId
import app.cuaderno.model.Notebook
import app.cuaderno.notebooks.NotebookPagesViewModel
import app.cuaderno.notebooks.PageNav
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Matrix as ComposeMatrix

@Composable
actual fun InkPrototypeScreen(services: AppServices?, notebook: Notebook?, onBack: (() -> Unit)?, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        if (onBack != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack) { Text("← Cuadernos") }
                BackHandler(onBack = onBack)
            }
        }
        if (services != null && notebook != null) {
            NotebookPagesScreen(services, notebook, Modifier.weight(1f))
        } else {
            // Sin cuaderno no hay dónde guardar: solo la hoja de prueba que no guarda.
            ComposeInkScreen(Modifier.weight(1f))
        }
    }
}

/** Medidas de la página A4 en el "mundo" de la tinta: décimas de milímetro (docs/arquitectura.md §3.4). */
private const val PAGE_WIDTH_UNITS = 2100f
private const val PAGE_HEIGHT_UNITS = 2970f

private const val ERASER_RADIUS_PX = 28f

/** Un toque del dedo que se mueve menos que esto (en píxeles) cuenta como toque, no como arrastre. */
private const val TAP_SLOP_PX = 24f

/** Alto que se supone a un cuadro de texto antes de medirlo: una línea (unidades de la página). */
private const val MIN_TEXT_HEIGHT_UNITS = 60f

/** Un cuadro de texto nuevo: a 1 cm del borde de lo que se ve y 12 cm de ancho. */
private const val NEW_TEXT_MARGIN_UNITS = 100f
private const val NEW_TEXT_WIDTH_UNITS = 1200

/**
 * Hoja A4 donde escribe el lápiz con `androidx.ink`. Solo dibuja y recoge lo que hace el usuario:
 * qué trazos hay, guardar, borrar y deshacer lo decide [InkPageViewModel].
 * Solo el lápiz escribe; el dedo se descarta antes de llegar a la tinta (RF-03).
 */
@Composable
private fun InkPageScreen(
    services: AppServices,
    notebook: Notebook,
    pageId: EntityId,
    controls: PageControls,
    modifier: Modifier = Modifier,
    viewModel: InkPageViewModel = viewModel(key = pageId.toString()) {
        InkPageViewModel(pageId, services.ink, services.saveScope)
    },
    textViewModel: TextBlocksViewModel = viewModel(key = "text-$pageId") {
        TextBlocksViewModel(pageId, services.text, services.saveScope)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current by rememberUpdatedState(state)
    val texts by textViewModel.state.collectAsStateWithLifecycle()
    val currentTexts by rememberUpdatedState(texts)
    var lastDevice by remember { mutableStateOf("ninguno todavía") }
    var pageScale by remember { mutableFloatStateOf(1f) }
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }

    // Estado del editor de cada cuadro de texto (cursor, formato), y su alto en unidades de la página.
    val richStates = remember { HashMap<EntityId, RichTextState>() }
    fun richStateFor(block: TextBlockUi) = richStates.getOrPut(block.id) { RichTextState().apply { setHtml(block.html) } }
    val blockHeights = remember { mutableStateMapOf<EntityId, Float>() }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    /** El cuadro de texto que hay en el punto ([x], [y]) de la página, si hay alguno. */
    fun textBlockAt(x: Float, y: Float): TextBlockUi? = currentTexts.blocks.lastOrNull { b ->
        x >= b.x && x <= b.x + b.width && y >= b.y && y <= b.y + (blockHeights[b.id] ?: MIN_TEXT_HEIGHT_UNITS)
    }

    val density = LocalDensity.current
    val moveBarPx = with(density) { TEXT_MOVE_BAR_HEIGHT.toPx() }
    val resizeHandlePx = with(density) { TEXT_RESIZE_HANDLE_WIDTH.toPx() }

    /** ¿El punto de la pantalla ([sx], [sy]) cae en el cuadro que se edita, incluidas sus asas? */
    fun insideEditingArea(sx: Float, sy: Float): Boolean {
        val b = currentTexts.editing ?: return false
        val s = pageScale
        val left = pageOrigin.x + b.x * s
        val top = pageOrigin.y + b.y * s
        val bottom = top + (blockHeights[b.id] ?: MIN_TEXT_HEIGHT_UNITS) * s
        return sx >= left && sx <= left + b.width * s + resizeHandlePx && sy >= top - moveBarPx && sy <= bottom
    }

    // Dedos o lápiz que se apoyaron en el cuadro que se edita (o en sus asas): mientras sigan apoyados,
    // ni desplazan la hoja ni escriben; el cuadro se encarga de ellos (cursor, mover, cambiar el ancho).
    val grabbed = remember { HashSet<PointerId>() }

    // Al empezar a editar un cuadro: el cursor va a él y, en modo Teclado, se abre el teclado (en modo Lápiz
    // queda escondido). Al cambiar de modo, igual. Al terminar: se cierra.
    // Mientras se edita, cada cambio se le pasa al ViewModel, que lo guarda.
    LaunchedEffect(texts.editingId, texts.keyboardMode) {
        val editing = texts.editing
        if (editing == null) {
            focusManager.clearFocus()
            keyboard?.hide()
            return@LaunchedEffect
        }
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
        if (texts.keyboardMode) keyboard?.show() else keyboard?.hide()
        val rich = richStateFor(editing)
        snapshotFlow { rich.annotatedString }.collect { textViewModel.onContentChanged(editing.id, rich.toHtml()) }
    }

    val tools = state.tools
    val brush = remember(tools.tool, tools.colorArgb, tools.width) { brushFor(tools.tool, tools.colorArgb, tools.width) }
    val currentBrush = rememberUpdatedState(brush)
    val renderer = remember { CanvasStrokeRenderer.create() }

    // Trazos de la librería de tinta ya armados, por clave del ViewModel. Los recién dibujados se guardan
    // tal cual; los cargados de la base de datos se arman la primera vez que se dibujan.
    val inkCache = remember { HashMap<Long, Stroke?>() }

    // Trazos recién terminados que la lista del ViewModel aún no trae: la lista llega un instante después,
    // y sin esto el trazo desaparecería durante un cuadro (parpadeo). Se dibujan aparte hasta que llega.
    val justDrawn = remember { mutableStateListOf<Pair<Long, Stroke>>() }
    LaunchedEffect(state.strokes) {
        val known = state.strokes.mapTo(HashSet()) { it.key }
        justDrawn.removeAll { it.first in known }
    }

    LaunchedEffect(PenButton.clicks) { viewModel.onPenButtonClicks(PenButton.clicks) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("${notebook.title} · ${controls.nav.label}", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    state.message != null -> state.message!!
                    texts.message != null -> texts.message!!
                    !state.loaded -> "Abriendo la página…"
                    else -> "Trazos: ${state.visibleCount}  ·  Guardados en la base de datos  ·  " +
                        "Último: $lastDevice  ·  Botón del lápiz: ${PenButton.clicks} pulsaciones"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = controls.onPrevious, enabled = controls.nav.canGoBack, modifier = Modifier.padding(end = 8.dp)) { Text("◀") }
                OutlinedButton(onClick = controls.onNext, enabled = controls.nav.canGoForward, modifier = Modifier.padding(end = 8.dp)) { Text("▶") }
                OutlinedButton(onClick = controls.onAdd, modifier = Modifier.padding(end = 8.dp)) { Text("Página nueva") }
                OutlinedButton(onClick = controls.onDuplicate, modifier = Modifier.padding(end = 8.dp)) { Text("Duplicar") }
                OutlinedButton(onClick = controls.onMoveEarlier, enabled = controls.nav.canGoBack, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Mover ◀")
                }
                OutlinedButton(onClick = controls.onMoveLater, enabled = controls.nav.canGoForward, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Mover ▶")
                }
                OutlinedButton(onClick = controls.onDelete, enabled = controls.nav.total > 1) { Text("Eliminar página") }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = viewModel::onUndo, enabled = state.canUndo, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Deshacer")
                }
                OutlinedButton(onClick = viewModel::onRedo, enabled = state.canRedo, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Rehacer")
                }
                OutlinedButton(onClick = viewModel::onFit, enabled = state.viewport.zoom > 1f, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Ajustar (${(state.viewport.zoom * 100).roundToInt()}%)")
                }
                // Borrar trazos y borrar textos son botones aparte; los dos se pueden deshacer.
                Button(onClick = viewModel::onClear, enabled = state.visibleCount > 0, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Borrar trazos")
                }
                OutlinedButton(
                    onClick = { textViewModel.onDeleteAll()?.let(viewModel::onOtherAction) },
                    enabled = texts.blocks.isNotEmpty(),
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("Borrar textos") }
                OutlinedButton(
                    onClick = {
                        // El cuadro nuevo aparece arriba a la izquierda de lo que se ve de la hoja.
                        val s = pageScale.coerceAtLeast(0.0001f)
                        val vp = current.viewport
                        val x = (-vp.offsetX / s).coerceAtLeast(0f) + NEW_TEXT_MARGIN_UNITS
                        val y = (-vp.offsetY / s).coerceAtLeast(0f) + NEW_TEXT_MARGIN_UNITS
                        textViewModel.onAddText(x.roundToInt(), y.roundToInt(), NEW_TEXT_WIDTH_UNITS)
                    },
                    enabled = texts.loaded,
                ) { Text("Agregar texto") }
            }
        }

        val editingBlock = texts.editing
        if (editingBlock != null) {
            // Mientras se escribe con el teclado, la barra de la tinta deja su lugar a la del formato.
            RichTextToolbar(
                state = richStateFor(editingBlock),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Button(onClick = textViewModel::onDoneEditing, modifier = Modifier.padding(end = 8.dp)) { Text("Listo") }
                OutlinedButton(onClick = { textViewModel.onDelete(editingBlock.id)?.let(viewModel::onOtherAction) }) {
                    Text("Eliminar texto")
                }
            }
        } else {
            InkToolbar(
                settings = state.tools,
                eraserMode = state.eraserMode,
                onSettings = viewModel::onToolSettings,
                onEraserMode = viewModel::onEraserMode,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                // El dedo desplaza y hace zoom (RF-03). No consume nada: de que el dedo no escriba se encarga la hoja.
                // Si el lápiz está apoyado se ignora el dedo (la palma).
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            for (c in event.changes) {
                                if (c.pressed && !c.previousPressed && insideEditingArea(c.position.x, c.position.y)) grabbed += c.id
                            }
                            // Un dedo que se levanta sigue «del cuadro» en este mismo evento: así el soltar llega
                            // entero al cuadro (si la hoja lo consumiera, la etiqueta del modo no recibiría el toque).
                            // Se olvida en el evento siguiente, cuando ya no aparece.
                            grabbed.removeAll { id -> event.changes.none { it.id == id } }
                            if (event.changes.any { it.id in grabbed }) continue
                            val penDown = event.changes.any { (it.type == PointerType.Stylus || it.type == PointerType.Eraser) && it.pressed }
                            val touches = event.changes.filter { it.type == PointerType.Touch && it.pressed && it.previousPressed }
                            if (penDown || touches.isEmpty()) continue
                            val cx = touches.map { it.position.x }.average().toFloat()
                            val cy = touches.map { it.position.y }.average().toFloat()
                            val px = touches.map { it.previousPosition.x }.average().toFloat()
                            val py = touches.map { it.previousPosition.y }.average().toFloat()
                            var zoomFactor = 1f
                            if (touches.size >= 2) {
                                val now = (touches[0].position - touches[1].position).getDistance()
                                val before = (touches[0].previousPosition - touches[1].previousPosition).getDistance()
                                if (before > 0f) zoomFactor = now / before
                            }
                            val vw = size.width.toFloat()
                            val vh = size.height.toFloat()
                            val fit = minOf(vw / PAGE_WIDTH_UNITS, vh / PAGE_HEIGHT_UNITS)
                            val fitW = PAGE_WIDTH_UNITS * fit
                            val fitH = PAGE_HEIGHT_UNITS * fit
                            val vp = viewModel.state.value.viewport
                            viewModel.onViewport(vp.clamped(fitW, fitH, vw, vh).transformed(cx, cy, cx - px, cy - py, zoomFactor, fitW, fitH, vw, vh))
                        }
                    }
                },
            contentAlignment = Alignment.TopStart,
        ) {
            // La hoja A4 entera ajustada al espacio disponible (zoom 1). `scale` son píxeles por unidad del mundo.
            val fitScale = minOf(constraints.maxWidth / PAGE_WIDTH_UNITS, constraints.maxHeight / PAGE_HEIGHT_UNITS)
            val fitWidth = PAGE_WIDTH_UNITS * fitScale
            val fitHeight = PAGE_HEIGHT_UNITS * fitScale
            val vp = state.viewport.clamped(fitWidth, fitHeight, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val scale = fitScale * vp.zoom
            SideEffect {
                pageScale = scale
                pageOrigin = Offset(vp.offsetX, vp.offsetY)
            }
            // La capa de tinta mide siempre lo que la pantalla, no lo que la hoja ampliada: con zoom la hoja
            // llega a millones de píxeles y la tinta de baja latencia se quedaba sin memoria.
            val view by rememberUpdatedState(vp)
            val viewScale by rememberUpdatedState(scale)
            // De la hoja (décimas de mm) a la pantalla.
            val worldToScreen = remember(scale, vp.offsetX, vp.offsetY) {
                android.graphics.Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(vp.offsetX, vp.offsetY)
                }
            }
            // De la pantalla a la hoja: (punto - esquina) / escala. Matriz 4x4 por columnas.
            val screenToWorld = remember(scale, vp.offsetX, vp.offsetY) {
                val s = 1f / scale
                ComposeMatrix(
                    floatArrayOf(
                        s, 0f, 0f, 0f,
                        0f, s, 0f, 0f,
                        0f, 0f, 1f, 0f,
                        -vp.offsetX * s, -vp.offsetY * s, 0f, 1f,
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Se ejecuta ANTES (pasada Initial) que la tinta: lo que se consume aquí, la tinta no lo dibuja.
                    .pointerInput(Unit) {
                        // Toques del dedo en curso (dónde empezó cada uno), para distinguir un toque de un arrastre.
                        val touchStarts = HashMap<PointerId, Offset>()
                        var severalFingers = false
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.type == PointerType.Touch && it.pressed } > 1) severalFingers = true
                                for (change in event.changes) {
                                    lastDevice = when (change.type) {
                                        PointerType.Stylus -> "lápiz"
                                        PointerType.Eraser -> "borrador"
                                        PointerType.Touch -> "dedo"
                                        PointerType.Mouse -> "ratón"
                                        else -> "desconocido"
                                    }
                                    val s = viewScale
                                    val x = (change.position.x - view.offsetX) / s
                                    val y = (change.position.y - view.offsetY) / s
                                    val editing = currentTexts.editing
                                    // En el cuadro que se está editando (o sus asas), el lápiz y el dedo son del cuadro: no se tocan aquí.
                                    if (change.id in grabbed) continue
                                    val starts = change.pressed && !change.previousPressed
                                    when (inputActionFor(change.type, current.eraserMode)) {
                                        // Escribir con el lápiz fuera del cuadro termina la edición del texto.
                                        InputAction.DRAW ->
                                            if (starts && editing != null && !insideEditingArea(change.position.x, change.position.y)) {
                                                textViewModel.onDoneEditing()
                                            }
                                        InputAction.ERASE -> {
                                            change.consume()
                                            if (change.pressed) {
                                                viewModel.onEraseAt(x, y, radius = ERASER_RADIUS_PX / s)
                                            } else if (change.previousPressed) {
                                                viewModel.onEraseEnd()
                                            }
                                        }
                                        InputAction.NAVIGATE -> {
                                            change.consume()
                                            // Un toque corto con un dedo sobre un cuadro de texto lo abre para escribir;
                                            // fuera de los cuadros, termina la edición.
                                            if (starts) {
                                                touchStarts[change.id] = change.position
                                            } else if (!change.pressed && change.previousPressed) {
                                                val start = touchStarts.remove(change.id)
                                                val isTap = start != null && !severalFingers &&
                                                    (change.position - start).getDistance() < TAP_SLOP_PX
                                                if (isTap) {
                                                    val hit = textBlockAt(x, y)
                                                    if (hit != null) textViewModel.onEdit(hit.id) else textViewModel.onDoneEditing()
                                                }
                                                if (touchStarts.isEmpty()) severalFingers = false
                                            }
                                        }
                                        InputAction.IGNORE -> change.consume()
                                    }
                                }
                            }
                        }
                    },
            ) {
                // Fondo gris, hoja blanca y trazos terminados.
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        Color.White,
                        topLeft = Offset(vp.offsetX, vp.offsetY),
                        size = Size(PAGE_WIDTH_UNITS * scale, PAGE_HEIGHT_UNITS * scale),
                    )
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        native.concat(worldToScreen)
                        for (stroke in state.strokes) {
                            if (!stroke.visible) continue
                            val inkStroke = inkCache.getOrPut(stroke.key) { stroke.toInkStroke() } ?: continue
                            renderer.draw(native, inkStroke, worldToScreen)
                        }
                        for ((_, inkStroke) in justDrawn) renderer.draw(native, inkStroke, worldToScreen)
                        native.restore()
                    }
                }
                // Trazo en curso, con baja latencia. Al terminar, se lo pasa al ViewModel, que lo muestra y lo guarda.
                // Cuadros de texto. Los que no se editan van debajo de la tinta: el lápiz escribe encima de ellos.
                @Composable
                fun TextBlockOnPage(block: TextBlockUi, editing: Boolean) = key(block.id) {
                    PageTextBlock(
                        keyboardMode = texts.keyboardMode,
                        onToggleInputMode = textViewModel::onToggleInputMode,
                        block = block,
                        state = richStateFor(block),
                        editing = editing,
                        scale = scale,
                        offsetX = vp.offsetX,
                        offsetY = vp.offsetY,
                        focusRequester = focusRequester,
                        onHeight = {
                            blockHeights[block.id] = it
                            textViewModel.onMeasured(block.id, it)
                        },
                        onGeometry = { x, y, width -> textViewModel.onGeometryChanged(block.id, x, y, width) },
                        onGeometryDone = { textViewModel.onGeometryDone(block.id) },
                    )
                }
                for (block in texts.blocks) if (block.id != texts.editingId) TextBlockOnPage(block, editing = false)
                InProgressStrokes(
                    defaultBrush = if (state.loaded) brush else null,
                    nextBrush = { currentBrush.value },
                    pointerEventToWorldTransform = screenToWorld,
                    onStrokesFinished = { finished ->
                        val tool = current.tools.tool
                        val pairs = finished.mapNotNull { ink -> ink.toNewStroke(tool)?.let { ink to it } }
                        val keys = viewModel.onStrokesDrawn(pairs.map { it.second })
                        for ((key, pair) in keys.zip(pairs)) {
                            inkCache[key] = pair.first
                            justDrawn += key to pair.first
                        }
                    },
                )
                // El cuadro que se está editando va encima de la tinta, para que reciba el lápiz y el dedo.
                texts.editing?.let { TextBlockOnPage(it, editing = true) }
            }
        }
    }
}

/** Lo que la pantalla de la página necesita para moverse por el cuaderno. */
private class PageControls(
    val nav: PageNav,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onAdd: () -> Unit,
    val onDuplicate: () -> Unit,
    val onMoveEarlier: () -> Unit,
    val onMoveLater: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * Las páginas de un cuaderno: muestra una a la vez, con flechas y «Página nueva». Cada página
 * tiene su propio [InkPageViewModel], que se descarta al cambiar de página. Qué página se ve y qué
 * hace cada botón lo decide [NotebookPagesViewModel].
 */
@Composable
private fun NotebookPagesScreen(
    services: AppServices,
    notebook: Notebook,
    modifier: Modifier = Modifier,
    viewModel: NotebookPagesViewModel = viewModel(key = notebook.meta.id.toString()) {
        NotebookPagesViewModel(notebook.meta.id, services.notebooks, services.pages)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentPage = state.currentPage
    if (currentPage == null) {
        Text(state.message ?: "Abriendo el cuaderno…", modifier = modifier.padding(16.dp))
        return
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissDelete,
            title = { Text("¿Eliminar la ${state.nav.label.lowercase()}?") },
            text = { Text("La página y lo que escribiste en ella dejarán de verse.") },
            confirmButton = { Button(onClick = viewModel::onConfirmDelete) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = viewModel::onDismissDelete) { Text("Cancelar") } },
        )
    }
    key(currentPage.meta.id) {
        ScopedViewModels(key = currentPage.meta.id) {
            InkPageScreen(
                services, notebook,
                pageId = currentPage.meta.id,
                controls = PageControls(
                    nav = state.nav,
                    onPrevious = viewModel::onPrevious,
                    onNext = viewModel::onNext,
                    onAdd = viewModel::onAdd,
                    onDuplicate = viewModel::onDuplicate,
                    onMoveEarlier = viewModel::onMoveEarlier,
                    onMoveLater = viewModel::onMoveLater,
                    onDelete = viewModel::onDeleteClick,
                ),
                modifier = modifier,
            )
        }
    }
}
