package com.reelsloth.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishedRulesCheckTest {
    private val ig = """
        "com.instagram.android": {
          "platform": "INSTAGRAM",
          "reelScreenMatchers": [ { "viewIdContains": "reel_pager" } ],
          "strategy": "ITEM_CHANGED",
          "pagerMatcher": { "viewIdContains": "reel_pager" }
        }
    """
    private val ytTodo = """
        "com.google.android.youtube": {
          "platform": "YOUTUBE",
          "reelScreenMatchers": [ { "viewIdContains": "TODO_FROM_INSPECTOR" } ],
          "strategy": "ITEM_CHANGED",
          "pagerMatcher": { "viewIdContains": "TODO_FROM_INSPECTOR" }
        }
    """

    private fun file(vararg apps: String, version: Int = 5, schema: Int = 1) =
        """{ "schemaVersion": $schema, "rulesVersion": $version, "apps": { ${apps.joinToString(",")} } }"""

    private fun problems(text: String) = PublishedRulesCheck.problems(text, bundledRulesVersion = 1)

    private fun assertFailsWith(fragment: String, text: String) {
        val problems = problems(text)
        assertTrue("expected a problem containing \"$fragment\", got $problems", problems.any { fragment in it })
    }

    @Test
    fun calibratedFilePasses() = assertEquals(emptyList<String>(), problems(file(ig)))

    @Test
    fun placeholderForOneAppIsAllowed() = assertEquals(emptyList<String>(), problems(file(ig, ytTodo)))

    @Test
    fun allPlaceholdersFail() = assertFailsWith("No app is calibrated", file(ytTodo))

    @Test
    fun notJsonFails() = assertFailsWith("Not a valid rules file", "{ nope")

    @Test
    fun missingRequiredFieldFails() = assertFailsWith("Not a valid rules file", """{ "schemaVersion": 1, "apps": {} }""")

    @Test
    fun misspeltKeyFails() =
        assertFailsWith("Not a valid rules file", file(ig.replace("\"pagerMatcher\": { \"viewIdContains\"", "\"pagerMatcher\": { \"viewIdContain\"")))

    @Test
    fun unknownPlatformValueFails() = assertFailsWith("Not a valid rules file", file(ig.replace("INSTAGRAM", "TIKTOK")))

    @Test
    fun unsupportedSchemaFails() = assertFailsWith("schema 2 is not supported", file(ig, schema = 2))

    @Test
    fun badDwellRangeFails() = assertFailsWith(
        "Dwell range",
        """{ "schemaVersion": 1, "rulesVersion": 5, "minDwellMs": 5000, "maxDwellMs": 1000, "apps": { $ig } }""",
    )

    @Test
    fun versionNotAboveBundledFails() = assertFailsWith("must be higher than the bundled", file(ig, version = 1))

    @Test
    fun unknownPackageFails() = assertFailsWith("Unknown app \"com.example.reels\"", file(ig, ig.replace("com.instagram.android", "com.example.reels")))

    @Test
    fun platformFiledUnderWrongAppFails() =
        assertFailsWith("filed under Instagram", file(ig.replace("\"platform\": \"INSTAGRAM\"", "\"platform\": \"FACEBOOK\"")))

    @Test
    fun emptyMatcherFails() = assertFailsWith("no conditions", file(ig.replace("{ \"viewIdContains\": \"reel_pager\" } ]", "{ } ]")))
}