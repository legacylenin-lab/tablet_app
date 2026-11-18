package app.cuaderno.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cuaderno.AppServices
import app.cuaderno.domain.RenderedPage
import app.cuaderno.model.Document

/** Convierte la imagen del motor de PDF en una que Compose sabe dibujar. */
expect fun RenderedPage.toImageBitmap(): ImageBitmap

/**
 * Lector de PDF (RF-12): una página a la vez, a lo ancho de la pantalla, con flechas y «Página X
 * de N». Abre donde el usuario se quedó. Si la página es más alta que la pantalla, se desplaza.
 */
@Composable
fun ReaderScreen(
    services: AppServices,
    document: Document,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = viewModel(key = document.meta.id.toString()) {
        ReaderViewModel(document, services.pdf, services.documents, services.saveScope)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(end = 12.dp)) { Text("◀ Biblioteca") }
            Column(modifier = Modifier.weight(1f)) {
                Text(state.document.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.message ?: state.nav.label, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = viewModel::onPrevious, enabled = state.nav.canGoBack, modifier = Modifier.padding(end = 8.dp)) {
                Text("◀")
            }
            OutlinedButton(onClick = viewModel::onNext, enabled = state.nav.canGoForward) { Text("▶") }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            LaunchedEffect(widthPx) { viewModel.onWidth(widthPx) }

            val image = state.image
            val bitmap = remember(image) { image?.toImageBitmap() }
            // La hoja vuelve arriba al cambiar de página.
            val scroll = remember(state.imageIndex) { ScrollState(0) }
            if (bitmap == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (state.message != null) "" else "Abriendo la página…")
                }
            } else {
                Image(
                    bitmap = bitmap,
                    contentDescription = state.nav.label,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().verticalScroll(scroll),
                )
            }
        }
    }
}
