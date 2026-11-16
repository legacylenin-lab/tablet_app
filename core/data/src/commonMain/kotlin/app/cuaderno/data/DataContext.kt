package app.cuaderno.data

import app.cuaderno.data.db.CuadernoDatabase
import app.cuaderno.model.Hlc
import app.cuaderno.model.HlcClock

/**
 * Lo que comparten los repositorios: la base de datos, quién es este dispositivo y
 * cómo se obtiene la hora (como función, para poder probar con relojes falsos).
 */
class DataContext(
    val db: CuadernoDatabase,
    val deviceId: String,
    val authorId: String,
    val nowMillis: () -> Long,
) {
    /**
     * Emite el siguiente HLC y lo guarda. Debe llamarse **dentro de una transacción**,
     * junto con la escritura que lo usa.
     */
    fun nextHlc(): Hlc {
        val stored = db.cuadernoQueries.selectClock().executeAsOneOrNull()
        val clock = HlcClock(deviceId, stored?.let(Hlc::parse))
        val hlc = clock.next(nowMillis())
        db.cuadernoQueries.upsertClock(hlc.toText())
        return hlc
    }

    /** Anota la operación en `change_log` (base de la sincronización de la v2). */
    fun logChange(entity: String, entityId: String, op: ChangeOp, hlc: Hlc) {
        db.cuadernoQueries.insertChange(entity, entityId, op.name, hlc.toText())
    }
}

enum class ChangeOp { UPSERT, DELETE }
