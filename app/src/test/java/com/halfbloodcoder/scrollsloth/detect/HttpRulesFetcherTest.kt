package com.halfbloodcoder.scrollsloth.detect

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.HttpURLConnection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Runs the real fetcher against a one-shot local socket and checks exactly what it sent. */
class HttpRulesFetcherTest {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val url = "https://127.0.0.1:${server.localPort}/detection-rules.json"

    private fun fetcher(maxBytes: Int = 256 * 1024) = HttpRulesFetcher(maxBytes) {
        URL(it.toString().replaceFirst("https:", "http:")).openConnection() as HttpURLConnection
    }

    @After
    fun closeServer() = server.close()

    /** Answers one request with [response] and returns the request head (request line + headers). */
    private fun serveOnce(response: String): CompletableFuture<List<String>> {
        val request = CompletableFuture<List<String>>()
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                request.complete(generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList())
                socket.getOutputStream().apply {
                    write(response.toByteArray())
                    flush()
                }
            }
        }
        return request
    }

    private fun ok(body: String) =
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"

    @Test
    fun sendsOnlyFixedNonIdentifyingHeaders() = runTest {
        val request = serveOnce(ok("""{"rulesVersion": 1}"""))

        val body = fetcher().fetch(url)

        assertEquals("""{"rulesVersion": 1}""", body.toString(Charsets.UTF_8))
        val lines = request.get(5, TimeUnit.SECONDS)
        assertEquals("GET /detection-rules.json HTTP/1.1", lines.first())
        val headers = lines.drop(1).associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
        assertEquals("ScrollSloth-rules", headers["user-agent"])
        assertEquals("application/json", headers["accept"])
        assertFalse("cookie" in headers)
        assertFalse("authorization" in headers)
        // Anything else may only be connection plumbing, never something about the phone or user.
        val allowed = setOf("user-agent", "accept", "host", "connection", "accept-encoding", "cache-control", "pragma")
        assertTrue("Unexpected headers: ${headers.keys - allowed}", (headers.keys - allowed).isEmpty())
    }

    @Test
    fun redirectsAreNotFollowed() = runTest {
        serveOnce("HTTP/1.1 302 Found\r\nLocation: https://elsewhere.example/rules.json\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")

        try {
            fetcher().fetch(url)
            fail("A redirect must not be followed")
        } catch (e: IOException) {
            assertEquals("HTTP 302", e.message)
        }
    }

    @Test
    fun oversizedResponseIsRefused() = runTest {
        serveOnce(ok("x".repeat(2_000)))

        try {
            fetcher(maxBytes = 1_000).fetch(url)
            fail("Expected the size cap to apply")
        } catch (e: IOException) {
            assertEquals("Rules file too large", e.message)
        }

        @Test
        fun signatureHasItsOwnSmallLimit() = runTest {
            serveOnce(ok("x".repeat(1_025)))
            try {
                fetcher().fetch("$url.sig")
                fail("Expected signature size cap")
            } catch (e: IOException) {
                assertEquals("Rules file too large", e.message)
            }
        }

        @Test
        fun refusesPlainHttpBeforeConnecting() = runTest {
            try {
                HttpRulesFetcher().fetch(url.replaceFirst("https:", "http:"))
                fail("Expected HTTPS enforcement")
            } catch (e: IOException) {
                assertEquals("The rules address must use https.", e.message)
            }
        }
    }
}
