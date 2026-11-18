package app.cuaderno

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Los ViewModels creados dentro de [content] viven mientras esta parte de la pantalla esté visible
 * y se descartan al salir (o al cambiar [key]). Así, al cerrar un cuaderno y volver a abrirlo,
 * empieza de cero, sin arrastrar el estado anterior. Cuando llegue Navigation Compose, cada destino
 * tendrá su propio almacén y esto sobrará.
 */
@Composable
fun ScopedViewModels(key: Any, content: @Composable () -> Unit) {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
