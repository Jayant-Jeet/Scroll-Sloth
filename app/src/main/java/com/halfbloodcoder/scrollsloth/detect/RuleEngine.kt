package com.halfbloodcoder.scrollsloth.detect

import com.halfbloodcoder.scrollsloth.debug.InspectableNode
import com.halfbloodcoder.scrollsloth.debug.NodeBounds

/**
 * Turns one window tree of a supported app into a [DetectorEvent] using the loaded rules. Kept
 * thin: all timing and counting decisions live in [ReelStateMachine].
 *
 * Only visible nodes are considered: subtrees with no on-screen area are skipped, which also keeps
 * hidden fragments (such as a feed behind the reel viewer) from matching. Text and descriptions
 * are read only to hash them, never kept.
 */
class RuleEngine(
    private val loaded: LoadedRules,
    private val maxNodes: Int = DEFAULT_MAX_NODES,
) {
    fun evaluate(timeMs: Long, packageName: String, root: InspectableNode?): DetectorEvent {
        val notReel = DetectorEvent(timeMs, packageName, onReelScreen = false)
        val rule = loaded.enabledApps[packageName] ?: return notReel
        if (root == null) return notReel
        val screen = root.bounds
        if (screen.width <= 0 || screen.height <= 0) return notReel

        val matched = BooleanArray(rule.reelScreenMatchers.size)
        var pager: InspectableNode? = null
        var pagerVisible: NodeBounds? = null
        val stack = ArrayDeque<InspectableNode>().apply { add(root) }
        var visited = 0
        while (stack.isNotEmpty() && visited < maxNodes) {
            val node = stack.removeLast()
            visited++
            val visible = node.bounds.intersect(screen) ?: continue
            rule.reelScreenMatchers.forEachIndexed { i, matcher ->
                if (!matched[i] && matcher.matches(node, visible, screen)) matched[i] = true
            }
            if (pager == null && rule.pagerMatcher?.matches(node, visible, screen) == true) {
                pager = node
                pagerVisible = visible
            }
            if (pager != null && matched.all { it }) break
            for (i in node.childCount - 1 downTo 0) node.child(i)?.let(stack::addLast)
        }

        if (!matched.all { it }) return notReel
        val page = pager?.let { mostVisibleChild(it, pagerVisible!!) ?: it }
        return when (rule.strategy) {
            DetectionStrategy.ITEM_CHANGED -> DetectorEvent(
                timeMs, packageName, onReelScreen = true,
                pagerIndex = page?.collectionItemInfo?.rowIndex,
            )
            DetectionStrategy.KEY_CHANGED -> DetectorEvent(
                timeMs, packageName, onReelScreen = true,
                reelKeyHash = page?.let {
                    keyHash(it, rule.keyFields.mapNotNull(KeyField::fromJsonName), rule.keyMatchers, screen)
                },
            )
        }
    }

    private fun mostVisibleChild(pager: InspectableNode, pagerVisible: NodeBounds): InspectableNode? {
        var best: InspectableNode? = null
        var bestArea = 0L
        for (i in 0 until pager.childCount) {
            val child = pager.child(i) ?: continue
            val area = child.bounds.intersect(pagerVisible)?.area ?: continue
            if (area > bestArea) {
                best = child
                bestArea = area
            }
        }
        return best
    }

    /**
     * Order-sensitive hash of the key fields in [page]'s subtree, or null if none are present.
     * With [keyMatchers], only visible nodes matching one of them contribute.
     */
    private fun keyHash(
        page: InspectableNode,
        fields: List<KeyField>,
        keyMatchers: List<NodeMatcher>,
        screen: NodeBounds,
    ): Int? {
        var hash = 17
        var values = 0
        val stack = ArrayDeque<InspectableNode>().apply { add(page) }
        var visited = 0
        while (stack.isNotEmpty() && visited < maxNodes) {
            val node = stack.removeLast()
            visited++
            for (i in node.childCount - 1 downTo 0) node.child(i)?.let(stack::addLast)
            if (keyMatchers.isNotEmpty()) {
                val visible = node.bounds.intersect(screen) ?: continue
                if (keyMatchers.none { it.matches(node, visible, screen) }) continue
            }
            for (field in fields) {
                val value = when (field) {
                    KeyField.TEXT -> node.text
                    KeyField.CONTENT_DESCRIPTION -> node.contentDescription
                }
                if (!value.isNullOrEmpty()) {
                    hash = 31 * (31 * hash + field.ordinal) + value.toString().hashCode()
                    values++
                }
            }
        }
        return if (values == 0) null else hash
    }

    companion object {
        const val DEFAULT_MAX_NODES = 2_500
    }
}

internal fun NodeMatcher.matches(node: InspectableNode, visible: NodeBounds, screen: NodeBounds): Boolean {
    viewIdContains?.let { if (node.viewIdResourceName?.contains(it) != true) return false }
    classNameEquals?.let { if (node.className?.toString() != it) return false }
    scrollable?.let { if (node.isScrollable != it) return false }
    minWidthFraction?.let { if (visible.width < it * screen.width) return false }
    minHeightFraction?.let { if (visible.height < it * screen.height) return false }
    minTopFraction?.let { if (visible.top - screen.top < it * screen.height) return false }
    maxRightFraction?.let { if (visible.right - screen.left > it * screen.width) return false }
    return true
}

private val NodeBounds.width: Int get() = right - left
private val NodeBounds.height: Int get() = bottom - top
private val NodeBounds.area: Long get() = width.toLong() * height

private fun NodeBounds.intersect(other: NodeBounds): NodeBounds? {
    val l = maxOf(left, other.left)
    val t = maxOf(top, other.top)
    val r = minOf(right, other.right)
    val b = minOf(bottom, other.bottom)
    return if (r > l && b > t) NodeBounds(l, t, r, b) else null
}
