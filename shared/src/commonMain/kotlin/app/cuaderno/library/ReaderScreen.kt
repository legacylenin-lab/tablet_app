package app.cuaderno.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cuaderno.AppServices
import app.cuaderno.domain.RenderedPage
import app.cuaderno.model.Document

/** Convierte la imagen del motor de PDF en una que Compose sabe dibujar. */
expect fun RenderedPage.toImageBitmap(): ImageBitmap

/**
 * Lector de PDF (RF-12): una página a la vez, a lo ancho de la pantalla, con flechas, «Ir a…», zoom y «Página X
 * de N». Abre donde el usuario se quedó. Un dedo desplaza la hoja y dos dedos la amplían.
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
    val total = state.document.pageCount
    // Mientras se arrastra la barra solo cambia el número; la página se dibuja al soltar.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val draggingPage = dragging?.let { ReaderViewModel.sliderPage(it, total) }

    if (state.goToDialog) {
        GoToPageDialog(total, onDismiss = viewModel::onDismissGoTo, onConfirm = viewModel::onConfirmGoTo)
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(end = 12.dp)) { Text("◀ Biblioteca") }
            Column(modifier = Modifier.weight(1f)) {
                Text(state.document.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    state.message ?: draggingPage?.let { "Ir a la página $it de $total" } ?: state.nav.label,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = viewModel::onFit, enabled = state.viewport.zoom > 1f, modifier = Modifier.padding(end = 8.dp)) {
                Text("Ajustar (${(state.viewport.zoom * 100).roundToInt()}%)")
            }
            OutlinedButton(onClick = viewModel::onGoToClick, enabled = total > 1, modifier = Modifier.padding(end = 8.dp)) {
                Text("Ir a…")
            }
            OutlinedButton(onClick = viewModel::onPrevious, enabled = state.nav.canGoBack, modifier = Modifier.padding(end = 8.dp)) {
                Text("◀")
            }
            OutlinedButton(onClick = viewModel::onNext, enabled = state.nav.canGoForward) { Text("▶") }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            val viewW = constraints.maxWidth
            val viewH = constraints.maxHeight
            LaunchedEffect(viewW, viewH) { viewModel.onSize(viewW, viewH) }

            val image = state.image
            val bitmap = remember(image) { image?.toImageBitmap() }
            val detail = state.detail
            val detailBitmap = remember(detail) { detail?.image?.toImageBitmap() }
            if (image == null || bitmap == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (state.message != null) "" else "Abriendo la página…")
                }
            } else {
                val fitW = image.width.toFloat()
                val fitH = image.height.toFloat()
                Canvas(
                    modifier = Modifier.fillMaxSize().pointerInput(viewW, viewH) {
                        // Un dedo desplaza; dos dedos amplían alrededor del punto entre ellos (como en los cuadernos).
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val s = viewModel.state.value
                            val page = s.image ?: return@detectTransformGestures
                            val w = page.width.toFloat()
                            val h = page.height.toFloat()
                            val vw = viewW.toFloat()
                            val vh = viewH.toFloat()
                            viewModel.onViewport(
                                s.viewport.clamped(w, h, vw, vh).transformed(centroid.x, centroid.y, pan.x, pan.y, zoom, w, h, vw, vh),
                            )
                        }
                    },
                ) {
                    val vp = state.viewport.clamped(fitW, fitH, size.width, size.height)
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset(vp.offsetX.roundToInt(), vp.offsetY.roundToInt()),
                        dstSize = IntSize((fitW * vp.zoom).roundToInt(), (fitH * vp.zoom).roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                    // La parte nítida se dibujó para otro zoom o posición: se acomoda a la actual.
                    if (detail != null && detailBitmap != null) {
                        val k = vp.zoom / detail.zoom
                        drawImage(
                            detailBitmap,
                            dstOffset = IntOffset((vp.offsetX + detail.left * k).roundToInt(), (vp.offsetY + detail.top * k).roundToInt()),
                            dstSize = IntSize((detail.image.width * k).roundToInt(), (detail.image.height * k).roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                }
            }
        }

        if (total > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("1", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = dragging ?: (state.index + 1).toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        draggingPage?.let(viewModel::onGoToPage)
                        dragging = null
                    },
                    valueRange = 1f..total.toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text("$total", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Ventanita para escribir el número de página. «Ir» solo se activa con un número que existe. */
@Composable
private fun GoToPageDialog(total: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = ReaderViewModel.parsePageNumber(text, total) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ir a la página") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(6) },
                label = { Text("Número (1 a $total)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onConfirm(text) }),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(text) }, enabled = valid) { Text("Ir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
