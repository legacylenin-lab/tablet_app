package app.cuaderno.backup

import androidx.compose.runtime.Composable
import app.cuaderno.domain.PlatformFile

/** En la PC todavía no hay respaldo (la PC no guarda datos hasta la v2). */
@Composable
actual fun rememberBackupDestinationPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? = null
