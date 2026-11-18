package app.cuaderno

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import app.cuaderno.ink.InkPrototypeScreen
import app.cuaderno.library.LibraryScreen
import app.cuaderno.library.ReaderScreen
import app.cuaderno.model.Document
import app.cuaderno.model.Notebook
import app.cuaderno.notebooks.NotebookListScreen

@Composable
@Preview
fun App(services: AppServices? = null) {
    MaterialTheme {
        var open by remember { mutableStateOf<Notebook?>(null) }
        var library by remember { mutableStateOf(false) }
        var reading by remember { mutableStateOf<Document?>(null) }
        val current = open
        val document = reading
        when {
            // Sin base de datos (por ejemplo, la PC por ahora) no hay lista: solo la pantalla de prueba.
            services == null -> InkPrototypeScreen(null)
            document != null -> ScopedViewModels(key = document.meta.id) {
                SystemBackHandler { reading = null }
                ReaderScreen(services, document, onBack = { reading = null })
            }
            library -> {
                SystemBackHandler { library = false }
                LibraryScreen(services, onBack = { library = false }, onOpen = { reading = it })
            }
            current == null -> NotebookListScreen(services, onOpen = { open = it }, onLibrary = { library = true })
            else -> ScopedViewModels(key = current.meta.id) {
                InkPrototypeScreen(services, notebook = current, onBack = { open = null })
            }
        }
    }
}
