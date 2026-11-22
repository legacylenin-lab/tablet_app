package app.cuaderno.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.cuaderno.domain.PlatformFile
import app.cuaderno.library.AndroidPlatformFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** «Guardar como» del sistema (Storage Access Framework), con el nombre `cuaderno-respaldo-AAAA-MM-DD.cuaderno-backup`. */
@Composable
actual fun rememberBackupDestinationPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val currentOnPicked = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) currentOnPicked.value(AndroidPlatformFile(uri, displayName(context, uri)))
    }
    return {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        launcher.launch("cuaderno-respaldo-$today.cuaderno-backup")
    }
}

/** «Abrir» del sistema para elegir un respaldo. Android no conoce el tipo `.cuaderno-backup`: se muestran todos. */
@Composable
actual fun rememberBackupSourcePicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val currentOnPicked = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) currentOnPicked.value(AndroidPlatformFile(uri, displayName(context, uri)))
    }
    return { launcher.launch(arrayOf("*/*")) }
}

/**
 * Cierra la app y la abre de nuevo: al arrancar, `AppContainer` termina la restauración con la
 * base de datos todavía cerrada.
 */
@Composable
actual fun rememberAppRestarter(): (() -> Unit)? {
    val context = LocalContext.current
    return {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
        Runtime.getRuntime().exit(0)
    }
}

actual fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm", Locale.forLanguageTag("es")).format(Date(millis))

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: "respaldo.cuaderno-backup"
