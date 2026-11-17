package app.cuaderno.data.backup

import app.cash.sqldelight.db.SqlDriver
import app.cuaderno.model.EntityId
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Copia la base de datos con `VACUUM INTO`, usando el mismo driver con el que la app la abre. */
class SqlDriverSnapshotter(private val driver: SqlDriver) : DatabaseSnapshotter {
    override fun snapshotTo(target: File) {
        require(!target.exists()) { "El destino ya existe: $target" }
        val path = target.absolutePath.replace("'", "''")
        driver.execute(null, "VACUUM INTO '$path'", 0)
    }
}

/**
 * Exporta, inspecciona y verifica respaldos. No toca los datos en uso: restaurar se hace en dos
 * tiempos (primero [extractVerified] a una carpeta de preparación, y luego la app intercambia
 * los archivos), para no cambiar nada hasta que todo se haya comprobado.
 *
 * @param documentsDir carpeta privada con los PDF (`<sha256>.pdf`).
 * @param workDir carpeta temporal para la copia de la base de datos.
 */
class BackupService(
    private val snapshotter: DatabaseSnapshotter,
    private val documentsDir: File,
    private val workDir: File,
    private val deviceId: EntityId,
    private val appVersion: String,
    private val schemaVersion: Int,
    private val nowMillis: () -> Long,
) {

    /** Crea el respaldo en [destination]. Devuelve el manifiesto escrito. */
    fun export(destination: StreamSink): BackupManifest {
        workDir.mkdirs()
        val snapshot = File(workDir, "export-${nowMillis()}-${System.nanoTime()}.sqlite")
        try {
            snapshotter.snapshotTo(snapshot)
            val documents = documentsDir.listFiles { f -> f.isFile && BackupFormat.isDocumentFile(f) }
                ?.sortedBy { it.name }.orEmpty()

            val checksums = linkedMapOf(BackupFormat.DATABASE to sha256(snapshot))
            for (doc in documents) checksums[BackupFormat.documentEntry(doc.nameWithoutExtension)] = sha256(doc)

            val manifest = BackupManifest(
                formatVersion = BackupFormat.FORMAT_VERSION,
                appVersion = appVersion,
                createdAtMillis = nowMillis(),
                deviceId = deviceId.value,
                schemaVersion = schemaVersion,
                documentCount = documents.size,
                checksums = checksums,
            )

            ZipOutputStream(destination.openOutput().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST))
                zip.write(BackupFormat.json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
                zip.addFile(BackupFormat.DATABASE, snapshot)
                for (doc in documents) zip.addFile(BackupFormat.documentEntry(doc.nameWithoutExtension), doc)
            }
            return manifest
        } finally {
            snapshot.delete()
        }
    }

    /**
     * Lee solo el manifiesto (la primera entrada) y comprueba que esta app pueda restaurarlo.
     * No recorre todo el archivo: para eso está [extractVerified].
     */
    fun inspect(source: StreamSource): BackupInfo {
        val manifest = source.openInput().use { readManifest(ZipInputStream(it.buffered())) }
        return checkCompatible(manifest).let {
            BackupInfo(manifest.formatVersion, manifest.appVersion, manifest.createdAtMillis, manifest.schemaVersion, manifest.documentCount)
        }
    }

    /**
     * Recorre todo el respaldo, comprueba cada checksum y deja los archivos en [stagingDir]
     * (`database.sqlite` y `documents/…`). Si algo falla, borra [stagingDir] y lanza [BackupException].
     */
    fun extractVerified(source: StreamSource, stagingDir: File): BackupManifest {
        require(!stagingDir.exists() || stagingDir.list().isNullOrEmpty()) { "La carpeta de preparación debe estar vacía" }
        stagingDir.mkdirs()
        try {
            source.openInput().use { raw ->
                val zip = ZipInputStream(raw.buffered())
                val manifest = checkCompatible(readManifest(zip))
                val seen = mutableSetOf<String>()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (entry.isDirectory) continue
                    corruptIf(!BackupFormat.isAllowedEntry(name) || name == BackupFormat.MANIFEST, "Entrada no permitida: $name")
                    corruptIf(!seen.add(name), "Entrada repetida: $name")
                    val expected = manifest.checksums[name]
                    corruptIf(expected == null, "Entrada fuera del manifiesto: $name")
                    val target = File(stagingDir, name)
                    target.parentFile.mkdirs()
                    val actual = copyAndHash(zip, target)
                    corruptIf(actual != expected, "Checksum distinto en $name")
                }
                corruptIf(seen != manifest.checksums.keys, "Faltan entradas: ${manifest.checksums.keys - seen}")
                val documents = seen.count { it != BackupFormat.DATABASE }
                corruptIf(documents != manifest.documentCount, "documentCount no coincide")
                return manifest
            }
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            throw if (e is BackupException) e else BackupException(BackupProblem.CORRUPT, e.message)
        }
    }

    private fun readManifest(zip: ZipInputStream): BackupManifest {
        val entry = try {
            zip.nextEntry
        } catch (e: Exception) {
            throw BackupException(BackupProblem.NOT_A_BACKUP)
        }
        if (entry == null || entry.name != BackupFormat.MANIFEST) throw BackupException(BackupProblem.NOT_A_BACKUP)
        val text = readUpTo(zip, MAX_MANIFEST_BYTES + 1)
        if (text.size > MAX_MANIFEST_BYTES) throw BackupException(BackupProblem.NOT_A_BACKUP)
        return try {
            BackupFormat.json.decodeFromString(BackupManifest.serializer(), text.decodeToString())
        } catch (e: Exception) {
            throw BackupException(BackupProblem.NOT_A_BACKUP)
        }
    }

    /** Lee como máximo [limit] bytes (sin depender de `readNBytes`, que Android viejo no tiene). */
    private fun readUpTo(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun checkCompatible(m: BackupManifest): BackupManifest {
        if (m.formatVersion > BackupFormat.FORMAT_VERSION) throw BackupException(BackupProblem.NEWER_FORMAT)
        if (m.formatVersion < 1) throw BackupException(BackupProblem.NOT_A_BACKUP)
        if (m.schemaVersion > schemaVersion) throw BackupException(BackupProblem.NEWER_SCHEMA)
        return m
    }

    private fun corruptIf(condition: Boolean, detail: String) {
        if (condition) throw BackupException(BackupProblem.CORRUPT, detail)
    }

    private fun ZipOutputStream.addFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun copyAndHash(input: InputStream, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        target.outputStream().use { out: OutputStream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                out.write(buffer, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_MANIFEST_BYTES = 1 shl 20
    }
}
