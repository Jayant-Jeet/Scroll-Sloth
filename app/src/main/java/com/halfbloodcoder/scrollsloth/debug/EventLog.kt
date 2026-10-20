package com.halfbloodcoder.scrollsloth.debug

/** In-memory ring buffer of the most recent accessibility events. */
class EventLog(private val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val events = ArrayDeque<EventRecord>(capacity)

    @Synchronized
    fun add(event: EventRecord) {
        if (events.size == capacity) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): List<EventRecord> = events.toList()

    @Synchronized
    fun clear() = events.clear()

    companion object {
        const val DEFAULT_CAPACITY = 30
    }
}
