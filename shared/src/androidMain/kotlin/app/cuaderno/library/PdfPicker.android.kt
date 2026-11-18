package app.cuaderno.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.cuaderno.domain.PlatformFile
import java.io.IOException
import java.io.InputStream

/** Un archivo elegido en el selector de Android (un `content://`). */
class AndroidPlatformFile(val uri: Uri, override val name: String) : PlatformFile {
    /** Abre el archivo para leerlo. El original no se modifica. */
    fun open(context: Context): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el archivo")
}

/** Usa el selector de archivos del sistema (Storage Access Framework) y solo deja elegir PDF. */
@Composable
actual fun rememberPdfPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val currentOnPicked = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) currentOnPicked.value(AndroidPlatformFile(uri, displayName(context, uri)))
    }
    return { launcher.launch(arrayOf("application/pdf")) }
}

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: "Documento.pdf"
