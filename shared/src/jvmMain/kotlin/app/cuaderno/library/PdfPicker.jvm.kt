package app.cuaderno.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import app.cuaderno.domain.PlatformFile
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Un archivo elegido en Windows: una ruta del disco. */
class JvmPlatformFile(val file: File) : PlatformFile {
    override val name: String get() = file.name
}

/** El diálogo «Abrir» de Windows, filtrado a PDF (FilePicker, docs/arquitectura.md §8). */
@Composable
actual fun rememberPdfPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? {
    val currentOnPicked = rememberUpdatedState(onPicked)
    return {
        val dialog = FileDialog(null as Frame?, "Elegir un PDF", FileDialog.LOAD).apply {
            file = "*.pdf" // filtro del diálogo nativo de Windows
            setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
            isVisible = true // espera a que el usuario elija o cancele
        }
        val name = dialog.file
        val dir = dialog.directory
        if (name != null && dir != null) currentOnPicked.value(JvmPlatformFile(File(dir, name)))
    }
}
