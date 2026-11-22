package app.cuaderno.backup

import androidx.compose.runtime.Composable
import app.cuaderno.domain.PlatformFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** En la PC todavía no hay respaldo (la PC no guarda datos hasta la v2). */
@Composable
actual fun rememberBackupDestinationPicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? = null

@Composable
actual fun rememberBackupSourcePicker(onPicked: (PlatformFile) -> Unit): (() -> Unit)? = null

@Composable
actual fun rememberAppRestarter(): (() -> Unit)? = null

actual fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm", Locale.forLanguageTag("es")).format(Date(millis))
