package app.cuaderno.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import app.cuaderno.domain.BackupService
import app.cuaderno.domain.PlatformFile

/**
 * Selector del sistema para elegir dónde guardar un respaldo nuevo, con un nombre sugerido.
 * Devuelve la función que lo abre, o `null` si la plataforma no lo tiene (la PC, hasta la v2).
 */
@Composable
expect fun rememberBackupDestinationPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)?

/** Pantalla de respaldo (RF-17): guardar todo en un archivo `.cuaderno-backup`. */
@Composable
fun BackupScreen(
    backup: BackupService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = viewModel { BackupViewModel(backup) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickDestination = rememberBackupDestinationPicker(viewModel::onDestinationPicked)

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, enabled = !state.working, modifier = Modifier.padding(end = 12.dp)) {
                Text("◀ Cuadernos")
            }
            Text("Respaldo", style = MaterialTheme.typography.headlineMedium)
        }

        Card(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(top = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Crear un respaldo", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Guarda en un solo archivo todos tus cuadernos (trazos y textos) y los PDF de la biblioteca. " +
                        "Guárdalo fuera de la tablet (por ejemplo, en Google Drive o en tu PC) para no perder nada " +
                        "si la tablet se daña o la cambias.",
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = { pickDestination?.invoke() },
                    enabled = pickDestination != null && !state.working,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(if (state.working) "Creando el respaldo…" else "Crear respaldo")
                }
            }
        }

        state.message?.let { message ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message,
                    color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::onDismissMessage) { Text("Cerrar") }
            }
        }
    }
}
