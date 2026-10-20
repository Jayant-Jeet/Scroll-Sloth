package com.reelsloth.debug

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityNodeAdapter(private val node: AccessibilityNodeInfo) : InspectableNode {
    override val className: CharSequence? get() = node.className
    override val viewIdResourceName: String? get() = node.viewIdResourceName
    override val text: CharSequence? get() = node.text
    override val contentDescription: CharSequence? get() = node.contentDescription
    override val isScrollable: Boolean get() = node.isScrollable

    override val bounds: NodeBounds
        get() {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            return NodeBounds(rect.left, rect.top, rect.right, rect.bottom)
        }

    override val collectionInfo: CollectionInfoSnapshot?
        get() = node.collectionInfo?.let {
            CollectionInfoSnapshot(it.rowCount, it.columnCount, it.isHierarchical)
        }

    override val collectionItemInfo: CollectionItemInfoSnapshot?
        get() = node.collectionItemInfo?.let {
            CollectionItemInfoSnapshot(it.rowIndex, it.rowSpan, it.columnIndex, it.columnSpan, it.isSelected)
        }

    override val rangeInfo: RangeInfoSnapshot?
        get() = node.rangeInfo?.let { RangeInfoSnapshot(it.type, it.min, it.max, it.current) }

    override val childCount: Int get() = node.childCount

    override fun child(index: Int): InspectableNode? = node.getChild(index)?.let(::AccessibilityNodeAdapter)
}
