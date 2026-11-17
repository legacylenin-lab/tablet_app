package app.cuaderno

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import app.cuaderno.ink.InkPrototypeScreen
import app.cuaderno.model.Notebook
import app.cuaderno.notebooks.NotebookListScreen
import app.cuaderno.text.RichTextPrototypeScreen

@Composable
@Preview
fun App(services: AppServices? = null) {
    MaterialTheme {
        var open by remember { mutableStateOf<Notebook?>(null) }
        var textTest by remember { mutableStateOf(false) }
        val current = open
        when {
            // Sin base de datos (por ejemplo, la PC por ahora) no hay lista: solo la pantalla de prueba.
            services == null -> InkPrototypeScreen(null)
            textTest -> RichTextPrototypeScreen(onBack = { textTest = false })
            current == null -> NotebookListScreen(services, onOpen = { open = it }, onTextTest = { textTest = true })
            else -> InkPrototypeScreen(services, notebook = current, onBack = { open = null })
        }
    }
}
