package app.cuaderno.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.cuaderno.ScopedViewModels
import app.cuaderno.model.Document

/**
 * Prototipo P-1 para la PC: la Biblioteca y el lector, sin cuadernos. Lo que se importa vive solo
 * mientras la ventana está abierta; guardar en la PC llega con la v2.
 */
@Composable
fun PdfPrototypeApp(services: LibraryServices) {
    MaterialTheme {
        var reading by remember { mutableStateOf<Document?>(null) }
        val document = reading
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (document != null) {
                ScopedViewModels(key = document.meta.id) {
                    ReaderScreen(services, document, onBack = { reading = null })
                }
            } else {
                LibraryScreen(services, onBack = null, onOpen = { reading = it })
            }
        }
    }
}
