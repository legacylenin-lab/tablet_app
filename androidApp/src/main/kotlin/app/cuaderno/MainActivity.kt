package app.cuaderno

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.cuaderno.ink.PenButton

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(container.services)
        }
    }

    /**
     * El botón del lápiz (Lenovo Tab Pen Plus) llega como una tecla desconocida de un dispositivo
     * llamado "...Pen Plus Consumer Control". Cada pulsación se reenvía a [PenButton].
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = handlePenKey(keyCode, event, isDown = true) ?: super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = handlePenKey(keyCode, event, isDown = false) ?: super.onKeyUp(keyCode, event)

    /** `null` si la tecla no viene del lápiz (la atiende Android); `true` si ya la atendimos. */
    private fun handlePenKey(keyCode: Int, event: KeyEvent, isDown: Boolean): Boolean? {
        val fromPen = event.device?.name?.contains("pen", ignoreCase = true) == true
        if (!fromPen) return null
        if (PenButton.isPress(keyCode, isDown, event.repeatCount)) PenButton.click()
        return true
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
