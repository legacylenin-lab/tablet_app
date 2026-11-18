package app.cuaderno.data

import app.cuaderno.domain.DocumentFileStore
import app.cuaderno.domain.PlatformFile
import app.cuaderno.domain.StoredFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Guarda los PDF en [documentsDir] como `<sha256>.pdf` (docs/arquitectura.md §5.2). Sirve para
 * Android y Windows; cada plataforma dice cómo abrir el archivo del usuario con [open].
 *
 * La copia se hace primero a un archivo temporal (`.tmp`, que el respaldo ignora) mientras se
 * calcula el hash, y al final se renombra. Así nunca queda un `<sha256>.pdf` a medio copiar.
 */
class FileDocumentStore(
    private val documentsDir: File,
    private val dispatcher: CoroutineDispatcher,
    private val open: (PlatformFile) -> InputStream,
) : DocumentFileStore {

    override suspend fun store(source: PlatformFile): StoredFile = withContext(dispatcher) {
        documentsDir.mkdirs()
        val temp = File.createTempFile("import-", ".tmp", documentsDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val head = ByteArray(HEAD_SIZE)
            var headLength = 0
            var size = 0L
            open(source).use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        if (headLength < HEAD_SIZE) {
                            val take = minOf(n, HEAD_SIZE - headLength)
                            buffer.copyInto(head, headLength, 0, take)
                            headLength += take
                        }
                        size += n
                    }
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val target = fileFor(sha256)
            val isNew = !target.exists()
            if (isNew && !temp.renameTo(target)) {
                temp.copyTo(target)
            }
            StoredFile(sha256, size, startsLikePdf(head, headLength), isNew)
        } finally {
            temp.delete()
        }
    }

    override suspend fun discard(sha256: String) {
        withContext(dispatcher) { fileFor(sha256).delete() }
    }

    /** El archivo guardado de un documento. */
    fun fileFor(sha256: String): File {
        require(sha256.matches(SHA256)) { "Hash inválido: $sha256" }
        return File(documentsDir, "$sha256.pdf")
    }

    private companion object {
        /** Un PDF trae `%PDF-` al principio; se tolera algo de basura antes, como hacen los lectores. */
        const val HEAD_SIZE = 1024
        val MAGIC = "%PDF-".encodeToByteArray()
        val SHA256 = Regex("[0-9a-f]{64}")

        fun startsLikePdf(head: ByteArray, length: Int): Boolean =
            (0..length - MAGIC.size).any { i -> MAGIC.indices.all { head[i + it] == MAGIC[it] } }
    }
}
