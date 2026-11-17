package app.cuaderno.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.roundToInt
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
import androidx.ink.strokes.StrokeInput
import app.cuaderno.AppServices
import app.cuaderno.domain.EnsureFirstPage
import app.cuaderno.model.EntityId
import app.cuaderno.model.StrokeTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Matrix as ComposeMatrix

@Composable
actual fun InkPrototypeScreen(services: AppServices?, modifier: Modifier) {
    var useInk by remember { mutableStateOf(true) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Motor de tinta:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
            if (useInk) {
                Button(onClick = {}) { Text("androidx.ink (guarda los trazos)") }
                OutlinedButton(onClick = { useInk = false }, modifier = Modifier.padding(start = 8.dp)) { Text("Solo Compose (no guarda)") }
            } else {
                OutlinedButton(onClick = { useInk = true }) { Text("androidx.ink (guarda los trazos)") }
                Button(onClick = {}, modifier = Modifier.padding(start = 8.dp)) { Text("Solo Compose (no guarda)") }
            }
        }
        if (useInk) InkPageScreen(services, Modifier.weight(1f)) else ComposeInkScreen(Modifier.weight(1f))
    }
}

/** Medidas de la página A4 en el "mundo" de la tinta: décimas de milímetro (docs/arquitectura.md §3.4). */
private const val PAGE_WIDTH_UNITS = 2100f
private const val PAGE_HEIGHT_UNITS = 2970f

private const val ERASER_RADIUS_PX = 28f

/** Un trazo en pantalla. [id] se completa cuando la base de datos lo guardó (los cargados ya lo traen). */
private class DrawnStroke(
    val id: CompletableDeferred<EntityId>,
    val ink: Stroke,
    val tool: StrokeTool = StrokeTool.PEN,
) {
    var visible by mutableStateOf(true)

    /** Puntos del trazo para saber si el borrador lo toca. Se calculan una sola vez. */
    val points: List<InkPoint> by lazy {
        val scratch = StrokeInput()
        List(ink.inputs.size) { i ->
            ink.inputs.populate(i, scratch)
            InkPoint(scratch.x, scratch.y, 1f)
        }
    }

    suspend fun idOrNull(): EntityId? = try {
        id.await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

private enum class ActionKind { DRAW, ERASE }

/** Algo que hizo el usuario y se puede deshacer o rehacer: dibujar o borrar uno o varios trazos. */
private class UserAction(val kind: ActionKind, val strokes: List<DrawnStroke>)

/**
 * Hoja A4 donde escribe el lápiz con `androidx.ink`. Cada trazo se guarda en la base de datos al
 * soltar el lápiz y se vuelve a cargar al abrir la app. Borrar y deshacer usan el borrado suave.
 * Solo el lápiz escribe; el dedo se descarta antes de llegar a la tinta (RF-03).
 */
@Composable
private fun InkPageScreen(services: AppServices?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    val undoStack = remember { mutableStateListOf<UserAction>() }
    val redoStack = remember { mutableStateListOf<UserAction>() }
    var pageId by remember { mutableStateOf<EntityId?>(null) }
    var loaded by remember { mutableStateOf(services == null) }
    var message by remember { mutableStateOf<String?>(null) }
    var eraserMode by remember { mutableStateOf(false) }
    var lastDevice by remember { mutableStateOf("ninguno todavía") }

    var toolSettings by remember { mutableStateOf(ToolSettings()) }
    var viewport by remember { mutableStateOf(PageViewport(1f, 0f, 0f)) }

    val brush = remember(toolSettings.tool, toolSettings.colorArgb, toolSettings.width) {
        brushFor(toolSettings.tool, toolSettings.colorArgb, toolSettings.width)
    }
    val currentBrush = rememberUpdatedState(brush)
    val renderer = remember { CanvasStrokeRenderer.create() }

    // Cada pulsación del botón del lápiz alterna entre lápiz y borrador.
    LaunchedEffect(PenButton.clicks) { if (PenButton.clicks > 0) eraserMode = !eraserMode }

    // Al abrir: encontrar (o crear) la primera página y cargar sus trazos.
    LaunchedEffect(services) {
        if (services == null) return@LaunchedEffect
        try {
            val page = EnsureFirstPage(services.notebooks, services.pages)()
            pageId = page.meta.id
            for (saved in services.ink.observeStrokes(page.meta.id).first()) {
                val ink = saved.toInkStroke() ?: continue
                strokes += DrawnStroke(CompletableDeferred(saved.meta.id), ink, saved.tool)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "No se pudo abrir la página: ${e.message}"
        }
        loaded = true
    }

    fun pushAction(action: UserAction) {
        undoStack += action
        redoStack.clear()
    }

    fun persistVisibility(targets: List<DrawnStroke>, visible: Boolean) {
        val s = services ?: return
        scope.launch {
            try {
                val ids = targets.mapNotNull { it.idOrNull() }
                if (visible) s.ink.restore(ids) else s.ink.erase(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "No se pudo guardar el cambio: ${e.message}"
            }
        }
    }

    fun persistNew(drawn: List<DrawnStroke>) {
        val s = services
        val page = pageId
        scope.launch {
            for (d in drawn) {
                try {
                    val newStroke = d.ink.toNewStroke(d.tool) ?: throw IllegalStateException("trazo vacío")
                    if (s == null || page == null) throw IllegalStateException("la página no está lista")
                    d.id.complete(s.ink.add(page, newStroke).meta.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    d.id.completeExceptionally(e)
                    if (s != null) message = "No se pudo guardar un trazo: ${e.message}"
                }
            }
        }
    }

    fun applyAction(action: UserAction, undo: Boolean) {
        // Deshacer un dibujo lo oculta; rehacerlo lo muestra. Con un borrado, al revés.
        val show = (action.kind == ActionKind.DRAW) != undo
        for (d in action.strokes) d.visible = show
        persistVisibility(action.strokes, show)
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        applyAction(action, undo = true)
        redoStack += action
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        applyAction(action, undo = false)
        undoStack += action
    }

    fun clearPage() {
        val targets = strokes.filter { it.visible }
        if (targets.isEmpty()) return
        for (d in targets) d.visible = false
        pushAction(UserAction(ActionKind.ERASE, targets))
        persistVisibility(targets, false)
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Página 1 · Mi primer cuaderno", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        message != null -> message!!
                        !loaded -> "Abriendo la página…"
                        services == null -> "Sin guardado (no hay base de datos)"
                        else -> "Trazos: ${strokes.count { it.visible }}  ·  Guardados en la base de datos  ·  " +
                            "Último: $lastDevice  ·  Botón del lápiz: ${PenButton.clicks} pulsaciones"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = { undo() }, enabled = undoStack.isNotEmpty(), modifier = Modifier.padding(end = 8.dp)) {
                Text("Deshacer")
            }
            OutlinedButton(onClick = { redo() }, enabled = redoStack.isNotEmpty(), modifier = Modifier.padding(end = 8.dp)) {
                Text("Rehacer")
            }
            OutlinedButton(onClick = { viewport = PageViewport(1f, 0f, 0f) }, enabled = viewport.zoom > 1f, modifier = Modifier.padding(end = 8.dp)) {
                Text("Ajustar (${(viewport.zoom * 100).roundToInt()}%)")
            }
            Button(onClick = { clearPage() }) { Text("Limpiar") }
        }

        InkToolbar(
            settings = toolSettings,
            eraserMode = eraserMode,
            onSettings = { toolSettings = it },
            onEraserMode = { eraserMode = it },
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
                            viewport = viewport.clamped(fitW, fitH, vw, vh).transformed(cx, cy, cx - px, cy - py, zoomFactor, fitW, fitH, vw, vh)
                        }
                    }
                },
            contentAlignment = Alignment.TopStart,
        ) {
            // La hoja A4 entera ajustada al espacio disponible (zoom 1). `scale` son píxeles por unidad del mundo.
            val fitScale = minOf(constraints.maxWidth / PAGE_WIDTH_UNITS, constraints.maxHeight / PAGE_HEIGHT_UNITS)
            val fitWidth = PAGE_WIDTH_UNITS * fitScale
            val fitHeight = PAGE_HEIGHT_UNITS * fitScale
            val vp = viewport.clamped(fitWidth, fitHeight, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
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
                        val gesture = mutableListOf<DrawnStroke>()
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
                                    when (inputActionFor(change.type, eraserMode)) {
                                        InputAction.DRAW -> Unit
                                        InputAction.ERASE -> {
                                            change.consume()
                                            if (change.pressed) {
                                                val s = viewScale
                                                val x = (change.position.x - view.offsetX) / s
                                                val y = (change.position.y - view.offsetY) / s
                                                for (d in strokes) {
                                                    if (d.visible && InkMath.isNear(d.points, x, y, ERASER_RADIUS_PX / s)) {
                                                        d.visible = false
                                                        gesture += d
                                                    }
                                                }
                                            } else if (change.previousPressed && gesture.isNotEmpty()) {
                                                val action = UserAction(ActionKind.ERASE, gesture.toList())
                                                gesture.clear()
                                                pushAction(action)
                                                persistVisibility(action.strokes, false)
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
                        for (d in strokes) if (d.visible) renderer.draw(native, d.ink, worldToScreen)
                        native.restore()
                    }
                }
                // Trazo en curso, con baja latencia. Al terminar, pasa a la lista de arriba y se guarda.
                InProgressStrokes(
                    defaultBrush = if (loaded) brush else null,
                    nextBrush = { currentBrush.value },
                    pointerEventToWorldTransform = screenToWorld,
                    onStrokesFinished = { finished ->
                        val tool = toolSettings.tool
                        val drawn = finished.map { DrawnStroke(CompletableDeferred(), it, tool) }
                        strokes.addAll(drawn)
                        pushAction(UserAction(ActionKind.DRAW, drawn))
                        persistNew(drawn)
                    },
                )
            }
        }
    }
}
