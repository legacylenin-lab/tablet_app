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
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }

    /**
     * El botón del lápiz (Lenovo Tab Pen Plus) llega como una tecla desconocida de un dispositivo
     * llamado "...Pen Plus Consumer Control". Cada pulsación se reenvía a [PenButton].
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val fromPen = event.device?.name?.contains("pen", ignoreCase = true) == true
        if (fromPen) {
            if (event.repeatCount == 0) PenButton.click()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
