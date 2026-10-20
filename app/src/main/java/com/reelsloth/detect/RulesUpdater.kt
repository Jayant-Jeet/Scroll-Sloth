package com.reelsloth.detect

import java.io.IOException

/** Downloads the rules file. Throws [IOException] on any network or HTTP problem. */
fun interface RulesFetcher {
    suspend fun fetch(url: String): String
}

/**
 * Asks the rules server for a newer `detection-rules.json`. This is one of the app's two allowed
 * network calls: a plain GET of a public file with no user data, no query parameters and no
 * identifiers. An empty [url] (the default build) disables it.
 */
class RulesUpdater(
    private val url: String,
    private val fetcher: RulesFetcher,
    private val repository: RulesRepository,
    private val onServerAnswered: suspend (Long) -> Unit,
    private val clock: Clock,
) {
    sealed interface CheckResult {
        data object NotConfigured : CheckResult
        data class UpToDate(val version: Int) : CheckResult
        data class Updated(val version: Int) : CheckResult
        data class Failed(val reason: String) : CheckResult
    }

    val isConfigured: Boolean get() = url.isNotBlank()

    suspend fun check(): CheckResult {
        if (url.isBlank()) return CheckResult.NotConfigured
        if (!url.startsWith("https://")) return CheckResult.Failed("The rules address must use https.")
        val text = try {
            fetcher.fetch(url)
        } catch (e: IOException) {
            return CheckResult.Failed("Could not reach the rules server.")
        }
        onServerAnswered(clock.nowMs())
        return when (val result = repository.applyDownloaded(text)) {
            is RulesRepository.UpdateResult.Updated -> CheckResult.Updated(result.rules.rules.rulesVersion)
            is RulesRepository.UpdateResult.UpToDate -> CheckResult.UpToDate(result.version)
            is RulesRepository.UpdateResult.Rejected -> CheckResult.Failed("The server's rules were not usable: ${result.reason}")
        }
    }
}
