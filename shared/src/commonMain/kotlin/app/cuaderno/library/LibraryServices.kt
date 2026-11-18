package app.cuaderno.library

import app.cuaderno.domain.DocumentRepository
import app.cuaderno.domain.PdfEngine
import kotlinx.coroutines.CoroutineScope

/**
 * Lo que necesitan la Biblioteca y el lector. En la tablet sale de `AppServices`; en la PC, del
 * prototipo P-1, que todavía no guarda nada (la PC completa llega con la v2).
 */
class LibraryServices(
    val documents: DocumentRepository,
    val pdf: PdfEngine,
    /** Vive tanto como la app: recordar la página no se cancela al salir del lector. */
    val saveScope: CoroutineScope,
)
