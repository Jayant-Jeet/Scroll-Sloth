package com.halfbloodcoder.scrollsloth.debug

/**
 * Read-only view of one accessibility node. Text and content description are exposed only so
 * [SnapshotSerializer] can record whether they exist and their length; they are never stored.
 */
interface InspectableNode {
    val className: CharSequence?
    val viewIdResourceName: String?
    val text: CharSequence?
    val contentDescription: CharSequence?
    val isScrollable: Boolean
    val bounds: NodeBounds
    val collectionInfo: CollectionInfoSnapshot?
    val collectionItemInfo: CollectionItemInfoSnapshot?
    val rangeInfo: RangeInfoSnapshot?
    val childCount: Int

    fun child(index: Int): InspectableNode?
}
