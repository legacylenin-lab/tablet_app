package app.cuaderno.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
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

/**
 * Un cuadro de texto sobre la hoja (RF-05). [scale] son píxeles de pantalla por unidad de la página
 * (décimas de mm) y ([offsetX], [offsetY]) la esquina de la hoja en la pantalla. Solo se puede escribir
 * en él si [editing]; si no, se ve como texto sobre la hoja. [onHeight] informa su alto, en unidades de
 * la página, para saber si un toque cae dentro.
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
) {
    Box(
        Modifier
            .offset { IntOffset((offsetX + block.x * scale).roundToInt(), (offsetY + block.y * scale).roundToInt()) }
            .onSizeChanged { onHeight(it.height / scale) },
    ) {
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
}
