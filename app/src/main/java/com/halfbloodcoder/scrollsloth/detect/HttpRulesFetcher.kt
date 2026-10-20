package com.halfbloodcoder.scrollsloth.detect

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plain GET with a fixed header set ([REQUEST_HEADERS]), no cookies, no cache, and no request body.
 * The User-Agent is overridden because the platform default names the phone model and Android build.
 * Redirects are not followed, so the request never goes anywhere but the configured address.
 * Refuses responses over [maxBytes] so a wrong URL cannot fill memory.
 */
class HttpRulesFetcher(private val maxBytes: Int = MAX_BYTES) : RulesFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            REQUEST_HEADERS.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            if (connection.contentLengthLong > maxBytes) throw IOException("Rules file too large")
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (out.size() + read > maxBytes) throw IOException("Rules file too large")
                    out.write(buffer, 0, read)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Identical for every install, so a request says nothing about who or what sent it. */
        val REQUEST_HEADERS = mapOf(
            "Accept" to "application/json",
            "User-Agent" to "ScrollSloth-rules",
        )
        private const val TIMEOUT_MS = 15_000
        private const val MAX_BYTES = 256 * 1024
    }
}
