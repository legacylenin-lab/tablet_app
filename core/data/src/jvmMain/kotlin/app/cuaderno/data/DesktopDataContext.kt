package app.cuaderno.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.domain.IdGenerator
import app.cuaderno.model.EntityId

/**
 * Base de datos **en memoria** para el prototipo de la PC (P-1): se pierde al cerrar la ventana.
 * La base de datos real de Windows (`%APPDATA%\CuadernoApp`) llega con la v2.
 */
fun createInMemoryDataContext(deviceId: EntityId, authorId: EntityId, ids: IdGenerator): DataContext {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CuadernoDatabase.Schema.create(it) }
    return DataContext(CuadernoDatabase(driver), deviceId, authorId, ids) { System.currentTimeMillis() }
}
