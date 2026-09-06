package com.staysupplierhub.search.port.out.supplier

import com.staysupplierhub.search.domain.DailyInventory
import com.staysupplierhub.search.domain.GuestComposition
import com.staysupplierhub.search.domain.Money
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayPeriod
import com.staysupplierhub.search.domain.StayPrice
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SupplierAvailabilityPortTest {
    @Test
    fun `supplier port receives neutral targets and search condition`() {
        val target = SupplierPropertyTarget(SupplierPropertyCode("hotel-a"))
        val condition = SearchCondition(
            StayPeriod(date("2026-09-01"), date("2026-09-04")),
            GuestComposition(adults = 2, children = 1),
        )
        val expected = SupplierAvailabilityOutcome.Completed(
            items = listOf(item()),
            failures = listOf(SearchSupplierFailure(SearchSupplierFailureType.RATE_LIMITED)),
        )
        val port = SupplierAvailabilityPort { targets, receivedCondition ->
            assertEquals(listOf(target), targets)
            assertEquals(condition, receivedCondition)
            expected
        }

        assertEquals(expected, port.search(listOf(target), condition))
    }

    @Test
    fun `supplier availability item preserves only neutral availability values`() {
        val item = item()

        assertEquals("hotel-a", item.supplierPropertyCode.value)
        assertEquals("room-a", item.supplierRoomTypeCode.value)
        assertEquals(429_000L, item.wholeStayPrice.total.amount)
        assertEquals("KRW", item.wholeStayPrice.total.currency)
        assertEquals(listOf(3, 1, 5), item.dailyInventories.map(DailyInventory::remainingRooms))
        assertEquals(false, item.breakfastIncluded)
    }

    @Test
    fun `supplier external codes must not be blank`() {
        assertFailsWith<IllegalArgumentException> { SupplierPropertyCode(" ") }
        assertFailsWith<IllegalArgumentException> { SupplierRoomTypeCode("") }
    }

    private fun item() = SupplierAvailabilityItem(
        supplierPropertyCode = SupplierPropertyCode("hotel-a"),
        supplierRoomTypeCode = SupplierRoomTypeCode("room-a"),
        wholeStayPrice = StayPrice(Money(amount = 429_000, currency = "KRW")),
        dailyInventories = listOf(
            DailyInventory(date("2026-09-01"), 3),
            DailyInventory(date("2026-09-02"), 1),
            DailyInventory(date("2026-09-03"), 5),
        ),
        breakfastIncluded = false,
    )

    private fun date(value: String): LocalDate = LocalDate.parse(value)
}
