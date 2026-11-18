package app.cuaderno.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cuaderno.AppServices
import app.cuaderno.model.Document

/**
 * Biblioteca (RF-10): la lista de PDF importados y el botón para importar uno nuevo. Tocar un
 * documento lo abre en el lector.
 */
@Composable
fun LibraryScreen(
    services: AppServices,
    onBack: () -> Unit,
    onOpen: (Document) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel { LibraryViewModel(services.documents) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickPdf = rememberPdfPicker(viewModel::onFilePicked)

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(end = 12.dp)) { Text("◀ Cuadernos") }
            Text("Biblioteca", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Button(onClick = { pickPdf?.invoke() }, enabled = pickPdf != null && !state.importing) {
                Text(if (state.importing) "Importando…" else "Importar PDF")
            }
        }
        state.message?.let { message ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::onDismissMessage) { Text("Cerrar") }
            }
        }

        val list = state.documents
        when {
            list == null -> Text("Abriendo…", modifier = Modifier.padding(top = 16.dp))
            list.isEmpty() -> Text(
                "Todavía no tienes documentos. Toca «Importar PDF» para agregar el primero.",
                modifier = Modifier.padding(top = 16.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.meta.id.toString() }) { document ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(document) }) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(document.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${LibraryViewModel.pagesLabel(document.pageCount)} · ${LibraryViewModel.sizeLabel(document.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            OutlinedButton(onClick = { viewModel.onDeleteClick(document) }) { Text("Eliminar") }
                        }
                    }
                }
            }
        }
    }

    state.confirmDelete?.let { document ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissDelete,
            title = { Text("¿Eliminar «${document.title}»?") },
            text = { Text("El documento dejará de verse en la biblioteca. Tu archivo original no se toca.") },
            confirmButton = { Button(onClick = viewModel::onConfirmDelete) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = viewModel::onDismissDelete) { Text("Cancelar") } },
        )
    }
}
