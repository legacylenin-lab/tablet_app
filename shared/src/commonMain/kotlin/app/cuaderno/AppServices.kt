package app.cuaderno

import app.cuaderno.domain.InkRepository
import app.cuaderno.domain.NotebookRepository
import app.cuaderno.domain.PageRepository

/**
 * Lo que la interfaz necesita para guardar y leer datos. Cada plataforma lo arma (`AppContainer`)
 * y se lo entrega a `App`. Con `null`, la pantalla funciona sin guardar nada (por ejemplo en la PC,
 * que todavía no guarda: eso llega con la v2).
 */
class AppServices(
    val notebooks: NotebookRepository,
    val pages: PageRepository,
    val ink: InkRepository,
)
