package com.halfbloodcoder.scrollsloth.detect

import com.halfbloodcoder.scrollsloth.debug.CollectionItemInfoSnapshot
import com.halfbloodcoder.scrollsloth.debug.FakeNode
import com.halfbloodcoder.scrollsloth.debug.NodeBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    private val pagerMatcher = NodeMatcher(viewIdContains = "id/pager", minWidthFraction = 0.9f, minHeightFraction = 0.5f)
    private val screen = NodeBounds(0, 0, 1000, 2000)

    private fun loaded(strategy: DetectionStrategy, keyFields: List<String> = emptyList()): LoadedRules {
        val rule = AppRule(Platform.INSTAGRAM, listOf(pagerMatcher), strategy, pagerMatcher, keyFields)
        return DetectionRulesLoader.validate(
            DetectionRules(1, 1, apps = mapOf(Platform.INSTAGRAM.packageName to rule)),
            RulesSource.OVERRIDE,
        )
    }

    private fun page(rowIndex: Int?, top: Int, bottom: Int, desc: String? = null, text: String? = null) = FakeNode(
        bounds = NodeBounds(0, top, 1000, bottom),
        collectionItemInfo = rowIndex?.let { CollectionItemInfoSnapshot(it, 1, 0, 1, false) },
        children = listOf(
            FakeNode(bounds = NodeBounds(0, top, 500, bottom), contentDescription = desc, text = text),
        ),
    )

    private fun tree(vararg pages: FakeNode, pagerBounds: NodeBounds = screen, tabDesc: String = "Home") = FakeNode(
        bounds = screen,
        children = listOf(
            FakeNode(viewIdResourceName = "app:id/pager", isScrollable = true, bounds = pagerBounds, children = pages.toList()),
            FakeNode(bounds = NodeBounds(0, 1900, 1000, 2000), contentDescription = tabDesc),
        ),
    )

    private fun RuleEngine.at(root: FakeNode?, pkg: String = Platform.INSTAGRAM.packageName) =
        evaluate(timeMs = 123, packageName = pkg, root = root)

    @Test
    fun itemStrategyReportsIndexOfMostVisiblePage() {
        val engine = RuleEngine(loaded(DetectionStrategy.ITEM_CHANGED))
        val event = engine.at(tree(page(3, -1200, 800), page(4, 800, 2800)))

        assertEquals(DetectorEvent(123, Platform.INSTAGRAM.packageName, true, null, 4), event)
    }

    @Test
    fun hiddenPagerIsNotAReelScreen() {
        val engine = RuleEngine(loaded(DetectionStrategy.ITEM_CHANGED))
        val event = engine.at(tree(page(0, 0, 2000), pagerBounds = NodeBounds(1000, 0, 1000, 2000)))

        assertFalse(event.onReelScreen)
        assertNull(event.pagerIndex)
    }

    @Test
    fun keyStrategyHashesOnlyTheCurrentPage() {
        val engine = RuleEngine(loaded(DetectionStrategy.KEY_CHANGED, listOf("contentDescription")))

        val a = engine.at(tree(page(null, 0, 2000, desc = "reel a"), tabDesc = "Home"))
        val sameReelOtherTab = engine.at(tree(page(null, 0, 2000, desc = "reel a"), tabDesc = "Home, 3 new"))
        val b = engine.at(tree(page(null, 0, 2000, desc = "reel b")))

        assertTrue(a.onReelScreen)
        assertNotNull(a.reelKeyHash)
        assertEquals(a.reelKeyHash, sameReelOtherTab.reelKeyHash)
        assertNotEquals(a.reelKeyHash, b.reelKeyHash)
        assertNull(a.pagerIndex)
    }

    @Test
    fun keyStrategyUsesOnlyConfiguredFields() {
        val engine = RuleEngine(loaded(DetectionStrategy.KEY_CHANGED, listOf("contentDescription")))

        val a = engine.at(tree(page(null, 0, 2000, desc = "reel", text = "12 likes")))
        val b = engine.at(tree(page(null, 0, 2000, desc = "reel", text = "13 likes")))

        assertEquals(a.reelKeyHash, b.reelKeyHash)
    }

    @Test
    fun keyIsNullWhenPageHasNoKeyFields() {
        val engine = RuleEngine(loaded(DetectionStrategy.KEY_CHANGED, listOf("contentDescription")))
        val event = engine.at(tree(page(null, 0, 2000)))

        assertTrue(event.onReelScreen)
        assertNull(event.reelKeyHash)
    }

    @Test
    fun disabledOrUnknownAppsAndMissingRootAreNeverReelScreens() {
        val bundled = DetectionRulesLoader.load(
            java.io.File("src/main/assets/detection-rules.json").readText(),
            RulesSource.BUNDLED,
        )
        val root = tree(page(0, 0, 2000))

        assertFalse(RuleEngine(bundled).at(root).onReelScreen)
        assertFalse(RuleEngine(loaded(DetectionStrategy.ITEM_CHANGED)).at(root, pkg = "com.example").onReelScreen)
        assertFalse(RuleEngine(loaded(DetectionStrategy.ITEM_CHANGED)).at(null).onReelScreen)
    }

    @Test
    fun keyMatchersLimitTheKeyToMatchingNodesOnThePage() {
        val captionBlock = NodeMatcher(minTopFraction = 0.7f, maxRightFraction = 0.85f)
        val rule = AppRule(
            Platform.FACEBOOK, listOf(pagerMatcher), DetectionStrategy.KEY_CHANGED, pagerMatcher,
            keyFields = listOf("contentDescription"), keyMatchers = listOf(captionBlock),
        )
        val engine = RuleEngine(
            DetectionRulesLoader.validate(
                DetectionRules(1, 1, apps = mapOf(Platform.FACEBOOK.packageName to rule)),
                RulesSource.OVERRIDE,
            ),
        )
        fun reel(caption: String, control: String?, likes: String) = FakeNode(
            bounds = screen,
            children = listOf(
                FakeNode(
                    viewIdResourceName = "app:id/pager", isScrollable = true, bounds = screen,
                    children = listOf(
                        FakeNode(
                            bounds = screen,
                            children = listOfNotNull(
                                FakeNode(bounds = NodeBounds(40, 1700, 800, 1760), contentDescription = caption),
                                control?.let { FakeNode(bounds = NodeBounds(400, 900, 600, 1100), contentDescription = it) },
                                FakeNode(bounds = NodeBounds(900, 1500, 1000, 1600), contentDescription = likes),
                            ),
                        ),
                    ),
                ),
            ),
        )
        fun key(root: FakeNode) = engine.evaluate(1, Platform.FACEBOOK.packageName, root).reelKeyHash

        val playing = key(reel("caption a", control = null, likes = "Like, 10"))

        assertNotNull(playing)
        assertEquals(playing, key(reel("caption a", control = "Play", likes = "Like, 10")))
        assertEquals(playing, key(reel("caption a", control = null, likes = "Like, 11")))
        assertNotEquals(playing, key(reel("caption b", control = null, likes = "Like, 10")))
    }

    @Test
    fun allScreenMatchersMustMatch() {
        val extra = NodeMatcher(classNameEquals = "com.example.Missing")
        val rule = AppRule(Platform.INSTAGRAM, listOf(pagerMatcher, extra), DetectionStrategy.ITEM_CHANGED, pagerMatcher)
        val engine = RuleEngine(
            DetectionRulesLoader.validate(
                DetectionRules(1, 1, apps = mapOf(Platform.INSTAGRAM.packageName to rule)),
                RulesSource.OVERRIDE,
            ),
        )

        assertFalse(engine.at(tree(page(0, 0, 2000))).onReelScreen)
    }
}
