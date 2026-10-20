package com.halfbloodcoder.scrollsloth.debug

import com.halfbloodcoder.scrollsloth.detect.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotSerializerTest {
    private val serializer = SnapshotSerializer()

    private val secretText = "watch my secret holiday reel"
    private val secretDescription = "Profile picture of alice_private"

    private val tree = FakeNode(
        viewIdResourceName = "com.example:id/root",
        children = listOf(
            FakeNode(
                className = "androidx.recyclerview.widget.RecyclerView",
                viewIdResourceName = "com.example:id/pager",
                isScrollable = true,
                collectionInfo = CollectionInfoSnapshot(rowCount = 12, columnCount = 1, hierarchical = false),
                children = listOf(
                    FakeNode(
                        className = "android.widget.TextView",
                        viewIdResourceName = "com.example:id/caption",
                        text = secretText,
                        contentDescription = secretDescription,
                        bounds = NodeBounds(10, 20, 30, 40),
                        collectionItemInfo = CollectionItemInfoSnapshot(3, 1, 0, 1, selected = true),
                    ),
                ),
            ),
            FakeNode(
                className = "android.widget.SeekBar",
                viewIdResourceName = "com.example:id/caption",
                rangeInfo = RangeInfoSnapshot(type = 0, min = 0f, max = 100f, current = 42f),
            ),
        ),
    )

    @Test
    fun jsonNeverContainsTextOrContentDescription() {
        val json = serializer.encode(capture(serializer.snapshot(tree)))

        assertFalse(json.contains(secretText))
        assertFalse(json.contains(secretDescription))
        assertFalse(json.contains("secret"))
        assertFalse(json.contains("alice"))
    }

    @Test
    fun recordsPresenceAndLengthOnly() {
        val root = serializer.snapshot(tree).root
        val caption = root.children[0].children[0]

        assertTrue(caption.hasText)
        assertEquals(secretText.length, caption.textLength)
        assertTrue(caption.hasContentDescription)
        assertEquals(secretDescription.length, caption.contentDescriptionLength)
        assertFalse(root.hasText)
        assertEquals(0, root.textLength)
        assertFalse(root.hasContentDescription)
    }

    @Test
    fun emptyTextCountsAsAbsent() {
        val node = serializer.snapshot(FakeNode(text = "", contentDescription = "")).root
        assertFalse(node.hasText)
        assertFalse(node.hasContentDescription)
    }

    @Test
    fun keepsStructureFieldsAndPaths() {
        val result = serializer.snapshot(tree)
        val pager = result.root.children[0]
        val caption = pager.children[0]
        val seekBar = result.root.children[1]

        assertEquals("0", result.root.path)
        assertEquals("0.0", pager.path)
        assertEquals("0.0.0", caption.path)
        assertEquals("0.1", seekBar.path)
        assertEquals("androidx.recyclerview.widget.RecyclerView", pager.className)
        assertEquals("com.example:id/pager", pager.viewId)
        assertTrue(pager.scrollable)
        assertEquals(CollectionInfoSnapshot(12, 1, false), pager.collectionInfo)
        assertEquals(CollectionItemInfoSnapshot(3, 1, 0, 1, true), caption.collectionItemInfo)
        assertEquals(RangeInfoSnapshot(0, 0f, 100f, 42f), seekBar.rangeInfo)
        assertEquals(NodeBounds(10, 20, 30, 40), caption.bounds)
        assertEquals(2, result.root.childCount)
        assertEquals(4, result.nodeCount)
        assertFalse(result.truncated)
    }

    @Test
    fun viewIdsAreSortedAndDistinct() {
        assertEquals(
            listOf("com.example:id/caption", "com.example:id/pager", "com.example:id/root"),
            serializer.snapshot(tree).viewIds,
        )
    }

    @Test
    fun emptyViewIdIsTreatedAsMissing() {
        val result = serializer.snapshot(FakeNode(viewIdResourceName = ""))
        assertNull(result.root.viewId)
        assertTrue(result.viewIds.isEmpty())
    }

    @Test
    fun nullChildrenAreSkippedButCounted() {
        val result = serializer.snapshot(FakeNode(children = listOf(null, FakeNode())))

        assertEquals(2, result.root.childCount)
        assertEquals(listOf("0.1"), result.root.children.map { it.path })
        assertEquals(2, result.nodeCount)
    }

    @Test
    fun stopsAtMaxNodes() {
        val wide = FakeNode(children = List(10) { FakeNode() })
        val result = SnapshotSerializer(maxNodes = 4).snapshot(wide)

        assertEquals(4, result.nodeCount)
        assertEquals(3, result.root.children.size)
        assertEquals(10, result.root.childCount)
        assertTrue(result.truncated)
    }

    @Test
    fun stopsAtMaxDepth() {
        val result = SnapshotSerializer(maxDepth = 2).snapshot(chain(5))

        assertEquals(3, result.nodeCount)
        assertTrue(result.truncated)
        val deepest = result.root.children[0].children[0]
        assertEquals(1, deepest.childCount)
        assertTrue(deepest.children.isEmpty())
    }

    @Test
    fun exactDepthLimitIsNotTruncated() {
        val result = SnapshotSerializer(maxDepth = 2).snapshot(chain(2))
        assertEquals(3, result.nodeCount)
        assertFalse(result.truncated)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val original = capture(serializer.snapshot(tree))
        assertEquals(original, serializer.decode(serializer.encode(original)))
    }

    @Test
    fun jsonOmitsNullFieldsAndKeepsSchemaVersion() {
        val json = serializer.encode(capture(serializer.snapshot(FakeNode())))
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertFalse(json.contains("null"))
        assertTrue(json.contains("\"platform\": \"YOUTUBE\""))
    }

    private fun capture(result: SnapshotSerializer.Result) = InspectorCapture(
        capturedAtEpochMs = 1_700_000_000_000,
        capturedAtUptimeMs = 5_000,
        platform = Platform.YOUTUBE,
        app = TargetAppInfo("com.google.android.youtube", "19.0", 1900L),
        device = DeviceInfo(sdkInt = 36, screenWidthPx = 1080, screenHeightPx = 2400),
        nodeCount = result.nodeCount,
        truncated = result.truncated,
        viewIds = result.viewIds,
        recentEvents = listOf(
            EventRecord.create(10, "TYPE_VIEW_SCROLLED", "com.google.android.youtube", "x.Pager", 0, 300, 2, 3, 9),
        ),
        root = result.root,
    )
}
