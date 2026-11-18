package app.cuaderno.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.cuaderno.domain.RenderedPage
import java.awt.image.BufferedImage

actual fun RenderedPage.toImageBitmap(): ImageBitmap =
    BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        .also { it.setRGB(0, 0, width, height, pixels, 0, width) }
        .toComposeImageBitmap()
