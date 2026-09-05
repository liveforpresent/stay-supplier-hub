package com.staysupplierhub.shared.infrastructure.id

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SnowflakeIdGeneratorTest {
    @Test
    fun `generated ID is positive`() {
        val generator = SnowflakeIdGenerator(nodeId = 1, clock = MutableClock(1_704_067_200_001L))

        assertTrue(generator.nextId() > 0)
    }

    @Test
    fun `sequential generation produces no duplicates`() {
        val generator = SnowflakeIdGenerator(nodeId = 1, clock = MutableClock(1_704_067_200_001L))

        val ids = (1..100).map { generator.nextId() }

        assertTrue(ids.toSet().size == ids.size)
    }

    @Test
    fun `concurrent generation produces no duplicates`() {
        val generator = SnowflakeIdGenerator(nodeId = 1)
        val workers = 8
        val idsPerWorker = 500
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val ids = ConcurrentHashMap.newKeySet<Long>()
        val executor = Executors.newFixedThreadPool(workers)

        repeat(workers) {
            executor.execute {
                start.await()
                repeat(idsPerWorker) { ids += generator.nextId() }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        executor.shutdown()

        assertTrue(ids.size == workers * idsPerWorker)
    }

    @Test
    fun `invalid node ID is rejected`() {
        assertFailsWith<IllegalArgumentException> { SnowflakeIdGenerator(nodeId = -1) }
        assertFailsWith<IllegalArgumentException> { SnowflakeIdGenerator(nodeId = 1_024) }
    }

    @Test
    fun `clock rollback fails instead of producing a duplicate`() {
        val clock = MutableClock(1_704_067_200_010L)
        val generator = SnowflakeIdGenerator(nodeId = 1, clock = clock)
        generator.nextId()
        clock.current = 1_704_067_200_009L

        assertFailsWith<ClockMovedBackwardsException> { generator.nextId() }
    }

    private class MutableClock(var current: Long) : MillisecondClock {
        override fun currentTimeMillis(): Long = current
    }
}
