package app.cuaderno.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Línea base del P-0: pizarra hecha solo con Compose (sin la librería de baja latencia). Una hoja en blanco donde **solo el lápiz dibuja** (RF-02, RF-03).
 * El dedo no deja marca. El extremo borrador del lápiz borra. En la PC (ratón) no se dibuja.
 *
 * Es un prototipo para probar en la tablet real; todavía no guarda nada.
 */
@Composable
fun ComposeInkScreen(modifier: Modifier = Modifier) {
    val strokes = remember { mutableStateListOf<List<InkPoint>>() }
    val current = remember { mutableStateListOf<InkPoint>() }
    var penPoints by remember { mutableStateOf(0) }
    var fingerEvents by remember { mutableStateOf(0) }
    var eraserEvents by remember { mutableStateOf(0) }
    var otherEvents by remember { mutableStateOf(0) }
    var lastDevice by remember { mutableStateOf("ninguno todavía") }
    var eraserMode by remember { mutableStateOf(false) }
    LaunchedEffect(PenButton.clicks) { if (PenButton.clicks > 0) eraserMode = !eraserMode }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Pizarra de prueba: dibuja con el lápiz", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lápiz: $penPoints puntos  ·  Dedo (no dibuja): $fingerEvents  ·  " +
                        "Borrador: $eraserEvents  ·  Otros: $otherEvents  ·  Último: $lastDevice",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = { eraserMode = !eraserMode }, modifier = Modifier.padding(end = 8.dp)) {
                Text(if (eraserMode) "Modo: BORRADOR" else "Modo: LÁPIZ")
            }
            Button(onClick = {
                strokes.clear()
                current.clear()
            }) { Text("Limpiar") }
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                lastDevice = typeName(change.type)
                                when (inputActionFor(change.type, eraserMode)) {
                                    InputAction.DRAW -> {
                                        if (change.pressed) {
                                            for (h in change.historical) {
                                                current += InkPoint(h.position.x, h.position.y, change.pressure)
                                            }
                                            current += InkPoint(change.position.x, change.position.y, change.pressure)
                                            penPoints += 1 + change.historical.size
                                            change.consume()
                                        } else if (change.previousPressed && current.isNotEmpty()) {
                                            strokes += current.toList()
                                            current.clear()
                                            change.consume()
                                        }
                                    }
                                    InputAction.ERASE -> {
                                        eraserEvents++
                                        if (change.pressed) {
                                            strokes.removeAll { InkMath.isNear(it, change.position.x, change.position.y, ERASER_RADIUS) }
                                            change.consume()
                                        }
                                    }
                                    // El dedo se contará, pero no escribe ni se consume: queda libre para desplazar y hacer zoom.
                                    InputAction.NAVIGATE -> if (change.pressed && !change.previousPressed) fingerEvents++
                                    InputAction.IGNORE -> otherEvents++
                                }
                            }
                        }
                    }
                },
        ) {
            for (stroke in strokes) drawStroke(stroke)
            drawStroke(current)
        }
    }
}

private const val BASE_WIDTH = 5f
private const val ERASER_RADIUS = 28f

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(points: List<InkPoint>) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(Color.Black, radius = InkMath.width(BASE_WIDTH, points[0].pressure) / 2, center = Offset(points[0].x, points[0].y))
        return
    }
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        drawLine(
            color = Color.Black,
            start = Offset(a.x, a.y),
            end = Offset(b.x, b.y),
            strokeWidth = InkMath.width(BASE_WIDTH, (a.pressure + b.pressure) / 2),
            cap = StrokeCap.Round,
        )
    }
}

private fun typeName(type: PointerType): String = when (type) {
    PointerType.Stylus -> "lápiz"
    PointerType.Eraser -> "borrador"
    PointerType.Touch -> "dedo"
    PointerType.Mouse -> "ratón"
    else -> "desconocido"
}
