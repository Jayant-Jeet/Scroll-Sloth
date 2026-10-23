package com.halfbloodcoder.scrollsloth.detect

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.Assert.assertArrayEquals
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignedRulesUpdaterTest {
    @get:Rule val folder = TemporaryFolder()
    private val primary = signingKey()
    private val backup = signingKey()
    private val verifier = RulesSignatureVerifier { listOf(primary.public.encoded, backup.public.encoded) }
    private val bundled = File("src/main/assets/detection-rules.json").readText()
    private val published = File("../site/detection-rules.json").readBytes()
    private val file by lazy { File(folder.root, RulesRepository.FILE_NAME) }
    private fun repository() = RulesRepository({ bundled }, file, Dispatchers.Unconfined, verifier)
    private val repository by lazy { repository() }
    private val requested = mutableListOf<String>()
    private val statuses = mutableListOf<String>()

    private fun updater(
        bytes: ByteArray = published,
        signature: ByteArray? = sign(bytes, primary),
        url: String = RulesUpdater.OFFICIAL_URL,
    ) = RulesUpdater(
        { url },
        { address ->
            requested += address
            if (address.endsWith(".sig")) signature ?: throw IOException("HTTP 404") else bytes
        },
        repository, {}, { 42L }, verifier, { statuses += it },
    )

    @Test fun officialRulesAreVerifiedAndRemainOfficialAfterReload() = runTest {
        assertTrue(updater().check() is RulesUpdater.CheckResult.Updated)
        assertEquals(RulesSource.OFFICIAL, repository.rules.value.source)
        assertEquals(RulesSource.OFFICIAL, repository().rules.value.source)
        assertEquals(listOf(RulesUpdater.OFFICIAL_URL, "${RulesUpdater.OFFICIAL_URL}.sig"), requested)
    }

    @Test fun missingSignatureKeepsCurrentRulesAndRecordsFailure() = runTest {
        val before = repository.rules.value
        assertTrue(updater(signature = null).check() is RulesUpdater.CheckResult.Failed)
        assertEquals(before, repository.rules.value)
        assertTrue(statuses.single().startsWith("Signature check failed"))
        assertTrue(!file.exists())
    }

    @Test fun changedBytesAreRejectedBeforeParsing() = runTest {
        val changed = published.copyOf().apply { this[0] = 0 }
        assertTrue(updater(changed, sign(published, primary)).check() is RulesUpdater.CheckResult.Failed)
        assertTrue(statuses.single().startsWith("Signature check failed"))
    }

    @Test fun wrongKeyAndMalformedSignatureRejected() = runTest {
        assertTrue(updater(signature = sign(published, signingKey())).check() is RulesUpdater.CheckResult.Failed)
        assertTrue(updater(signature = byteArrayOf(1, 2)).check() is RulesUpdater.CheckResult.Failed)
        assertEquals(RulesSource.BUNDLED, repository.rules.value.source)
    }

    @Test fun backupKeyAccepted() = runTest {
        assertTrue(updater(signature = sign(published, backup)).check() is RulesUpdater.CheckResult.Updated)
    }

    @Test fun validlySignedLowerVersionDoesNotReplaceActiveRules() = runTest {
        updater().check()
        val before = repository.rules.value
        val lower = published.toString(Charsets.UTF_8)
            .replace(Regex("\"rulesVersion\"\\s*:\\s*\\d+"), "\"rulesVersion\": ${before.rules.rulesVersion - 1}").toByteArray()
        val result = updater(lower).check()
        assertEquals(RulesUpdater.CheckResult.UpToDate(before.rules.rulesVersion), result)
        assertEquals(before, repository.rules.value)
    }

    @Test fun validSignatureDoesNotBypassSchemaValidation() = runTest {
        assertTrue(updater("{}".toByteArray()).check() is RulesUpdater.CheckResult.Failed)
        assertEquals(RulesSource.BUNDLED, repository.rules.value.source)
    }

    @Test fun customAddressRequiresConfirmationAndIsNeverOfficial() = runTest {
        val updater = updater(url = "https://example.org/rules.json")
        assertEquals(RulesUpdater.CheckResult.ConfirmationRequired, updater.check())
        assertTrue(requested.isEmpty())
        assertTrue(updater.check(allowUnverified = true) is RulesUpdater.CheckResult.Updated)
        assertEquals(RulesSource.DOWNLOADED, repository.rules.value.source)
        assertEquals(1, requested.size)
    }

    @Test fun officialAddressCannotBypassVerificationWithConfirmation() = runTest {
        assertTrue(updater(signature = null).check(allowUnverified = true) is RulesUpdater.CheckResult.Failed)
    }

    @Test fun tamperedSavedOfficialRulesFallBackToLastGood() = runTest {
        updater().check()
        val version = repository.rules.value.rules.rulesVersion
        val newer = published.toString(Charsets.UTF_8)
            .replace(Regex("\"rulesVersion\"\\s*:\\s*\\d+"), "\"rulesVersion\": ${version + 1}").toByteArray()
        assertTrue(updater(newer).check() is RulesUpdater.CheckResult.Updated)
        file.appendText(" ")
        val loaded = repository().rules.value
        assertEquals(RulesSource.OFFICIAL, loaded.source)
        assertTrue(loaded.warning.orEmpty().contains("signature check failed"))
        assertEquals(version + 1, repository.rules.value.rules.rulesVersion)
        assertEquals(version, loaded.rules.rulesVersion)
    }

    @Test fun rawBytesIncludingLineEndingsAreSavedUnchanged() = runTest {
        val bytes = published.toString(Charsets.UTF_8).replace("\r\n", "\n").replace("\n", "\r\n").toByteArray()
        assertTrue(updater(bytes).check() is RulesUpdater.CheckResult.Updated)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(RulesSource.OFFICIAL, repository().rules.value.source)
    }

    @Test fun concurrentSignedUpdatesKeepTheNewestFileAndMatchingProof() = runTest {
        updater().check()
        val version = repository.rules.value.rules.rulesVersion
        (1..8).map { increment ->
            async(Dispatchers.Default) {
                val bytes = published.toString(Charsets.UTF_8)
                    .replace(Regex("\"rulesVersion\"\\s*:\\s*\\d+"), "\"rulesVersion\": ${version + increment}")
                    .toByteArray()
                repository.applyDownloaded(bytes, sign(bytes, if (increment % 2 == 0) primary else backup))
            }
        }.awaitAll()
        val loaded = repository().rules.value
        assertEquals(version + 8, loaded.rules.rulesVersion)
        assertEquals(RulesSource.OFFICIAL, loaded.source)
    }
}
