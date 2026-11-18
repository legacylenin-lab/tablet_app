package app.cuaderno.library

import androidx.compose.runtime.Composable
import app.cuaderno.domain.PlatformFile

/**
 * Selector de PDF del sistema (FilePicker, docs/arquitectura.md §8). Devuelve la función que lo
 * abre, o `null` si la plataforma todavía no lo tiene (la PC, hasta la v2).
 */
@Composable
expect fun rememberPdfPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)?
