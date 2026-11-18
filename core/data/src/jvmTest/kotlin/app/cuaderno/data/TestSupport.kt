package app.cuaderno.data

import app.cuaderno.domain.IdGenerator
import app.cuaderno.domain.PdfEngine
import app.cuaderno.domain.PlatformFile
import app.cuaderno.model.EntityId

/** Identificador válido y predecible: `testId(1)`, `testId(2)`… */
fun testId(n: Int): EntityId = EntityId.parse("00000000-0000-4000-8000-" + n.toString(16).padStart(12, '0'))

/** Un identificador válido que no existe en la base (para probar errores). */
val unknownId: EntityId = testId(0xFFFF)

/** Archivo del usuario en memoria, para probar la importación. */
class BytesFile(override val name: String, val bytes: ByteArray) : PlatformFile

/** Motor de PDF de prueba: devuelve [pages] páginas, o falla con [failWith] (como un PDF dañado). */
class FakePdfEngine(var pages: Int = 10, var failWith: Exception? = null) : PdfEngine {
    val asked = mutableListOf<String>()
    override suspend fun pageCount(sha256: String): Int {
        asked += sha256
        failWith?.let { throw it }
        return pages
    }
}

/** Genera `testId(1000)`, `testId(1001)`… para que las pruebas sean predecibles. */
class SequentialIdGenerator(start: Int = 1000) : IdGenerator {
    private var next = start
    override fun newId(): EntityId = testId(next++)
}
