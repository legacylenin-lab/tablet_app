package app.cuaderno.text

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor

private val TITLE_SIZE = TextUnit(2f, TextUnitType.Em)

/**
 * Prueba de la librería de texto enriquecido (RF-04, etapa 4): negrita, cursiva, subrayado, título y listas,
 * y abajo el HTML que se guardaría. Provisional: se reemplaza al poner los bloques de texto en la página.
 */
@Composable
fun RichTextPrototypeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberRichTextState()
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("← Cuadernos") }
            Text("Prueba de texto", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp))
        }
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            val gap = Modifier.padding(end = 8.dp)
            OutlinedButton(onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }, modifier = gap) { Text("Negrita") }
            OutlinedButton(onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }, modifier = gap) { Text("Cursiva") }
            OutlinedButton(onClick = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }, modifier = gap) { Text("Subrayado") }
            OutlinedButton(onClick = { state.toggleTitle() }, modifier = gap) { Text("Título") }
            OutlinedButton(onClick = { state.toggleUnorderedList() }, modifier = gap) { Text("Lista") }
            OutlinedButton(onClick = { state.toggleOrderedList() }) { Text("Lista numerada") }
        }
        RichTextEditor(state = state, modifier = Modifier.fillMaxWidth().height(320.dp))
        Text("HTML que se guardaría:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        Text(
            RichTextHtml.normalize(state.toHtml()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        )
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
