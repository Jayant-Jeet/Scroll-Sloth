package com.halfbloodcoder.scrollsloth.detect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionRulesLoaderTest {
    private val igPager = NodeMatcher(viewIdContains = "pager")

    private fun rulesWith(vararg apps: Pair<String, AppRule>) = DetectionRules(
        schemaVersion = 1,
        rulesVersion = 3,
        apps = mapOf(*apps),
    )

    private fun igRule(
        matchers: List<NodeMatcher> = listOf(igPager),
        strategy: DetectionStrategy = DetectionStrategy.ITEM_CHANGED,
        pager: NodeMatcher? = igPager,
        keyFields: List<String> = emptyList(),
        platform: Platform = Platform.INSTAGRAM,
        keyMatchers: List<NodeMatcher> = emptyList(),
    ) = Platform.INSTAGRAM.packageName to AppRule(platform, matchers, strategy, pager, keyFields, keyMatchers)

    private fun statusOf(rules: DetectionRules, platform: Platform = Platform.INSTAGRAM) =
        DetectionRulesLoader.validate(rules, RulesSource.OVERRIDE).statuses.getValue(platform)

    @Test
    fun bundledAssetHasEveryAppNeedingCalibration() {
        val loaded = DetectionRulesLoader.load(File("src/main/assets/detection-rules.json").readText(), RulesSource.BUNDLED)

        assertEquals(Platform.entries.toSet(), loaded.statuses.keys)
        assertTrue(loaded.statuses.values.all { it.state == RuleState.NEEDS_CALIBRATION })
        assertTrue(loaded.enabledApps.isEmpty())
        assertEquals(2_000, loaded.rules.minDwellMs)
        assertEquals(120_000, loaded.rules.maxDwellMs)
    }

    @Test
    fun calibratedRulesEnableAllThreeApps() {
        val loaded = DetectionRulesLoader.load(File("../site/detection-rules.json").readText(), RulesSource.OVERRIDE)

        assertTrue(loaded.statuses.values.all { it.state == RuleState.READY })
        assertEquals(Platform.entries.map { it.packageName }.toSet(), loaded.enabledApps.keys)
    }

    @Test
    fun todoMarkerDisablesOnlyThatApp() {
        val todo = NodeMatcher(viewIdContains = DetectionRules.TODO_MARKER)
        val fb = Platform.FACEBOOK.packageName to AppRule(
            Platform.FACEBOOK,
            listOf(NodeMatcher(classNameEquals = "Pager")),
            DetectionStrategy.KEY_CHANGED,
            NodeMatcher(classNameEquals = "Pager"),
            listOf("contentDescription"),
        )
        val loaded = DetectionRulesLoader.validate(rulesWith(igRule(pager = todo), fb), RulesSource.OVERRIDE)

        assertEquals(RuleState.NEEDS_CALIBRATION, loaded.statuses.getValue(Platform.INSTAGRAM).state)
        assertEquals(RuleState.READY, loaded.statuses.getValue(Platform.FACEBOOK).state)
        assertEquals(setOf(Platform.FACEBOOK.packageName), loaded.enabledApps.keys)
    }

    @Test
    fun missingAppNeedsCalibration() {
        val status = statusOf(rulesWith(igRule()), Platform.YOUTUBE)
        assertEquals(RuleState.NEEDS_CALIBRATION, status.state)
    }

    @Test
    fun incompleteRulesNeedCalibration() {
        val cases = listOf(
            igRule(matchers = emptyList()),
            igRule(matchers = listOf(NodeMatcher())),
            igRule(pager = null),
            igRule(strategy = DetectionStrategy.KEY_CHANGED, keyFields = emptyList()),
            igRule(strategy = DetectionStrategy.KEY_CHANGED, keyFields = listOf("caption")),
            igRule(strategy = DetectionStrategy.KEY_CHANGED, keyFields = listOf(DetectionRules.TODO_MARKER)),
            igRule(platform = Platform.YOUTUBE),
            igRule(
                strategy = DetectionStrategy.KEY_CHANGED, keyFields = listOf("text"),
                keyMatchers = listOf(NodeMatcher(classNameEquals = DetectionRules.TODO_MARKER)),
            ),
            igRule(strategy = DetectionStrategy.KEY_CHANGED, keyFields = listOf("text"), keyMatchers = listOf(NodeMatcher())),
        )
        cases.forEach { case ->
            assertEquals(case.toString(), RuleState.NEEDS_CALIBRATION, statusOf(rulesWith(case)).state)
        }
    }

    @Test
    fun completeRuleIsReady() {
        val status = statusOf(rulesWith(igRule()))
        assertEquals(RuleState.READY, status.state)
        assertNull(status.reason)
    }

    @Test
    fun keyRuleWithPositionalKeyMatcherIsReady() {
        val rule = igRule(
            strategy = DetectionStrategy.KEY_CHANGED,
            keyFields = listOf("contentDescription"),
            keyMatchers = listOf(NodeMatcher(minTopFraction = 0.7f, maxRightFraction = 0.85f)),
        )
        assertEquals(RuleState.READY, statusOf(rulesWith(rule)).state)
    }

    @Test(expected = RulesFormatException::class)
    fun unsupportedSchemaVersionIsRejected() {
        DetectionRulesLoader.parse("""{"schemaVersion": 2, "rulesVersion": 1, "apps": {}}""")
    }

    @Test(expected = RulesFormatException::class)
    fun malformedJsonIsRejected() {
        DetectionRulesLoader.parse("""{"schemaVersion": 1""")
    }

    @Test(expected = RulesFormatException::class)
    fun impossibleDwellRangeIsRejected() {
        DetectionRulesLoader.parse("""{"schemaVersion": 1, "rulesVersion": 1, "minDwellMs": 5000, "maxDwellMs": 1000}""")
    }

    @Test
    fun unknownFieldsAreIgnoredForForwardCompatibility() {
        val rules = DetectionRulesLoader.parse(
            """{"schemaVersion": 1, "rulesVersion": 9, "notes": "x", "apps": {}}""",
        )
        assertEquals(9, rules.rulesVersion)
    }
}
