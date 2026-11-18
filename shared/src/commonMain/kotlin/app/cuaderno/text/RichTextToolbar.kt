package app.cuaderno.text

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState

private val TITLE_SIZE = TextUnit(2f, TextUnitType.Em)

/** Botones de formato del texto enriquecido (RF-04). [trailing] agrega botones al final de la fila. */
@Composable
fun RichTextToolbar(state: RichTextState, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val gap = Modifier.padding(end = 8.dp)
        OutlinedButton(onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }, modifier = gap) { Text("Negrita") }
        OutlinedButton(onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }, modifier = gap) { Text("Cursiva") }
        OutlinedButton(onClick = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }, modifier = gap) { Text("Subrayado") }
        OutlinedButton(onClick = { state.toggleTitle() }, modifier = gap) { Text("Título") }
        OutlinedButton(onClick = { state.toggleUnorderedList() }, modifier = gap) { Text("Lista") }
        OutlinedButton(onClick = { state.toggleOrderedList() }, modifier = gap) { Text("Lista numerada") }
        trailing()
    }
}

/** Convierte en título el párrafo donde está el cursor (o lo vuelve normal si ya lo era). */
private fun RichTextState.toggleTitle() {
    val text = annotatedString.text
    val from = selection.min.coerceIn(0, text.length)
    val to = selection.max.coerceIn(0, text.length)
    val start = text.lastIndexOf('\n', (from - 1).coerceAtLeast(0)).let { if (from == 0) 0 else it + 1 }
    val end = text.indexOf('\n', to).let { if (it < 0) text.length else it }
    val keep = selection
    selection = TextRange(start, end)
    toggleSpanStyle(SpanStyle(fontSize = TITLE_SIZE, fontWeight = FontWeight.Bold))
    selection = keep
}
