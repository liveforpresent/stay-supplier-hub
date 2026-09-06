package com.staysupplierhub.search.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StayAvailabilityTest {
    @Test
    fun `availability is the minimum inventory across every stay date`() {
        val availability = StayAvailability.from(period(), inventory(3, 1, 5))

        assertEquals(1, availability.availableRooms)
    }

    @Test
    fun `zero inventory is a valid availability result`() {
        val availability = StayAvailability.from(period(), inventory(3, 0, 5))

        assertEquals(0, availability.availableRooms)
    }

    @Test
    fun `availability rejects a missing required date`() {
        assertFailsWith<IllegalArgumentException> {
            StayAvailability.from(period(), inventory(3, 1))
        }
    }

    @Test
    fun `availability rejects duplicate dates`() {
        assertFailsWith<IllegalArgumentException> {
            StayAvailability.from(
                period(),
                listOf(
                    DailyInventory(date("2026-09-01"), 3),
                    DailyInventory(date("2026-09-01"), 1),
                    DailyInventory(date("2026-09-03"), 5),
                ),
            )
        }
    }

    @Test
    fun `availability rejects dates outside the stay period`() {
        assertFailsWith<IllegalArgumentException> {
            StayAvailability.from(
                period(),
                listOf(
                    DailyInventory(date("2026-09-01"), 3),
                    DailyInventory(date("2026-09-02"), 1),
                    DailyInventory(date("2026-09-04"), 5),
                ),
            )
        }
    }

    @Test
    fun `daily inventory rejects negative remaining rooms`() {
        assertFailsWith<IllegalArgumentException> {
            DailyInventory(date("2026-09-01"), -1)
        }
    }

    private fun period() = StayPeriod(date("2026-09-01"), date("2026-09-04"))

    private fun inventory(vararg remainingRooms: Int): List<DailyInventory> =
        remainingRooms.mapIndexed { index, rooms ->
            DailyInventory(date("2026-09-01").plusDays(index.toLong()), rooms)
        }

    private fun date(value: String): LocalDate = LocalDate.parse(value)
}
