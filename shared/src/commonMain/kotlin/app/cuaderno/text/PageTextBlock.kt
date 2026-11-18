package app.cuaderno.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlin.math.roundToInt

/**
 * Dentro de un cuadro de texto, 1 dp mide un cuarto de milímetro de la hoja (2,5 unidades de la página).
 * Así el texto crece y se achica con el zoom igual que la tinta, y el cursor queda fino.
 */
private const val UNITS_PER_DP = 2.5f

/** Tamaño de la letra: 16 cuartos de milímetro = 4 mm (más o menos la letra de 11 puntos). */
private val TEXT_STYLE = TextStyle(fontSize = 16.sp, color = Color.Black)

/** Alto mínimo del cuadro mientras se edita: 4 cm. */
private const val EDITING_MIN_HEIGHT_UNITS = 400f

private val EDITING_BACKGROUND = Color(0x141A56DB)
private val EDITING_BORDER = Color(0xFF1A56DB)

/** Alto de la barra para mover el cuadro, encima de él. Medida de pantalla: no cambia con el zoom. */
val TEXT_MOVE_BAR_HEIGHT: Dp = 32.dp

/** Ancho de la franja para cambiar el ancho del cuadro, a su derecha. Medida de pantalla. */
val TEXT_RESIZE_HANDLE_WIDTH: Dp = 28.dp

/**
 * Un cuadro de texto sobre la hoja (RF-05). [scale] son píxeles de pantalla por unidad de la página
 * (décimas de mm) y ([offsetX], [offsetY]) la esquina de la hoja en la pantalla. Solo se puede escribir
 * en él si [editing]; entonces muestra arriba una barra para moverlo y a la derecha una franja para
 * cambiar su ancho. [onGeometry] informa la posición y el ancho mientras se arrastra (unidades de la
 * página) y [onGeometryDone], que se soltó. [onHeight] informa el alto del texto, en unidades de la página.
 */
@Composable
fun PageTextBlock(
    block: TextBlockUi,
    state: RichTextState,
    editing: Boolean,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    focusRequester: FocusRequester,
    onHeight: (Float) -> Unit,
    onGeometry: (x: Int, y: Int, width: Int) -> Unit = { _, _, _ -> },
    onGeometryDone: () -> Unit = {},
) {
    val barPx = with(LocalDensity.current) { TEXT_MOVE_BAR_HEIGHT.toPx() }
    val currentBlock by rememberUpdatedState(block)
    val currentScale by rememberUpdatedState(scale)

    /** Arrastre de un asa: [apply] recibe el cuadro como estaba al empezar y cuánto se movió el dedo (en unidades). */
    fun Modifier.dragHandle(apply: (start: TextBlockUi, dx: Float, dy: Float) -> Unit) = pointerInput(block.id) {
        var start = currentBlock
        var total = Offset.Zero
        detectDragGestures(
            onDragStart = {
                start = currentBlock
                total = Offset.Zero
            },
            onDrag = { change, delta ->
                change.consume()
                total += delta
                apply(start, total.x / currentScale, total.y / currentScale)
            },
            onDragEnd = { onGeometryDone() },
            onDragCancel = { onGeometryDone() },
        )
    }

    Column(
        Modifier.offset {
            val top = offsetY + block.y * scale - if (editing) barPx else 0f
            IntOffset((offsetX + block.x * scale).roundToInt(), top.roundToInt())
        },
    ) {
        if (editing) {
            Box(
                Modifier
                    .height(TEXT_MOVE_BAR_HEIGHT)
                    .width(with(LocalDensity.current) { (block.width * scale).toDp() } + TEXT_RESIZE_HANDLE_WIDTH)
                    .background(EDITING_BORDER)
                    .dragHandle { start, dx, dy ->
                        onGeometry((start.x + dx).roundToInt(), (start.y + dy).roundToInt(), start.width)
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("  ✥  Arrastra aquí para mover", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.onSizeChanged { onHeight(it.height / scale) }) {
                CompositionLocalProvider(LocalDensity provides Density(density = scale * UNITS_PER_DP, fontScale = 1f)) {
                    val look = if (editing) {
                        // Más alto mientras se edita: así es fácil escribir dentro con el lápiz (Android lo pasa a letras).
                        Modifier.heightIn(min = (EDITING_MIN_HEIGHT_UNITS / UNITS_PER_DP).dp)
                            .background(EDITING_BACKGROUND).border(1.dp, EDITING_BORDER).focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                    BasicRichTextEditor(
                        state = state,
                        modifier = Modifier.width((block.width / UNITS_PER_DP).dp).then(look),
                        readOnly = !editing,
                        textStyle = TEXT_STYLE,
                    )
                }
            }
            if (editing) {
                Box(
                    Modifier
                        .width(TEXT_RESIZE_HANDLE_WIDTH)
                        .fillMaxHeight()
                        .background(EDITING_BORDER)
                        .dragHandle { start, dx, _ ->
                            onGeometry(start.x, start.y, (start.width + dx).roundToInt())
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↔", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
