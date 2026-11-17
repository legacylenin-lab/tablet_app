package app.cuaderno

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.cuaderno.ink.InkPrototypeScreen

@Composable
@Preview
fun App(services: AppServices? = null) {
    MaterialTheme {
        // Provisional (etapa 3): prototipo de tinta P-0. La interfaz real llega en las etapas 3 y 4.
        InkPrototypeScreen(services)
    }
}
