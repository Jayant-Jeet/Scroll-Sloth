package com.halfbloodcoder.scrollsloth.detect

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
    private val calibrated = File("../site/detection-rules.json").readText()

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

    // --- Validation of downloaded rules ---

    private fun withTodoFor(text: String, platform: Platform): String {
        val rules = DetectionRulesLoader.json.decodeFromString(DetectionRules.serializer(), text)
        val rule = rules.apps.getValue(platform.packageName)
        val todo = rule.copy(reelScreenMatchers = listOf(NodeMatcher(viewIdContains = DetectionRules.TODO_MARKER)))
        return DetectionRulesLoader.json.encodeToString(
            DetectionRules.serializer(),
            rules.copy(apps = rules.apps + (platform.packageName to todo)),
        )
    }

    private suspend fun assertDownloadRejected(text: String) {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)
        assertTrue(repo.applyDownloaded(text) is RulesRepository.UpdateResult.Rejected)
        assertFalse(file.exists())
        assertEquals(RulesSource.BUNDLED, repo.rules.value.source)
    }

    @Test
    fun downloadWithUnsupportedSchemaIsRejected() = runTest {
        assertDownloadRejected(calibrated.replace(Regex("\"schemaVersion\":\\s*1"), "\"schemaVersion\": 2"))
    }

    @Test
    fun downloadMissingRequiredFieldsIsRejected() = runTest {
        assertDownloadRejected("""{"schemaVersion": 1, "apps": {}}""")
        assertDownloadRejected(calibrated.replace("\"strategy\"", "\"strategyTypo\""))
    }

    @Test
    fun downloadWithWrongTypesOrBadDwellIsRejected() = runTest {
        assertDownloadRejected(calibrated.replace(Regex("\"rulesVersion\":\\s*\\d+"), "\"rulesVersion\": \"latest\""))
        assertDownloadRejected(calibrated.replace(Regex("\"maxDwellMs\":\\s*\\d+"), "\"maxDwellMs\": 10"))
        assertDownloadRejected(calibrated.replace("\"INSTAGRAM\"", "\"MYSPACE\""))
        assertDownloadRejected("")
    }

    @Test
    fun downloadThatIsAllTodoIsRejectedEvenWhenNewer() = runTest {
        assertDownloadRejected(withVersion(bundled, Int.MAX_VALUE))
    }

    @Test
    fun downloadWithTodoForOneAppDisablesOnlyThatApp() = runTest {
        val repo = repository()

        val result = repo.applyDownloaded(withTodoFor(calibrated, Platform.FACEBOOK))

        assertTrue(result is RulesRepository.UpdateResult.Updated)
        val statuses = repo.rules.value.statuses
        assertEquals(RuleState.NEEDS_CALIBRATION, statuses.getValue(Platform.FACEBOOK).state)
        assertEquals(RuleState.READY, statuses.getValue(Platform.INSTAGRAM).state)
        assertEquals(RuleState.READY, statuses.getValue(Platform.YOUTUBE).state)
        assertFalse(Platform.FACEBOOK.packageName in repo.rules.value.enabledApps)
    }

    @Test
    fun lowerVersionDownloadNeverReplacesNewerDownload() = runTest {
        val repo = repository()
        repo.applyDownloaded(withVersion(calibrated, 10))

        assertEquals(RulesRepository.UpdateResult.UpToDate(10), repo.applyDownloaded(withVersion(calibrated, 9)))
        assertEquals(10, repo.rules.value.rules.rulesVersion)
    }

    // --- Atomic writes and last-good fallback ---

    @Test
    fun writesLeaveNoTempFileBehind() = runTest {
        val repo = repository()
        repo.applyDownloaded(withVersion(calibrated, 10))
        repo.applyDownloaded(withVersion(calibrated, 11))

        assertTrue(folder.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun damagedOverrideFallsBackToLastGoodRules() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)
        repo.applyDownloaded(withVersion(calibrated, 10))
        repo.applyDownloaded(withVersion(calibrated, 11))
        file.writeText("{\"schemaVersion\": 1, \"rulesVer") // Torn by something outside the app.

        val loaded = repository(file).rules.value

        assertEquals(10, loaded.rules.rulesVersion)
        assertEquals(RulesSource.DOWNLOADED, loaded.source)
        assertEquals(3, loaded.enabledApps.size)
        assertNotNull(loaded.warning)
    }

    @Test
    fun damagedOverrideAndLastGoodFallBackToBundled() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)
        repo.applyDownloaded(withVersion(calibrated, 10))
        repo.applyDownloaded(withVersion(calibrated, 11))
        file.writeText("garbage")
        File(folder.root, "${RulesRepository.FILE_NAME}.lastgood").writeText("garbage")

        val loaded = repository(file).rules.value

        assertEquals(RulesSource.BUNDLED, loaded.source)
        assertNotNull(loaded.warning)
    }

    @Test
    fun lastGoodRemembersWhereTheOldRulesCameFrom() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)
        repo.importOverride(withVersion(calibrated, 10))
        repo.applyDownloaded(withVersion(calibrated, 11))
        file.writeText("garbage")

        val loaded = repository(file).rules.value

        assertEquals(10, loaded.rules.rulesVersion)
        assertEquals(RulesSource.OVERRIDE, loaded.source)
    }

    @Test
    fun resetToBundledAlsoForgetsLastGood() = runTest {
        val file = File(folder.root, RulesRepository.FILE_NAME)
        val repo = repository(file)
        repo.applyDownloaded(withVersion(calibrated, 10))
        repo.applyDownloaded(withVersion(calibrated, 11))

        repo.resetToBundled()

        assertEquals(emptyList<String>(), folder.root.list()!!.toList())
    }
}