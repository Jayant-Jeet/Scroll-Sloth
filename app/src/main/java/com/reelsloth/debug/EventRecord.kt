package com.reelsloth.debug

import kotlinx.serialization.Serializable

@Serializable
data class EventRecord(
    val eventTimeMs: Long,
    val type: String,
    val packageName: String? = null,
    val className: String? = null,
    val scrollDeltaX: Int? = null,
    val scrollDeltaY: Int? = null,
    val fromIndex: Int? = null,
    val toIndex: Int? = null,
    val itemCount: Int? = null,
) {
    companion object {
        /** Android reports unknown indices and counts as -1; those are stored as absent. */
        fun create(
            eventTimeMs: Long,
            type: String,
            packageName: String?,
            className: String?,
            scrollDeltaX: Int?,
            scrollDeltaY: Int?,
            fromIndex: Int,
            toIndex: Int,
            itemCount: Int,
        ) = EventRecord(
            eventTimeMs = eventTimeMs,
            type = type,
            packageName = packageName,
            className = className,
            scrollDeltaX = scrollDeltaX,
            scrollDeltaY = scrollDeltaY,
            fromIndex = fromIndex.takeIf { it >= 0 },
            toIndex = toIndex.takeIf { it >= 0 },
            itemCount = itemCount.takeIf { it >= 0 },
        )
    }
}
