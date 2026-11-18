package app.cuaderno.data

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import app.cuaderno.domain.PdfEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Motor de PDF de Android, con el `PdfRenderer` del sistema (docs/arquitectura.md §8). */
class AndroidPdfEngine(
    private val files: FileDocumentStore,
    private val dispatcher: CoroutineDispatcher,
) : PdfEngine {

    override suspend fun pageCount(sha256: String): Int = withContext(dispatcher) {
        ParcelFileDescriptor.open(files.fileFor(sha256), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val renderer = PdfRenderer(fd)
            try {
                renderer.pageCount
            } finally {
                renderer.close()
            }
        }
    }
}
