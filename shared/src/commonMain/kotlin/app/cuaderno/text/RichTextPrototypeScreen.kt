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
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor

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
        RichTextToolbar(state, modifier = Modifier.padding(vertical = 8.dp))
        RichTextEditor(state = state, modifier = Modifier.fillMaxWidth().height(320.dp))
        Text("HTML que se guardaría:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        Text(
            RichTextHtml.normalize(state.toHtml()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        )
    }
}
