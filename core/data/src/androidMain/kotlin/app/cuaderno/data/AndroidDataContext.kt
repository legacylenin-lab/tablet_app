package app.cuaderno.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cuaderno.data.backup.BackupService
import app.cuaderno.data.backup.SqlDriverSnapshotter
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.domain.IdGenerator
import app.cuaderno.model.EntityId
import java.io.File

/**
 * Los datos de la app en el almacenamiento privado de Android: la base de datos
 * (`databases/cuaderno.db`), la carpeta de los PDF (`files/documents/`) y el respaldo de todo ello.
 */
class AndroidStorage(
    context: Context,
    deviceId: EntityId,
    authorId: EntityId,
    ids: IdGenerator,
    appVersion: String,
    databaseName: String = "cuaderno.db",
) {
    private val app = context.applicationContext
    val driver: SqlDriver = AndroidSqliteDriver(CuadernoDatabase.Schema, app, databaseName)
    val data = DataContext(CuadernoDatabase(driver), deviceId, authorId, ids) { System.currentTimeMillis() }
    val documentsDir = File(app.filesDir, "documents")

    /** Respaldo completo (RF-17). La copia temporal de la base de datos se hace en la caché. */
    val backup = BackupService(
        snapshotter = SqlDriverSnapshotter(driver),
        documentsDir = documentsDir,
        workDir = File(app.cacheDir, "backup"),
        deviceId = deviceId,
        appVersion = appVersion,
        schemaVersion = CuadernoDatabase.Schema.version.toInt(),
        nowMillis = { System.currentTimeMillis() },
    )
}
