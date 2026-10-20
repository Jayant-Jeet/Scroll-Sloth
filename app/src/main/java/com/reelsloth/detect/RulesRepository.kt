package com.reelsloth.detect

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Holds the active detection rules: [overrideFile] when present and valid (imported calibrated
 * rules, or a downloaded update), otherwise the bundled asset. Never touches the network itself;
 * [RulesUpdater] hands it downloaded text.
 */
class RulesRepository(
    private val bundledRules: () -> String,
    private val overrideFile: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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

    private val state = MutableStateFlow(loadNow())
    val rules: StateFlow<LoadedRules> = state.asStateFlow()

    suspend fun reload(): LoadedRules = withContext(ioDispatcher) { loadNow() }.also { state.value = it }

    /** Validates [text] and, only if it parses, saves it as the override. */
    suspend fun importOverride(text: String): ImportResult = withContext(ioDispatcher) {
        val loaded = try {
            DetectionRulesLoader.load(text, RulesSource.OVERRIDE)
        } catch (e: RulesFormatException) {
            return@withContext ImportResult.Rejected(e.message ?: "Not a valid rules file.")
        }
        try {
            writeAtomically(text)
            downloadedMarker.delete()
        } catch (e: IOException) {
            return@withContext ImportResult.Rejected("Could not save the rules: ${e.message}")
        }
        state.value = loaded
        ImportResult.Imported(loaded)
    }

    /**
     * Saves downloaded rules only when they parse and are newer than the active ones, so an older
     * server file never replaces rules the user imported.
     */
    suspend fun applyDownloaded(text: String): UpdateResult = withContext(ioDispatcher) {
        val loaded = try {
            DetectionRulesLoader.load(text, RulesSource.DOWNLOADED)
        } catch (e: RulesFormatException) {
            return@withContext UpdateResult.Rejected(e.message ?: "Not a valid rules file.")
        }
        val current = state.value.rules.rulesVersion
        if (loaded.rules.rulesVersion <= current) return@withContext UpdateResult.UpToDate(current)
        try {
            writeAtomically(text)
            downloadedMarker.createNewFile()
        } catch (e: IOException) {
            return@withContext UpdateResult.Rejected("Could not save the rules: ${e.message}")
        }
        state.value = loaded
        UpdateResult.Updated(loaded)
    }

    suspend fun resetToBundled(): LoadedRules = withContext(ioDispatcher) {
        overrideFile.delete()
        downloadedMarker.delete()
        loadNow()
    }.also { state.value = it }

    private fun loadNow(): LoadedRules {
        var warning: String? = null
        if (overrideFile.exists()) {
            try {
                val source = if (downloadedMarker.exists()) RulesSource.DOWNLOADED else RulesSource.OVERRIDE
                return DetectionRulesLoader.load(overrideFile.readText(), source)
            } catch (e: RulesFormatException) {
                warning = "Imported rules were ignored: ${e.message}"
            } catch (e: IOException) {
                warning = "Imported rules could not be read: ${e.message}"
            }
        }
        return DetectionRulesLoader.load(bundledRules(), RulesSource.BUNDLED).copy(warning = warning)
    }

    private fun writeAtomically(text: String) {
        overrideFile.parentFile?.mkdirs()
        val temp = File(overrideFile.parentFile, "${overrideFile.name}.tmp")
        temp.writeText(text)
        Files.move(temp.toPath(), overrideFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        const val FILE_NAME = "detection-rules.json"
    }
}
