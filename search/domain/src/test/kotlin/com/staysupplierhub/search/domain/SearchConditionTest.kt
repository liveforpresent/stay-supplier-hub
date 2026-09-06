package com.staysupplierhub.search.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchConditionTest {
    @Test
    fun `stay period accepts a check-in before check-out`() {
        val period = StayPeriod(date("2026-09-01"), date("2026-09-04"))

        assertEquals(date("2026-09-01"), period.checkIn)
        assertEquals(date("2026-09-04"), period.checkOut)
    }

    @Test
    fun `stay period rejects equal check-in and check-out`() {
        assertFailsWith<IllegalArgumentException> {
            StayPeriod(date("2026-09-01"), date("2026-09-01"))
        }
    }

    @Test
    fun `stay period rejects check-out before check-in`() {
        assertFailsWith<IllegalArgumentException> {
            StayPeriod(date("2026-09-04"), date("2026-09-01"))
        }
    }

    @Test
    fun `stay period excludes check-out from required dates`() {
        val period = StayPeriod(date("2026-09-01"), date("2026-09-04"))

        assertEquals(
            listOf(date("2026-09-01"), date("2026-09-02"), date("2026-09-03")),
            period.requiredDates,
        )
    }

    @Test
    fun `guest composition preserves adults and children separately`() {
        val guests = GuestComposition(adults = 2, children = 1)

        assertEquals(2, guests.adults)
        assertEquals(1, guests.children)
    }

    @Test
    fun `guest composition rejects an adult count below one`() {
        assertFailsWith<IllegalArgumentException> {
            GuestComposition(adults = 0, children = 0)
        }
    }

    @Test
    fun `guest composition rejects negative children`() {
        assertFailsWith<IllegalArgumentException> {
            GuestComposition(adults = 1, children = -1)
        }
    }

    private fun date(value: String): LocalDate = LocalDate.parse(value)
}
