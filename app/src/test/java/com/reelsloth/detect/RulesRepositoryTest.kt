package com.reelsloth.detect

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RulesRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bundled = File("src/main/assets/detection-rules.json").readText()
    private val calibrated = File("../rules/detection-rules.json").readText()

    private fun repository(overrideFile: File = File(folder.root, RulesRepository.FILE_NAME)) =
        RulesRepository({ bundled }, overrideFile, Dispatchers.Unconfined)

    @Test
    fun usesBundledRulesWhenThereIsNoOverride() {
        val loaded = repository().rules.value
        assertEquals(RulesSource.BUNDLED, loaded.source)
        assertTrue(loaded.enabledApps.isEmpty())
    }

    @Test
    fun prefersOverrideFile() {
        val file = File(folder.root, RulesRepository.FILE_NAME).apply { writeText(calibrated) }
        val loaded = repository(file).rules.value
        assertEquals(RulesSource.OVERRIDE, loaded.source)
        assertEquals(3, loaded.enabledApps.size)
    }

    @Test
    fun brokenOverrideFallsBackToBundledWithWarning() {
        val file = File(folder.root, RulesRepository.FILE_NAME).apply { writeText("""{"schemaVersion": 99}""") }
        val loaded = repository(file).rules.value
        assertEquals(RulesSource.BUNDLED, loaded.source)
        assertNotNull(loaded.warning)
    }

    @Test
    fun importRejectsInvalidRulesWithoutTouchingTheFile() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)

        val result = repo.importOverride("not json")

        assertTrue(result is RulesRepository.ImportResult.Rejected)
        assertFalse(file.exists())
        assertEquals(RulesSource.BUNDLED, repo.rules.value.source)
    }

    @Test
    fun importSavesAndPublishesValidRules() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)

        val result = repo.importOverride(calibrated)

        assertTrue(result is RulesRepository.ImportResult.Imported)
        assertEquals(calibrated, file.readText())
        assertEquals(RulesSource.OVERRIDE, repo.rules.value.source)

        // A second import replaces the first one.
        assertTrue(repo.importOverride(calibrated) is RulesRepository.ImportResult.Imported)
    }

    @Test
    fun resetToBundledDeletesOverride() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME).apply { writeText(calibrated) }
        val repo = repository(file)

        val loaded = repo.resetToBundled()

        assertFalse(file.exists())
        assertEquals(RulesSource.BUNDLED, loaded.source)
        assertEquals(RulesSource.BUNDLED, repo.rules.value.source)
    }

    @Test
    fun reloadPicksUpFileWrittenOutsideTheApp() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)
        file.writeText(calibrated)

        assertEquals(RulesSource.OVERRIDE, repo.reload().source)
    }

    private fun withVersion(text: String, version: Int) =
        text.replace(Regex("\"rulesVersion\":\\s*\\d+"), "\"rulesVersion\": $version")

    @Test
    fun newerDownloadedRulesReplaceBundledAndSurviveReload() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)

        val result = repo.applyDownloaded(calibrated)

        assertTrue(result is RulesRepository.UpdateResult.Updated)
        assertEquals(RulesSource.DOWNLOADED, repo.rules.value.source)
        assertEquals(RulesSource.DOWNLOADED, repository(file).rules.value.source)
    }

    @Test
    fun downloadedRulesNeverReplaceNewerOrEqualImportedRules() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)
        repo.importOverride(withVersion(calibrated, 50))

        assertEquals(RulesRepository.UpdateResult.UpToDate(50), repo.applyDownloaded(withVersion(calibrated, 49)))
        assertEquals(RulesRepository.UpdateResult.UpToDate(50), repo.applyDownloaded(withVersion(calibrated, 50)))
        assertEquals(RulesSource.OVERRIDE, repo.rules.value.source)
        assertEquals(50, repo.rules.value.rules.rulesVersion)
    }

    @Test
    fun brokenDownloadIsRejectedAndKeepsCurrentRules() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)

        assertTrue(repo.applyDownloaded("<html>Not found</html>") is RulesRepository.UpdateResult.Rejected)
        assertFalse(file.exists())
        assertEquals(RulesSource.BUNDLED, repo.rules.value.source)
    }

    @Test
    fun importAfterDownloadCountsAsImported() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)
        repo.applyDownloaded(calibrated)

        repo.importOverride(withVersion(calibrated, 1))

        assertEquals(RulesSource.OVERRIDE, repository(file).rules.value.source)
        repo.resetToBundled()
        assertEquals(RulesSource.BUNDLED, repository(file).rules.value.source)
    }
}
