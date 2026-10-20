package com.reelsloth.detect

import com.reelsloth.debug.CollectionInfoSnapshot
import com.reelsloth.debug.CollectionItemInfoSnapshot
import com.reelsloth.debug.InspectableNode
import com.reelsloth.debug.NodeBounds
import com.reelsloth.debug.NodeSnapshot
import com.reelsloth.debug.RangeInfoSnapshot
import com.reelsloth.debug.SnapshotSerializer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Runs the calibrated rules in `rules/detection-rules.json` over the real Rule Inspector captures
 * they were derived from. Captures hold no text, so key fields are replaced by placeholders of
 * the recorded length; that is enough to check a key can be built.
 */
class RuleEngineCaptureTest {
    private class SnapshotNode(private val snapshot: NodeSnapshot) : InspectableNode {
        override val className: CharSequence? get() = snapshot.className
        override val viewIdResourceName: String? get() = snapshot.viewId
        override val text: CharSequence? get() = if (snapshot.hasText) "#".repeat(snapshot.textLength) else null
        override val contentDescription: CharSequence?
            get() = if (snapshot.hasContentDescription) "#".repeat(snapshot.contentDescriptionLength) else null
        override val isScrollable: Boolean get() = snapshot.scrollable
        override val bounds: NodeBounds get() = snapshot.bounds
        override val collectionInfo: CollectionInfoSnapshot? get() = snapshot.collectionInfo
        override val collectionItemInfo: CollectionItemInfoSnapshot? get() = snapshot.collectionItemInfo
        override val rangeInfo: RangeInfoSnapshot? get() = snapshot.rangeInfo
        override val childCount: Int get() = snapshot.children.size
        override fun child(index: Int): InspectableNode = SnapshotNode(snapshot.children[index])
    }

    private val engine = RuleEngine(
        DetectionRulesLoader.load(File("../rules/detection-rules.json").readText(), RulesSource.OVERRIDE),
    )

    private fun evaluate(fileName: String): DetectorEvent {
        val capture = SnapshotSerializer().decode(File("../captures/2026-10-20-sm-s928b/$fileName").readText())
        return engine.evaluate(capture.capturedAtEpochMs, capture.app.packageName, SnapshotNode(capture.root))
    }

    @Test
    fun instagramFeedIsNotAReelScreenAndReelsReportPagerIndex() {
        assertEquals(false, evaluate("capture-instagram-1792508840565.json").onReelScreen)
        assertEquals(0, evaluate("capture-instagram-1792508856246.json").pagerIndex)
        assertEquals(1, evaluate("capture-instagram-1792508878218.json").pagerIndex)
    }

    @Test
    fun youtubeHomeIsNotAReelScreenAndShortsReportPagerIndex() {
        assertEquals(false, evaluate("capture-youtube-1792508899404.json").onReelScreen)
        assertEquals(0, evaluate("capture-youtube-1792509127043.json").pagerIndex)
        assertEquals(1, evaluate("capture-youtube-1792509138136.json").pagerIndex)
    }

    @Test
    fun facebookReelsProduceAKeyThatSurvivesPlayerControls() {
        assertEquals(false, evaluate("capture-facebook-1792508945803.json").onReelScreen)
        listOf("capture-facebook-1792509004029.json", "capture-facebook-1792509051761.json").forEach { file ->
            val event = evaluate(file)
            assertEquals(file, true, event.onReelScreen)
            assertNotNull(file, event.reelKeyHash)
        }

        // One reel captured playing, paused (four extra control buttons, layout shifted up), then playing.
        val playing = evaluate("capture-facebook-1792512011980.json").reelKeyHash
        assertNotNull(playing)
        assertEquals(playing, evaluate("capture-facebook-1792512020946.json").reelKeyHash)
        assertEquals(playing, evaluate("capture-facebook-1792512034270.json").reelKeyHash)
        assertNotEquals(playing, evaluate("capture-facebook-1792509004029.json").reelKeyHash)
    }
}
