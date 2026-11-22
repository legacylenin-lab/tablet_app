package app.cuaderno.notebooks

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cuaderno.AppServices
import app.cuaderno.model.Notebook

/**
 * Lista de cuadernos (RF-01): crear, abrir, renombrar y eliminar. Eliminar es un borrado suave
 * (el cuaderno deja de verse pero sus datos siguen guardados). Solo dibuja: lo que pasa al tocar
 * cada botón lo decide [NotebookListViewModel].
 */
@Composable
fun NotebookListScreen(
    services: AppServices,
    onOpen: (Notebook) -> Unit,
    onLibrary: () -> Unit,
    onBackup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotebookListViewModel = viewModel { NotebookListViewModel(services.notebooks) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mis cuadernos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBackup, modifier = Modifier.padding(end = 8.dp)) { Text("Respaldo") }
            OutlinedButton(onClick = onLibrary, modifier = Modifier.padding(end = 8.dp)) { Text("Biblioteca") }
            Button(onClick = viewModel::onCreateClick) { Text("Nuevo cuaderno") }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

        val list = state.notebooks
        when {
            list == null -> Text("Abriendo…", modifier = Modifier.padding(top = 16.dp))
            list.isEmpty() -> Text(
                "Todavía no tienes cuadernos. Toca «Nuevo cuaderno» para crear el primero.",
                modifier = Modifier.padding(top = 16.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.meta.id.toString() }) { notebook ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(notebook) }) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(notebook.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { viewModel.onRenameClick(notebook) }, modifier = Modifier.padding(end = 8.dp)) {
                                Text("Renombrar")
                            }
                            OutlinedButton(onClick = { viewModel.onDeleteClick(notebook) }) { Text("Eliminar") }
                        }
                    }
                }
            }
        }
    }

    when (val d = state.dialog) {
        null -> Unit
        NotebookDialog.Create -> TitleDialog(
            heading = "Nuevo cuaderno",
            confirmLabel = "Crear",
            initial = "",
            onDismiss = viewModel::onDismissDialog,
            onConfirm = viewModel::onConfirmTitle,
        )
        is NotebookDialog.Rename -> TitleDialog(
            heading = "Renombrar cuaderno",
            confirmLabel = "Guardar",
            initial = d.notebook.title,
            onDismiss = viewModel::onDismissDialog,
            onConfirm = viewModel::onConfirmTitle,
        )
        is NotebookDialog.Delete -> AlertDialog(
            onDismissRequest = viewModel::onDismissDialog,
            title = { Text("¿Eliminar «${d.notebook.title}»?") },
            text = { Text("El cuaderno dejará de verse en la lista.") },
            confirmButton = { Button(onClick = viewModel::onConfirmDelete) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = viewModel::onDismissDialog) { Text("Cancelar") } },
        )
    }
}

/** Diálogo con un campo de nombre. Lo que se va escribiendo es estado de la pantalla, no del ViewModel. */
@Composable
private fun TitleDialog(
    heading: String,
    confirmLabel: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val normalized = NotebookTitle.normalize(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Nombre") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { normalized?.let(onConfirm) }, enabled = normalized != null) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
