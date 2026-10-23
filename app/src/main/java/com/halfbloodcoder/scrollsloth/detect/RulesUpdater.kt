package com.halfbloodcoder.scrollsloth.detect

import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

/** Downloads the rules file. Throws [IOException] on any network or HTTP problem. */
fun interface RulesFetcher {
    suspend fun fetch(url: String): ByteArray
}

/** Checks a rules server address typed by the user or set at build time. */
object RulesAddress {
    /** Returns null when [address] can be used, otherwise a short reason. Blank means "updates off". */
    fun problem(address: String): String? {
        if (address.isBlank()) return null
        if (address.length > MAX_LENGTH) return "That address is too long."
        val uri = try {
            URI(address.trim())
        } catch (_: URISyntaxException) {
            return "That is not a web address."
        }
        return when {
            !uri.scheme.equals("https", ignoreCase = true) -> "The rules address must use https."
            uri.host.isNullOrBlank() -> "The address needs a host name."
            uri.rawUserInfo != null -> "The address must not contain a user name or password."
            else -> null
        }
    }

    private const val MAX_LENGTH = 2_048
}

/**
 * Asks the rules server for a newer `detection-rules.json`. This is one of the app's two allowed
 * network operations: public rules and signature GETs with no user data or identifiers. The address
 * comes from [url] (Settings, or the build default); a blank address turns updates off.
 */
class RulesUpdater(
    private val url: suspend () -> String,
    private val fetcher: RulesFetcher,
    private val repository: RulesRepository,
    private val onServerAnswered: suspend (Long) -> Unit,
    private val clock: Clock,
    private val signatureVerifier: RulesSignatureVerifier,
    private val onStatus: suspend (String) -> Unit = {},
) {
    sealed interface CheckResult {
        data object NotConfigured : CheckResult
        data class UpToDate(val version: Int) : CheckResult
        data class Updated(val version: Int) : CheckResult
        data class Failed(val reason: String) : CheckResult
        data object ConfirmationRequired : CheckResult
    }

    suspend fun isConfigured(): Boolean = url().isNotBlank()

    /** On any failure the active rules (override, last-good or bundled) stay exactly as they were. */
    suspend fun check(allowUnverified: Boolean = false): CheckResult {
        val result = checkNow(allowUnverified)
        onStatus(when (result) {
            is CheckResult.Failed -> result.reason
            is CheckResult.Updated -> "Updated to rules version ${result.version}."
            is CheckResult.UpToDate -> "Already up to date (version ${result.version})."
            CheckResult.NotConfigured -> "Rule updates are off."
            CheckResult.ConfirmationRequired -> "Unverified rules need confirmation in Settings."
        })
        return result
    }

    private suspend fun checkNow(allowUnverified: Boolean): CheckResult {
        val url = url().trim()
        if (url.isBlank()) return CheckResult.NotConfigured
        RulesAddress.problem(url)?.let { return CheckResult.Failed(it) }
        val official = url == OFFICIAL_URL
        if (!official && !allowUnverified) return CheckResult.ConfirmationRequired
        val bytes = try {
            fetcher.fetch(url)
        } catch (e: IOException) {
            return CheckResult.Failed("Could not reach the rules server.")
        }
        onServerAnswered(clock.nowMs())
        val signature = if (official) {
            val signature = try {
                fetcher.fetch("$url.sig")
            } catch (_: IOException) {
                return CheckResult.Failed("Signature check failed: missing or unreadable signature.")
            }
            if (!signatureVerifier.verify(bytes, signature)) {
                return CheckResult.Failed("Signature check failed: invalid signature or public key.")
            }
            signature
        } else null
        return when (val result = repository.applyDownloaded(bytes, signature)) {
            is RulesRepository.UpdateResult.Updated -> CheckResult.Updated(result.rules.rules.rulesVersion)
            is RulesRepository.UpdateResult.UpToDate -> CheckResult.UpToDate(result.version)
            is RulesRepository.UpdateResult.Rejected -> CheckResult.Failed("The server's rules were not usable: ${result.reason}")
        }
    }

    companion object {
        const val OFFICIAL_URL = "https://jayant-jeet.github.io/Scroll-Sloth/detection-rules.json"
    }
}
