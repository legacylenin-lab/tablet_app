package app.cuaderno.ink

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cuaderno.AppServices
import app.cuaderno.model.Notebook

/** En la PC no se dibuja (RF-21): el ratón se ignora, y esta pantalla solo sirve para ver que la app arranca. */
@Composable
actual fun InkPrototypeScreen(services: AppServices?, notebook: Notebook?, onBack: (() -> Unit)?, modifier: Modifier) {
    ComposeInkScreen(modifier)
}
