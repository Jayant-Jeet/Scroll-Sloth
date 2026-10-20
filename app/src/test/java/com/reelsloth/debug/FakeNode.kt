package com.reelsloth.debug

data class FakeNode(
    override val className: CharSequence? = "android.widget.FrameLayout",
    override val viewIdResourceName: String? = null,
    override val text: CharSequence? = null,
    override val contentDescription: CharSequence? = null,
    override val isScrollable: Boolean = false,
    override val bounds: NodeBounds = NodeBounds(0, 0, 100, 100),
    override val collectionInfo: CollectionInfoSnapshot? = null,
    override val collectionItemInfo: CollectionItemInfoSnapshot? = null,
    override val rangeInfo: RangeInfoSnapshot? = null,
    val children: List<FakeNode?> = emptyList(),
) : InspectableNode {
    override val childCount: Int get() = children.size

    override fun child(index: Int): InspectableNode? = children[index]
}

/** A straight chain of [depth] + 1 nodes. */
fun chain(depth: Int): FakeNode =
    if (depth == 0) FakeNode() else FakeNode(children = listOf(chain(depth - 1)))
