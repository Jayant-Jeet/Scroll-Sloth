package com.halfbloodcoder.scrollsloth.detect

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Holds the active detection rules. In order of preference: [overrideFile] (imported calibrated
 * rules, or a downloaded update), the last-good copy of the override it replaced, then the bundled
 * asset. Files are only ever replaced by a write-to-temp-then-rename, and only with text that has
 * already passed validation. Never touches the network itself; [RulesUpdater] hands it downloaded text.
 */
class RulesRepository(
    private val bundledRules: () -> String,
    private val overrideFile: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val signatureVerifier: RulesSignatureVerifier = RulesSignatureVerifier { emptyList() },
) {
    sealed interface ImportResult {
        data class Imported(val rules: LoadedRules) : ImportResult
        data class Rejected(val reason: String) : ImportResult
    }

    sealed interface UpdateResult {
        data class Updated(val rules: LoadedRules) : UpdateResult
        data class UpToDate(val version: Int) : UpdateResult
        data class Rejected(val reason: String) : UpdateResult
    }

    /** Present next to [overrideFile] when it came from the rules server rather than an import. */
    private val downloadedMarker = File(overrideFile.parentFile, "${overrideFile.name}.downloaded")

    /** The previous override, kept when a new one is saved, in case the new file is damaged on disk. */
    private val lastGoodFile = File(overrideFile.parentFile, "${overrideFile.name}.lastgood")
    private val lastGoodDownloadedMarker = File(overrideFile.parentFile, "${lastGoodFile.name}.downloaded")

    private val mutex = Mutex()
    private val state = MutableStateFlow(loadNow())
    val rules: StateFlow<LoadedRules> = state.asStateFlow()

    suspend fun reload(): LoadedRules = mutex.withLock {
        withContext(ioDispatcher) { loadNow() }.also { state.value = it }
    }

    /** Validates [text] and, only if it parses, saves it as the override. */
    suspend fun importOverride(text: String): ImportResult = mutex.withLock {
        withContext(ioDispatcher) {
            val loaded = try {
                DetectionRulesLoader.load(text, RulesSource.OVERRIDE)
            } catch (e: RulesFormatException) {
                return@withContext ImportResult.Rejected(e.message ?: "Not a valid rules file.")
            }
            try {
                replaceOverride(text.toByteArray(Charsets.UTF_8), downloaded = false)
            } catch (e: IOException) {
                return@withContext ImportResult.Rejected("Could not save the rules: ${e.message}")
            }
            state.value = loaded
            ImportResult.Imported(loaded)
        }
    }

    /**
     * Saves downloaded rules only when they parse, enable at least one app, and are newer than the
     * active ones, so an older or placeholder server file never replaces working rules.
     */
    suspend fun applyDownloaded(text: String): UpdateResult = applyDownloaded(text.toByteArray(Charsets.UTF_8))

    suspend fun applyDownloaded(bytes: ByteArray, signature: ByteArray? = null): UpdateResult = mutex.withLock {
        withContext(ioDispatcher) {
            if (signature != null && !signatureVerifier.verify(bytes, signature)) {
                return@withContext UpdateResult.Rejected("Signature check failed.")
            }
            val loaded = try {
                DetectionRulesLoader.load(
                    bytes.toString(Charsets.UTF_8),
                    if (signature != null) RulesSource.OFFICIAL else RulesSource.DOWNLOADED,
                )
            } catch (e: RulesFormatException) {
                return@withContext UpdateResult.Rejected(e.message ?: "Not a valid rules file.")
            }
            val current = state.value.rules.rulesVersion
            if (loaded.rules.rulesVersion <= current) return@withContext UpdateResult.UpToDate(current)
            if (loaded.enabledApps.isEmpty()) {
                return@withContext UpdateResult.Rejected("No app in them is calibrated yet.")
            }
            try {
                replaceOverride(bytes, downloaded = true, signature = signature)
            } catch (e: IOException) {
                return@withContext UpdateResult.Rejected("Could not save the rules: ${e.message}")
            }
            state.value = loaded
            UpdateResult.Updated(loaded)
        }
    }

    suspend fun resetToBundled(): LoadedRules = mutex.withLock {
        withContext(ioDispatcher) {
            listOf(overrideFile, downloadedMarker, lastGoodFile, lastGoodDownloadedMarker).forEach { it.delete() }
            loadNow()
        }.also { state.value = it }
    }

    private fun loadNow(): LoadedRules {
        var warning: String? = null
        if (overrideFile.exists()) {
            when (val result = tryLoad(overrideFile, downloadedMarker)) {
                is Attempt.Loaded -> return result.rules
                is Attempt.Failed -> warning = "Saved rules were ignored: ${result.reason}"
            }
            if (lastGoodFile.exists()) {
                val lastGood = tryLoad(lastGoodFile, lastGoodDownloadedMarker)
                if (lastGood is Attempt.Loaded) {
                    return lastGood.rules.copy(warning = "$warning Using the previous good rules.")
                }
            }
        }
        return DetectionRulesLoader.load(bundledRules(), RulesSource.BUNDLED).copy(warning = warning)
    }

    private sealed interface Attempt {
        data class Loaded(val rules: LoadedRules) : Attempt
        data class Failed(val reason: String?) : Attempt
    }

    private fun tryLoad(file: File, marker: File): Attempt = try {
        val bytes = file.readBytes()
        val proof = marker.takeIf { it.exists() }?.readBytes()
        val source = when {
            proof == null -> RulesSource.OVERRIDE
            proof.isEmpty() -> RulesSource.DOWNLOADED
            signatureVerifier.verify(bytes, proof) -> RulesSource.OFFICIAL
            else -> throw RulesFormatException("signature check failed")
        }
        Attempt.Loaded(DetectionRulesLoader.load(bytes.toString(Charsets.UTF_8), source))
    } catch (e: RulesFormatException) {
        Attempt.Failed(e.message)
    } catch (e: IOException) {
        Attempt.Failed("could not be read (${e.message})")
    }

    /** Keeps the current override (if it still loads) as last-good, then swaps in [bytes]. */
    private fun replaceOverride(bytes: ByteArray, downloaded: Boolean, signature: ByteArray? = null) {
        overrideFile.parentFile?.mkdirs()
        if (overrideFile.exists() && tryLoad(overrideFile, downloadedMarker) is Attempt.Loaded) {
            writeAtomically(lastGoodFile, overrideFile.readBytes())
            if (downloadedMarker.exists()) writeAtomically(lastGoodDownloadedMarker, downloadedMarker.readBytes())
            else lastGoodDownloadedMarker.delete()
        }
        if (downloaded) writeAtomically(downloadedMarker, signature ?: byteArrayOf())
        else downloadedMarker.delete()
        writeAtomically(overrideFile, bytes)
    }

    /** Writes and syncs a temp file, then renames it over [target], so readers see old or new, never half. */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val FILE_NAME = "detection-rules.json"
    }
}
