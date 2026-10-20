package com.reelsloth.debug

import kotlinx.serialization.json.Json

/**
 * Turns an accessibility node tree into a privacy-safe [NodeSnapshot] tree and JSON.
 * Only structure is kept: text and content descriptions become presence flags and lengths.
 */
class SnapshotSerializer(
    private val maxNodes: Int = DEFAULT_MAX_NODES,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {
    data class Result(
        val root: NodeSnapshot,
        val nodeCount: Int,
        val truncated: Boolean,
        val viewIds: List<String>,
    )

    private class TraversalState {
        var nodeCount = 0
        var truncated = false
        val viewIds = sortedSetOf<String>()
    }

    fun snapshot(root: InspectableNode): Result {
        val state = TraversalState()
        val snapshot = snapshotNode(root, path = "0", depth = 0, state = state)
        return Result(snapshot, state.nodeCount, state.truncated, state.viewIds.toList())
    }

    fun encode(capture: InspectorCapture): String = json.encodeToString(InspectorCapture.serializer(), capture)

    fun decode(content: String): InspectorCapture = json.decodeFromString(InspectorCapture.serializer(), content)

    private fun snapshotNode(
        node: InspectableNode,
        path: String,
        depth: Int,
        state: TraversalState,
    ): NodeSnapshot {
        state.nodeCount++
        node.viewIdResourceName?.takeIf { it.isNotEmpty() }?.let(state.viewIds::add)

        val childCount = node.childCount
        val children = mutableListOf<NodeSnapshot>()
        if (childCount > 0 && depth >= maxDepth) {
            state.truncated = true
        } else {
            for (index in 0 until childCount) {
                if (state.nodeCount >= maxNodes) {
                    state.truncated = true
                    break
                }
                val child = node.child(index) ?: continue
                children += snapshotNode(child, "$path.$index", depth + 1, state)
            }
        }

        val textLength = node.text?.length ?: 0
        val contentDescriptionLength = node.contentDescription?.length ?: 0
        return NodeSnapshot(
            path = path,
            className = node.className?.toString(),
            viewId = node.viewIdResourceName?.takeIf { it.isNotEmpty() },
            hasContentDescription = contentDescriptionLength > 0,
            contentDescriptionLength = contentDescriptionLength,
            hasText = textLength > 0,
            textLength = textLength,
            scrollable = node.isScrollable,
            bounds = node.bounds,
            collectionInfo = node.collectionInfo,
            collectionItemInfo = node.collectionItemInfo,
            rangeInfo = node.rangeInfo,
            childCount = childCount,
            children = children,
        )
    }

    companion object {
        const val DEFAULT_MAX_NODES = 3_000
        const val DEFAULT_MAX_DEPTH = 80

        private val json = Json {
            prettyPrint = true
            explicitNulls = false
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
