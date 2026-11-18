package app.cuaderno

import androidx.compose.runtime.Composable

/** El gesto o botón «atrás» del sistema (PQ-23). En la PC no hace nada. */
@Composable
expect fun SystemBackHandler(onBack: () -> Unit)
