package com.halfbloodcoder.scrollsloth.debug

import com.halfbloodcoder.scrollsloth.detect.Platform
import kotlinx.serialization.Serializable

@Serializable
data class InspectorCapture(
    val schemaVersion: Int = SCHEMA_VERSION,
    val capturedAtEpochMs: Long,
    val capturedAtUptimeMs: Long,
    val platform: Platform,
    val app: TargetAppInfo,
    val device: DeviceInfo,
    val nodeCount: Int,
    val truncated: Boolean,
    val viewIds: List<String>,
    val recentEvents: List<EventRecord>,
    val root: NodeSnapshot,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class TargetAppInfo(
    val packageName: String,
    val versionName: String? = null,
    val versionCode: Long? = null,
)

@Serializable
data class DeviceInfo(
    val sdkInt: Int,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
)

@Serializable
data class NodeSnapshot(
    val path: String,
    val className: String? = null,
    val viewId: String? = null,
    val hasContentDescription: Boolean,
    val contentDescriptionLength: Int,
    val hasText: Boolean,
    val textLength: Int,
    val scrollable: Boolean,
    val bounds: NodeBounds,
    val collectionInfo: CollectionInfoSnapshot? = null,
    val collectionItemInfo: CollectionItemInfoSnapshot? = null,
    val rangeInfo: RangeInfoSnapshot? = null,
    val childCount: Int,
    val children: List<NodeSnapshot> = emptyList(),
)

@Serializable
data class NodeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

@Serializable
data class CollectionInfoSnapshot(
    val rowCount: Int,
    val columnCount: Int,
    val hierarchical: Boolean,
)

@Serializable
data class CollectionItemInfoSnapshot(
    val rowIndex: Int,
    val rowSpan: Int,
    val columnIndex: Int,
    val columnSpan: Int,
    val selected: Boolean,
)

@Serializable
data class RangeInfoSnapshot(
    val type: Int,
    val min: Float,
    val max: Float,
    val current: Float,
)
