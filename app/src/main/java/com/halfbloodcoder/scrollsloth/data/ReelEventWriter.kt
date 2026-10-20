package com.halfbloodcoder.scrollsloth.data

import com.halfbloodcoder.scrollsloth.detect.ReelViewed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Single writer for detected reels: callers [submit] without blocking, one coroutine inserts in
 * order. Only timestamp, platform and dwell reach the database.
 */
class ReelEventWriter(private val dao: ReelDao, scope: CoroutineScope) {
    private val pending = Channel<ReelViewed>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (event in pending) {
                try {
                    dao.insert(
                        ReelEventEntity(
                            timestampMs = event.timestampMs,
                            platform = event.platform.name,
                            dwellMs = event.dwellMs,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // One failed insert must not stop counting; the next event is tried normally.
                }
            }
        }
    }

    /** Returns false once the writer is closed. */
    fun submit(event: ReelViewed): Boolean = pending.trySend(event).isSuccess

    fun close() {
        pending.close()
    }
}
