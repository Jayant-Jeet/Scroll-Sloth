package com.halfbloodcoder.scrollsloth.detect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Validate rules" CI check: the published rules file must pass [PublishedRulesCheck]. */
class PublishedRulesTest {
    @Test
    fun siteRulesArePublishable() {
        val bundled = DetectionRulesLoader.parse(File("src/main/assets/detection-rules.json").readText())
        val problems = PublishedRulesCheck.problems(File("../site/detection-rules.json").readText(), bundled.rulesVersion)

        assertEquals("site/detection-rules.json:\n" + problems.joinToString("\n"), emptyList<String>(), problems)
    }

    @Test
    fun siteRulesEnableEveryApp() {
        val loaded = DetectionRulesLoader.load(File("../site/detection-rules.json").readText(), RulesSource.DOWNLOADED)

        assertTrue(loaded.statuses.toString(), loaded.statuses.values.all { it.state == RuleState.READY })
    }
}