package app.cuaderno

import app.cuaderno.domain.DocumentRepository
import app.cuaderno.domain.InkRepository
import app.cuaderno.domain.NotebookRepository
import app.cuaderno.domain.PageRepository
import app.cuaderno.domain.TextBlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Lo que la interfaz necesita para guardar y leer datos. Cada plataforma lo arma (`AppContainer`)
 * y se lo entrega a `App`. Con `null`, la pantalla funciona sin guardar nada (por ejemplo en la PC,
 * que todavía no guarda: eso llega con la v2).
 *
 * [saveScope] vive tanto como la app: los guardados que se lanzan desde ahí no se cancelan si el
 * usuario cambia de página o sale del cuaderno justo después de escribir.
 */
class AppServices(
    val notebooks: NotebookRepository,
    val pages: PageRepository,
    val ink: InkRepository,
    val text: TextBlockRepository,
    val documents: DocumentRepository,
    val saveScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
)
