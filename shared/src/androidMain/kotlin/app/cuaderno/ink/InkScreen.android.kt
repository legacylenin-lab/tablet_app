package app.cuaderno.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput

@Composable
actual fun InkPrototypeScreen(modifier: Modifier) {
    var useInk by remember { mutableStateOf(true) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Motor de tinta:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
            if (useInk) {
                Button(onClick = {}) { Text("androidx.ink (baja latencia)") }
                OutlinedButton(onClick = { useInk = false }, modifier = Modifier.padding(start = 8.dp)) { Text("Solo Compose") }
            } else {
                OutlinedButton(onClick = { useInk = true }) { Text("androidx.ink (baja latencia)") }
                Button(onClick = {}, modifier = Modifier.padding(start = 8.dp)) { Text("Solo Compose") }
            }
        }
        if (useInk) InkLowLatencyScreen(Modifier.weight(1f)) else ComposeInkScreen(Modifier.weight(1f))
    }
}

private const val BRUSH_SIZE = 3f
private const val BRUSH_EPSILON = 0.1f
private const val ERASER_RADIUS = 28f

/**
 * Pizarra con `androidx.ink`: el trazo en curso se dibuja directo en pantalla con predicción de
 * movimiento (baja latencia). Solo el lápiz escribe; el dedo se consume antes de llegar a la tinta (RF-03).
 * El modo borrador (botón en pantalla o botón del lápiz) borra los trazos que toque.
 */
@Composable
private fun InkLowLatencyScreen(modifier: Modifier = Modifier) {
    val finished = remember { mutableStateListOf<Stroke>() }
    val brush = remember {
        Brush.createWithColorIntArgb(
            family = StockBrushes.pressurePen(),
            colorIntArgb = 0xFF000000.toInt(),
            size = BRUSH_SIZE,
            epsilon = BRUSH_EPSILON,
        )
    }
    val renderer = remember { CanvasStrokeRenderer.create() }
    val identity = remember { android.graphics.Matrix() }

    var penDowns by remember { mutableStateOf(0) }
    var fingerDowns by remember { mutableStateOf(0) }
    var eraseEvents by remember { mutableStateOf(0) }
    var lastDevice by remember { mutableStateOf("ninguno todavía") }
    var eraserMode by remember { mutableStateOf(false) }
    // Cada pulsación del botón del lápiz alterna entre lápiz y borrador.
    LaunchedEffect(PenButton.clicks) { if (PenButton.clicks > 0) eraserMode = !eraserMode }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Pizarra con androidx.ink: dibuja con el lápiz", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Trazos: ${finished.size}  ·  Lápiz apoyado: $penDowns veces  ·  Dedo (no dibuja): $fingerDowns  ·  " +
                        "Borrando: $eraseEvents  ·  Último: $lastDevice  ·  Botón del lápiz: ${PenButton.clicks} pulsaciones",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = { eraserMode = !eraserMode }, modifier = Modifier.padding(end = 8.dp)) {
                Text(if (eraserMode) "Modo: BORRADOR" else "Modo: LÁPIZ")
            }
            Button(onClick = { finished.clear() }) { Text("Limpiar") }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                // Se ejecuta ANTES (pasada Initial) que la tinta: lo que se consume aquí, la tinta no lo dibuja.
                .pointerInput(Unit) {
                    val scratch = StrokeInput()
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
                                val newlyDown = change.pressed && !change.previousPressed
                                when (inputActionFor(change.type, eraserMode)) {
                                    InputAction.DRAW -> if (newlyDown) penDowns++
                                    InputAction.ERASE -> {
                                        change.consume()
                                        if (change.pressed) {
                                            eraseEvents++
                                            finished.removeAll { stroke ->
                                                val points = List(stroke.inputs.size) { i ->
                                                    stroke.inputs.populate(i, scratch)
                                                    InkPoint(scratch.x, scratch.y, 1f)
                                                }
                                                InkMath.isNear(points, change.position.x, change.position.y, ERASER_RADIUS)
                                            }
                                        }
                                    }
                                    InputAction.NAVIGATE -> {
                                        change.consume()
                                        if (newlyDown) fingerDowns++
                                    }
                                    InputAction.IGNORE -> change.consume()
                                }
                            }
                        }
                    }
                },
        ) {
            // Trazos terminados.
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    for (stroke in finished) renderer.draw(canvas.nativeCanvas, stroke, identity)
                }
            }
            // Trazo en curso, con baja latencia. Al terminar, pasa a la lista de arriba.
            InProgressStrokes(
                defaultBrush = brush,
                onStrokesFinished = { strokes -> finished.addAll(strokes) },
            )
        }
    }
}
