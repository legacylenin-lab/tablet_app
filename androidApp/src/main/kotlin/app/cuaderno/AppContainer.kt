package app.cuaderno

import android.content.Context
import app.cuaderno.data.SqlInkRepository
import app.cuaderno.data.SqlNotebookRepository
import app.cuaderno.data.SqlPageRepository
import app.cuaderno.data.SqlTextBlockRepository
import app.cuaderno.data.createAndroidDataContext
import app.cuaderno.domain.RandomIdGenerator
import app.cuaderno.model.EntityId
import kotlinx.coroutines.Dispatchers

/**
 * Arma los repositorios de la tablet (inyección de dependencias manual, docs/arquitectura.md §2).
 * El identificador del dispositivo y el del autor se crean la primera vez y se guardan para siempre.
 */
class AppContainer(context: Context) {
    val services: AppServices

    init {
        val prefs = context.getSharedPreferences("cuaderno", Context.MODE_PRIVATE)
        val ids = RandomIdGenerator()

        fun stableId(key: String): EntityId {
            val saved = prefs.getString(key, null)?.let { runCatching { EntityId.parse(it) }.getOrNull() }
            if (saved != null) return saved
            val created = ids.newId()
            prefs.edit().putString(key, created.value).apply()
            return created
        }

        val data = createAndroidDataContext(context, stableId("device_id"), stableId("author_id"), ids)
        services = AppServices(
            notebooks = SqlNotebookRepository(data, Dispatchers.IO),
            pages = SqlPageRepository(data, Dispatchers.IO),
            ink = SqlInkRepository(data, Dispatchers.IO),
            text = SqlTextBlockRepository(data, Dispatchers.IO),
        )
    }
}
