package app.cuaderno.ink

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current by rememberUpdatedState(state)
    var lastDevice by remember { mutableStateOf("ninguno todavía") }

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
                    !state.loaded -> "Abriendo la página…"
                    else -> "Trazos: ${state.visibleCount}  ·  Guardados en la base de datos  ·  " +
                        "Último: $lastDevice  ·  Botón del lápiz: ${PenButton.clicks} pulsaciones"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = controls.onPrevious, enabled = controls.nav.canGoBack, modifier = Modifier.padding(end = 8.dp)) { Text("◀") }
                OutlinedButton(onClick = controls.onNext, enabled = controls.nav.canGoForward, modifier = Modifier.padding(end = 8.dp)) { Text("▶") }
                OutlinedButton(onClick = controls.onAdd, modifier = Modifier.padding(end = 8.dp)) { Text("Página nueva") }
                OutlinedButton(onClick = controls.onDuplicate, modifier = Modifier.padding(end = 8.dp)) { Text("Duplicar") }
                OutlinedButton(onClick = controls.onDelete, enabled = controls.nav.total > 1) { Text("Eliminar página") }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = viewModel::onUndo, enabled = state.canUndo, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Deshacer")
                }
                OutlinedButton(onClick = viewModel::onRedo, enabled = state.canRedo, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Rehacer")
                }
                OutlinedButton(onClick = viewModel::onFit, enabled = state.viewport.zoom > 1f, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Ajustar (${(state.viewport.zoom * 100).roundToInt()}%)")
                }
                Button(onClick = viewModel::onClear) { Text("Limpiar") }
            }
        }

        InkToolbar(
            settings = state.tools,
            eraserMode = state.eraserMode,
            onSettings = viewModel::onToolSettings,
            onEraserMode = viewModel::onEraserMode,
        )

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
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                for (change in event.changes) {
                                    lastDevice = when (change.type) {
                                        PointerType.Stylus -> "lápiz"
                                        PointerType.Eraser -> "borrador"
                                        PointerType.Touch -> "dedo"
                                        PointerType.Mouse -> "ratón"
                                        else -> "desconocido"
                                    }
                                    when (inputActionFor(change.type, current.eraserMode)) {
                                        InputAction.DRAW -> Unit
                                        InputAction.ERASE -> {
                                            change.consume()
                                            if (change.pressed) {
                                                val s = viewScale
                                                viewModel.onEraseAt(
                                                    x = (change.position.x - view.offsetX) / s,
                                                    y = (change.position.y - view.offsetY) / s,
                                                    radius = ERASER_RADIUS_PX / s,
                                                )
                                            } else if (change.previousPressed) {
                                                viewModel.onEraseEnd()
                                            }
                                        }
                                        InputAction.NAVIGATE, InputAction.IGNORE -> change.consume()
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
                    onDelete = viewModel::onDeleteClick,
                ),
                modifier = modifier,
            )
        }
    }
}
