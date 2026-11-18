package app.cuaderno.library

import androidx.compose.runtime.Composable
import app.cuaderno.domain.PlatformFile

/** En la PC todavía no se importan PDF (llega con la v2). */
@Composable
actual fun rememberPdfPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? = null
