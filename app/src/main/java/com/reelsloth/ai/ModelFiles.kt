package com.reelsloth.ai

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The model file on disk and the marker that proves it was verified.
 *
 * Downloads land in [partFile]. [install] hashes the whole file once; only a matching file is
 * renamed to [modelFile], and the [markerFile] records its checksum, size and modification time.
 * [isReady] then only compares the marker with the file, so the app start check stays cheap.
 */
class ModelFiles(val directory: File, val spec: ModelSpec) {
    val modelFile = File(directory, spec.fileName)
    val partFile = File(directory, spec.fileName + PART_SUFFIX)
    val markerFile = File(directory, spec.fileName + MARKER_SUFFIX)

    @Serializable
    data class Marker(val sha256: String, val sizeBytes: Long, val lastModified: Long)

    fun isReady(): Boolean {
        if (!modelFile.isFile || modelFile.length() != spec.sizeBytes) return false
        val marker = readMarker() ?: return false
        return marker == Marker(spec.sha256, spec.sizeBytes, modelFile.lastModified())
    }

    /** Verifies [partFile] and installs it. Returns false, and deletes it, if it does not match [spec]. */
    fun install(): Boolean {
        val ok = partFile.isFile && partFile.length() == spec.sizeBytes && sha256(partFile) == spec.sha256
        if (!ok) {
            partFile.delete()
            return false
        }
        markerFile.delete()
        modelFile.delete()
        if (!partFile.renameTo(modelFile)) return false
        markerFile.writeText(json.encodeToString(Marker.serializer(), Marker(spec.sha256, spec.sizeBytes, modelFile.lastModified())))
        return true
    }

    /** Removes the installed model and its marker, keeping any download in progress. */
    fun deleteInstalled() {
        markerFile.delete()
        modelFile.delete()
    }

    fun deleteAll() {
        deleteInstalled()
        partFile.delete()
    }

    private fun readMarker(): Marker? =
        runCatching { json.decodeFromString(Marker.serializer(), markerFile.readText()) }.getOrNull()

    companion object {
        const val PART_SUFFIX = ".download"
        const val MARKER_SUFFIX = ".verified.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
