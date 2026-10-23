package com.halfbloodcoder.scrollsloth.detect

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
    private val calibrated = File("../site/detection-rules.json").readText()
    private val repository by lazy {
        RulesRepository({ bundled }, File(folder.root, RulesRepository.FILE_NAME), Dispatchers.Unconfined)
    }
    private val requested = mutableListOf<String>()
    private val answered = mutableListOf<Long>()

    private fun updater(url: String, fetcher: suspend (String) -> String) =
        RulesUpdater({ url }, { requested += it; fetcher(it).toByteArray() }, repository, { answered += it }, { 42L },
            RulesSignatureVerifier { emptyList() })

    @Test
    fun emptyUrlNeverTouchesTheNetwork() = runTest {
        val updater = updater("") { error("must not fetch") }

        assertEquals(RulesUpdater.CheckResult.NotConfigured, updater.check())
        assertEquals(false, updater.isConfigured())
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
        val result = updater("https://example.com/rules.json") { calibrated }.check(allowUnverified = true)

        assertEquals(RulesUpdater.CheckResult.Updated(repository.rules.value.rules.rulesVersion), result)
        assertEquals(RulesSource.DOWNLOADED, repository.rules.value.source)
        assertEquals(listOf("https://example.com/rules.json"), requested)
        assertEquals(listOf(42L), answered)
    }

    @Test
    fun sameVersionIsUpToDate() = runTest {
        updater("https://example.com/rules.json") { calibrated }.check(allowUnverified = true)

        val again = updater("https://example.com/rules.json") { calibrated }.check(allowUnverified = true)

        assertTrue(again is RulesUpdater.CheckResult.UpToDate)
    }

    @Test
    fun networkFailureIsReportedAndNotRecordedAsAnswered() = runTest {
        val result = updater("https://example.com/rules.json") { throw IOException("offline") }.check(allowUnverified = true)

        assertEquals(RulesUpdater.CheckResult.Failed("Could not reach the rules server."), result)
        assertTrue(answered.isEmpty())
        assertEquals(RulesSource.BUNDLED, repository.rules.value.source)
    }

    @Test
    fun unusableServerFileIsReported() = runTest {
        val result = updater("https://example.com/rules.json") { "<html>oops</html>" }.check(allowUnverified = true)

        assertTrue(result is RulesUpdater.CheckResult.Failed)
        assertEquals(RulesSource.BUNDLED, repository.rules.value.source)
    }

    @Test
    fun addressIsReadAtEachCheckSoSettingsChangesApply() = runTest {
        var address = ""
        val updater = RulesUpdater({ address }, { requested += it; calibrated.toByteArray() }, repository, { answered += it }, { 42L },
            RulesSignatureVerifier { emptyList() })

        assertEquals(RulesUpdater.CheckResult.NotConfigured, updater.check())
        address = "https://example.org/rules.json"

        assertTrue(updater.isConfigured())
        assertTrue(updater.check(allowUnverified = true) is RulesUpdater.CheckResult.Updated)
        assertEquals(listOf("https://example.org/rules.json"), requested)
    }

    @Test
    fun rejectedServerFileLeavesPreviouslyDownloadedRulesActive() = runTest {
        updater("https://example.com/rules.json") { calibrated }.check(allowUnverified = true)
        val before = repository.rules.value

        val result = updater("https://example.com/rules.json") { """{"schemaVersion": 1, "rulesVersion": 2147483647}""" }.check(allowUnverified = true)

        assertTrue(result is RulesUpdater.CheckResult.Failed)
        assertEquals(before, repository.rules.value)
    }

    @Test
    fun addressValidation() {
        assertEquals(null, RulesAddress.problem(""))
        assertEquals(null, RulesAddress.problem("https://jayant-jeet.github.io/Scroll-Sloth/detection-rules.json"))
        assertEquals(null, RulesAddress.problem("  HTTPS://example.com/r.json  "))
        assertTrue(RulesAddress.problem("http://example.com/r.json") != null)
        assertTrue(RulesAddress.problem("ftp://example.com/r.json") != null)
        assertTrue(RulesAddress.problem("example.com/r.json") != null)
        assertTrue(RulesAddress.problem("https:///r.json") != null)
        assertTrue(RulesAddress.problem("https://user:pass@example.com/r.json") != null)
        assertTrue(RulesAddress.problem("https://exa mple.com/") != null)
        assertTrue(RulesAddress.problem("https://example.com/" + "a".repeat(3_000)) != null)
    }

    @Test
    fun invalidAddressNeverTouchesTheNetwork() = runTest {
        val result = updater("https://user:pass@example.com/rules.json") { error("must not fetch") }.check()

        assertTrue(result is RulesUpdater.CheckResult.Failed)
        assertTrue(requested.isEmpty())
    }
}