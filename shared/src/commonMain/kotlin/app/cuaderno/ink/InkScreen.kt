package app.cuaderno.ink

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Pantalla del prototipo de tinta (P-0). En Android usa la librería de baja latencia `androidx.ink`
 * (con la versión de solo Compose para comparar); en la PC no se dibuja (RF-21).
 */
@Composable
expect fun InkPrototypeScreen(modifier: Modifier = Modifier)
