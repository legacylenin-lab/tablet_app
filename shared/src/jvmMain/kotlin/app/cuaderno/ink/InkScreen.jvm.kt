package app.cuaderno.ink

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** En la PC no se dibuja (RF-21): el ratón se ignora, y esta pantalla solo sirve para ver que la app arranca. */
@Composable
actual fun InkPrototypeScreen(modifier: Modifier) {
    ComposeInkScreen(modifier)
}
