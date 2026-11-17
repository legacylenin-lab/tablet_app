package app.cuaderno.ink

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cuaderno.model.StrokeTool

/**
 * Barra de herramientas del lienzo (RF-02): lápiz o resaltador, sus colores y sus grosores.
 * Cada herramienta recuerda su propio color y grosor (ver [ToolSettings]).
 */
@Composable
internal fun InkToolbar(
    settings: ToolSettings,
    eraserMode: Boolean,
    onSettings: (ToolSettings) -> Unit,
    onEraserMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton("Lápiz", selected = !eraserMode && settings.tool == StrokeTool.PEN) {
            onEraserMode(false)
            onSettings(settings.withTool(StrokeTool.PEN))
        }
        Spacer(Modifier.width(8.dp))
        ToolButton("Resaltador", selected = !eraserMode && settings.tool == StrokeTool.HIGHLIGHTER) {
            onEraserMode(false)
            onSettings(settings.withTool(StrokeTool.HIGHLIGHTER))
        }
        Spacer(Modifier.width(8.dp))
        ToolButton("Borrador", selected = eraserMode) { onEraserMode(true) }

        Spacer(Modifier.width(20.dp))
        for (color in InkTools.colorsFor(settings.tool)) {
            ColorDot(color, selected = color.argb == settings.colorArgb) { onSettings(settings.withColor(color.argb)) }
            Spacer(Modifier.width(8.dp))
        }

        Spacer(Modifier.width(12.dp))
        for (units in InkTools.widthsFor(settings.tool)) {
            WidthChip(units, settings.colorArgb, selected = units == settings.width) { onSettings(settings.withWidth(units)) }
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun ToolButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) } else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun ColorDot(color: InkColor, selected: Boolean, onClick: () -> Unit) {
    // Los colores del resaltador son transparentes: se dibujan sobre un fondo blanco para verlos bien.
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White)
            .background(Color(color.argb))
            .border(
                if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Gray),
                CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/** Muestra el grosor como una raya con ese grosor (1 unidad = 0,1 mm ≈ 0,4 dp en una tablet; se limita para que se vea). */
@Composable
private fun WidthChip(units: Float, argb: Int, selected: Boolean, onClick: () -> Unit) {
    val lineHeight = (units * 0.6f).coerceIn(2f, 26f).dp
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 36.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color.White)
            .border(
                if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Gray),
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(lineHeight).background(Color(argb)))
    }
}
