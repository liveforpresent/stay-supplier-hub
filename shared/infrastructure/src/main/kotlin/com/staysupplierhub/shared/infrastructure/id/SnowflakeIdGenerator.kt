package com.staysupplierhub.shared.infrastructure.id

fun interface MillisecondClock {
    fun currentTimeMillis(): Long
}

class ClockMovedBackwardsException(
    lastTimestamp: Long,
    currentTimestamp: Long,
) : IllegalStateException("Clock moved backwards from $lastTimestamp to $currentTimestamp")

class SnowflakeIdGenerator(
    nodeId: Long,
    private val clock: MillisecondClock = MillisecondClock(System::currentTimeMillis),
) {
    private val nodeId = nodeId.also {
        require(it in 0..MAX_NODE_ID) { "nodeId must be between 0 and $MAX_NODE_ID" }
    }

    private var lastTimestamp = -1L
    private var sequence = 0L

    @Synchronized
    fun nextId(): Long {
        var timestamp = clock.currentTimeMillis()
        if (timestamp < lastTimestamp) {
            throw ClockMovedBackwardsException(lastTimestamp, timestamp)
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) and MAX_SEQUENCE
            if (sequence == 0L) {
                timestamp = waitForNextMillis(lastTimestamp)
            }
        } else {
            sequence = 0L
        }

        require(timestamp >= EPOCH_MILLIS) { "clock must not precede the configured epoch" }
        lastTimestamp = timestamp
        return ((timestamp - EPOCH_MILLIS) shl TIMESTAMP_SHIFT) or (nodeId shl NODE_ID_SHIFT) or sequence
    }

    private fun waitForNextMillis(previousTimestamp: Long): Long {
        var timestamp = clock.currentTimeMillis()
        while (timestamp <= previousTimestamp) {
            timestamp = clock.currentTimeMillis()
        }
        return timestamp
    }

    private companion object {
        const val EPOCH_MILLIS = 1_704_067_200_000L // 2024-01-01T00:00:00Z
        const val NODE_ID_BITS = 10
        const val SEQUENCE_BITS = 12
        const val MAX_NODE_ID = (1L shl NODE_ID_BITS) - 1
        const val MAX_SEQUENCE = (1L shl SEQUENCE_BITS) - 1
        const val NODE_ID_SHIFT = SEQUENCE_BITS
        const val TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS
    }
}
