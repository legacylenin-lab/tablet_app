package app.cuaderno.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.domain.IdGenerator
import app.cuaderno.model.EntityId

/**
 * Abre (o crea) la base de datos de la app en el almacenamiento privado de Android y arma el
 * [DataContext] que comparten los repositorios. El archivo es `databases/cuaderno.db`.
 */
fun createAndroidDataContext(
    context: Context,
    deviceId: EntityId,
    authorId: EntityId,
    ids: IdGenerator,
    databaseName: String = "cuaderno.db",
): DataContext {
    val driver = AndroidSqliteDriver(CuadernoDatabase.Schema, context.applicationContext, databaseName)
    return DataContext(CuadernoDatabase(driver), deviceId, authorId, ids) { System.currentTimeMillis() }
}
