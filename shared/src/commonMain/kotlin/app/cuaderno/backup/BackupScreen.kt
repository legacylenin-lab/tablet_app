package app.cuaderno.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.DialogProperties
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

/** Selector del sistema para elegir un respaldo y restaurarlo. */
@Composable
expect fun rememberBackupSourcePicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)?

/** Cierra la app y la vuelve a abrir (para terminar una restauración), o `null` si no se puede. */
@Composable
expect fun rememberAppRestarter(): (() -> Unit)?

/** Fecha y hora legibles: «22/11/2026 a las 09:17». */
expect fun formatDateTime(millis: Long): String

/** Pantalla de respaldo (RF-17): guardar todo en un archivo `.cuaderno-backup` y restaurarlo. */
@Composable
fun BackupScreen(
    backup: BackupService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = viewModel { BackupViewModel(backup) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickDestination = rememberBackupDestinationPicker(viewModel::onDestinationPicked)
    val pickSource = rememberBackupSourcePicker(viewModel::onSourcePicked)
    val restart = rememberAppRestarter()
    val busy = state.working != null

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.padding(end = 12.dp)) {
                Text("◀ Cuadernos")
            }
            Text("Respaldo", style = MaterialTheme.typography.headlineMedium)
        }

        state.working?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
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

        Section("Crear un respaldo") {
            Text(
                "Guarda en un solo archivo todos tus cuadernos (trazos y textos) y los PDF de la biblioteca. " +
                    "Guárdalo fuera de la tablet (por ejemplo, en Google Drive o en tu PC) para no perder nada " +
                    "si la tablet se daña o la cambias. Fíjate arriba en qué carpeta se guarda.",
            )
            Button(onClick = { pickDestination?.invoke() }, enabled = pickDestination != null && !busy, modifier = Modifier.padding(top = 12.dp)) {
                Text("Crear respaldo")
            }
        }

        Section("Restaurar un respaldo") {
            Text(
                "Reemplaza todo lo que hay ahora en la app por lo que tiene un archivo de respaldo. Antes de " +
                    "cambiar nada, se revisa que el archivo esté completo y se guarda una copia de lo que tienes ahora.",
            )
            Button(onClick = { pickSource?.invoke() }, enabled = pickSource != null && !busy, modifier = Modifier.padding(top = 12.dp)) {
                Text("Restaurar desde un archivo…")
            }
        }

        state.previous?.let { previous ->
            Section("Volver a como estaba") {
                Text(
                    "Antes de la última restauración se guardó una copia de tus datos (del ${formatDateTime(previous.createdAtMillis)}, " +
                        "con ${BackupViewModel.pdfCount(previous.documentCount)}). Si te equivocaste de archivo, puedes volver a ella.",
                )
                OutlinedButton(onClick = viewModel::onUndoClick, enabled = !busy, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Volver a como estaba")
                }
            }
        }
    }

    when (val step = state.restore) {
        null -> Unit
        is RestoreStep.Confirm -> AlertDialog(
            onDismissRequest = viewModel::onCancelRestore,
            title = { Text(if (step.undo) "¿Volver a como estaba?" else "¿Restaurar este respaldo?") },
            text = {
                Text(
                    buildString {
                        step.fileName?.let { append("Archivo: «$it»\n") }
                        append("Creado el ${formatDateTime(step.summary.createdAtMillis)}, con ${BackupViewModel.pdfCount(step.summary.documentCount)}.\n\n")
                        append("Todo lo que hay ahora en la app (cuadernos y biblioteca) se reemplazará por lo de ")
                        append(if (step.undo) "esa copia" else "este respaldo")
                        append(". Antes se guardará una copia de lo actual. Al terminar, la app se reiniciará.")
                    },
                )
            },
            confirmButton = { Button(onClick = viewModel::onConfirmRestore) { Text(if (step.undo) "Volver" else "Restaurar") } },
            dismissButton = { TextButton(onClick = viewModel::onCancelRestore) { Text("Cancelar") } },
        )
        RestoreStep.ReadyToRestart -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Todo listo") },
            text = { Text("El respaldo se revisó y está preparado. La app se cerrará y se abrirá de nuevo para terminar de restaurar.") },
            confirmButton = { Button(onClick = { restart?.invoke() }, enabled = restart != null) { Text("Reiniciar ahora") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}
