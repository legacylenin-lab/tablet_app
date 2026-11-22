package app.cuaderno.backup

import android.content.Context
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

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: "respaldo.cuaderno-backup"
