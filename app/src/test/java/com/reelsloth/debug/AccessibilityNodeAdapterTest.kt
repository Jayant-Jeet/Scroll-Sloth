package com.reelsloth.debug

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccessibilityNodeAdapterTest {
    @Suppress("DEPRECATION")
    @Test
    fun mapsNodeFieldsWithoutKeepingText() {
        val node = AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.TextView"
            viewIdResourceName = "com.example:id/title"
            text = "private words"
            contentDescription = "desc"
            isScrollable = true
            setBoundsInScreen(Rect(1, 2, 3, 4))
            collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(5, 1, false)
            rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, 10f, 4f,
            )
        }

        val snapshot = SnapshotSerializer().snapshot(AccessibilityNodeAdapter(node)).root

        assertEquals("android.widget.TextView", snapshot.className)
        assertEquals("com.example:id/title", snapshot.viewId)
        assertEquals(13, snapshot.textLength)
        assertEquals(4, snapshot.contentDescriptionLength)
        assertTrue(snapshot.scrollable)
        assertEquals(NodeBounds(1, 2, 3, 4), snapshot.bounds)
        assertEquals(CollectionInfoSnapshot(5, 1, false), snapshot.collectionInfo)
        assertEquals(
            RangeInfoSnapshot(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, 10f, 4f),
            snapshot.rangeInfo,
        )
    }
}
