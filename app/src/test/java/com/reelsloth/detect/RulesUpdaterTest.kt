package com.reelsloth.detect

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RulesUpdaterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bundled = File("src/main/assets/detection-rules.json").readText()
    private val calibrated = File("../rules/detection-rules.json").readText()
    private val repository by lazy {
        RulesRepository({ bundled }, File(folder.root, RulesRepository.FILE_NAME), Dispatchers.Unconfined)
    }
    private val requested = mutableListOf<String>()
    private val answered = mutableListOf<Long>()

    private fun updater(url: String, fetcher: RulesFetcher) =
        RulesUpdater(url, { requested += it; fetcher.fetch(it) }, repository, { answered += it }, { 42L })

    @Test
    fun emptyUrlNeverTouchesTheNetwork() = runTest {
        val updater = updater("") { error("must not fetch") }

        assertEquals(RulesUpdater.CheckResult.NotConfigured, updater.check())
        assertEquals(false, updater.isConfigured)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun plainHttpIsRefused() = runTest {
        val result = updater("http://example.com/rules.json") { error("must not fetch") }.check()

        assertTrue(result is RulesUpdater.CheckResult.Failed)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun newerServerRulesAreAppliedAndTheCheckIsRecorded() = runTest {
        val result = updater("https://example.com/rules.json") { calibrated }.check()

        assertEquals(RulesUpdater.CheckResult.Updated(repository.rules.value.rules.rulesVersion), result)
        assertEquals(RulesSource.DOWNLOADED, repository.rules.value.source)
        assertEquals(listOf("https://example.com/rules.json"), requested)
        assertEquals(listOf(42L), answered)
    }

    @Test
    fun sameVersionIsUpToDate() = runTest {
        updater("https://example.com/rules.json") { calibrated }.check()

        val again = updater("https://example.com/rules.json") { calibrated }.check()

        assertTrue(again is RulesUpdater.CheckResult.UpToDate)
    }

    @Test
    fun networkFailureIsReportedAndNotRecordedAsAnswered() = runTest {
        val result = updater("https://example.com/rules.json") { throw IOException("offline") }.check()

        assertEquals(RulesUpdater.CheckResult.Failed("Could not reach the rules server."), result)
        assertTrue(answered.isEmpty())
        assertEquals(RulesSource.BUNDLED, repository.rules.value.source)
    }

    @Test
    fun unusableServerFileIsReported() = runTest {
        val result = updater("https://example.com/rules.json") { "<html>oops</html>" }.check()

        assertTrue(result is RulesUpdater.CheckResult.Failed)
        assertEquals(RulesSource.BUNDLED, repository.rules.value.source)
    }
}
