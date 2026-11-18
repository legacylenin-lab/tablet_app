package app.cuaderno

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.cuaderno.data.FileDocumentStore
import app.cuaderno.data.PdfBoxEngine
import app.cuaderno.data.SqlDocumentRepository
import app.cuaderno.data.createInMemoryDataContext
import app.cuaderno.domain.RandomIdGenerator
import app.cuaderno.library.JvmPlatformFile
import app.cuaderno.library.LibraryServices
import app.cuaderno.library.PdfPrototypeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Files

/**
 * App de Windows. Por ahora es el **prototipo P-1**: Biblioteca y lector de PDF con PDFBox, sin
 * guardar nada (base de datos en memoria y copias de los PDF en una carpeta temporal que se borra
 * al cerrar). La app de PC completa llega con la v2.
 */
fun main() {
    val tempDocuments = Files.createTempDirectory("cuaderno-p1-").toFile()
    val ids = RandomIdGenerator()
    val data = createInMemoryDataContext(ids.newId(), ids.newId(), ids)
    val files = FileDocumentStore(tempDocuments, Dispatchers.IO) { (it as JvmPlatformFile).file.inputStream() }
    val pdf = PdfBoxEngine(files, Dispatchers.IO)
    val services = LibraryServices(
        documents = SqlDocumentRepository(data, Dispatchers.IO, files, pdf),
        pdf = pdf,
        saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
    try {
        application {
            Window(onCloseRequest = ::exitApplication, title = "Cuaderno: prototipo P-1 (la PC aún no guarda)") {
                PdfPrototypeApp(services)
            }
        }
    } finally {
        pdf.close()
        tempDocuments.deleteRecursively()
    }
}
