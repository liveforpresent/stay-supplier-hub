package com.staysupplierhub.search.application

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.ReadSearchableCatalog
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.api.SearchableProperty
import com.staysupplierhub.catalog.api.SearchableRoomType
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.search.domain.DailyInventory
import com.staysupplierhub.search.domain.GuestComposition
import com.staysupplierhub.search.domain.Money
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayPeriod
import com.staysupplierhub.search.domain.StayPrice
import com.staysupplierhub.search.port.`in`.SearchOutcome
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailure
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailureType
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityItem
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityOutcome
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityPort
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyCode
import com.staysupplierhub.search.port.out.supplier.SupplierRoomTypeCode
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SearchStaysServiceTest {
    @Test
    fun `merges successful supplier offers using catalog metadata`() {
        val service = serviceFromOutcomes(
            outcomes = mapOf(a to completed(item(propertyCode = "a-property", roomCode = "a-room")), b to completed(item(propertyCode = "b-property", roomCode = "b-room"))),
        )

        val result = assertIs<SearchOutcome.Result>(service.search(condition()))

        assertEquals(2, result.offers.size)
        assertTrue(result.failures.isEmpty())
        assertEquals(setOf("Catalog A room", "Catalog B room"), result.offers.map { it.roomTypeName }.toSet())
        assertEquals(setOf(a, b), result.offers.map { it.supplierId }.toSet())
        assertTrue(result.offers.all { it.availability.availableRooms == 1 })
    }

    @Test
    fun `preserves successful supplier result when other supplier fails`() {
        val service = serviceFromOutcomes(mapOf(a to completed(item("a-property", "a-room")), b to failed(SearchSupplierFailureType.TIMEOUT)))

        val result = assertIs<SearchOutcome.Result>(service.search(condition()))

        assertEquals(1, result.offers.size)
        assertEquals(listOf(b to SearchSupplierFailureType.TIMEOUT), result.failures.map { it.supplierId to it.type })
    }

    @Test
    fun `preserves supplier B offers when supplier A fails`() {
        val service = serviceFromOutcomes(mapOf(a to failed(SearchSupplierFailureType.TIMEOUT), b to completed(item("b-property", "b-room"))))

        val result = assertIs<SearchOutcome.Result>(service.search(condition()))

        assertEquals(listOf(b), result.offers.map { it.supplierId })
        assertEquals(listOf(a to SearchSupplierFailureType.TIMEOUT), result.failures.map { it.supplierId to it.type })
    }

    @Test
    fun `returns partial empty result for legitimate empty supplier response and another failure`() {
        val service = serviceFromOutcomes(mapOf(a to completed(), b to failed(SearchSupplierFailureType.SERVICE_UNAVAILABLE)))

        val result = assertIs<SearchOutcome.Result>(service.search(condition()))

        assertTrue(result.offers.isEmpty())
        assertEquals(listOf(b to SearchSupplierFailureType.SERVICE_UNAVAILABLE), result.failures.map { it.supplierId to it.type })
    }

    @Test
    fun `returns unavailable when every relevant supplier fails`() {
        val service = serviceFromOutcomes(mapOf(a to failed(SearchSupplierFailureType.TIMEOUT), b to failed(SearchSupplierFailureType.SERVICE_UNAVAILABLE)))

        val result = assertIs<SearchOutcome.Unavailable>(service.search(condition()))

        assertEquals(setOf(a, b), result.failures.map { it.supplierId }.toSet())
    }

    @Test
    fun `does not invoke suppliers when catalog has no searchable target`() {
        var invoked = false
        val service = SearchStaysService(ReadSearchableCatalog { emptyList() }, mapOf(a to SupplierAvailabilityPort { _, _ -> invoked = true; completed() }))

        val result = assertIs<SearchOutcome.Result>(service.search(condition()))

        assertTrue(result.offers.isEmpty())
        assertTrue(result.failures.isEmpty())
        assertTrue(!invoked)
    }

    @Test
    fun `drops invalid items while preserving valid siblings and records failure`() {
        val service = serviceFromOutcomes(mapOf(a to completed(item("a-property", "a-room"), item("a-property", "unknown-room")), b to completed()))

        val result = assertIs<SearchOutcome.Result>(service.search(condition()))

        assertEquals(1, result.offers.size)
        assertEquals(listOf(a to SearchSupplierFailureType.INVALID_RESPONSE), result.failures.map { it.supplierId to it.type })
    }

    @Test
    fun `retains zero availability as a valid offer`() {
        val zeroInventory = listOf(
            DailyInventory(LocalDate.of(2026, 9, 1), 2),
            DailyInventory(LocalDate.of(2026, 9, 2), 0),
        )
        val service = serviceFromOutcomes(mapOf(a to completed(item("a-property", "a-room", zeroInventory)), b to completed()))

        val result = assertIs<SearchOutcome.Result>(service.search(condition()))

        assertEquals(1, result.offers.size)
        assertEquals(0, result.offers.single().availability.availableRooms)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `drops occupancy-incompatible items and records failure`() {
        val catalog = ReadSearchableCatalog { listOf(property(a, "a-property", "a-room", "Small room", maxOccupancy = 1)) }
        val service = SearchStaysService(catalog, mapOf(a to SupplierAvailabilityPort { _, _ -> completed(item("a-property", "a-room")) }))

        val result = assertIs<SearchOutcome.Result>(service.search(condition()))

        assertTrue(result.offers.isEmpty())
        assertEquals(listOf(a to SearchSupplierFailureType.INVALID_RESPONSE), result.failures.map { it.supplierId to it.type })
    }

    @Test
    fun `starts supplier groups before either group is released and failure does not cancel sibling`() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val first = SupplierAvailabilityPort { _, _ -> entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); failed(SearchSupplierFailureType.TIMEOUT) }
        val second = SupplierAvailabilityPort { _, _ -> entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); completed(item("b-property", "b-room")) }
        val service = service(mapOf(a to first, b to second))
        val result = arrayOfNulls<SearchOutcome>(1)
        val searchThread = Thread { result[0] = service.search(condition()) }.apply { start() }

        assertTrue(entered.await(5, TimeUnit.SECONDS), "both supplier groups should enter before release")
        release.countDown()
        searchThread.join()

        val completedResult = assertIs<SearchOutcome.Result>(result[0])
        assertEquals(1, completedResult.offers.size)
        assertEquals(listOf(a to SearchSupplierFailureType.TIMEOUT), completedResult.failures.map { it.supplierId to it.type })
    }

    private fun service(ports: Map<SupplierId, SupplierAvailabilityPort>) =
        SearchStaysService(ReadSearchableCatalog { listOf(property(a, "a-property", "a-room", "Catalog A room"), property(b, "b-property", "b-room", "Catalog B room")) }, ports)

    private fun serviceFromOutcomes(outcomes: Map<SupplierId, SupplierAvailabilityOutcome>) =
        service(outcomes.mapValues { (_, outcome) -> SupplierAvailabilityPort { _, _ -> outcome } })

    private fun property(supplierId: SupplierId, propertyCode: String, roomCode: String, roomName: String, maxOccupancy: Int = 2) =
        SearchableProperty(PropertyId(if (supplierId == a) 1 else 2), "Catalog ${supplierId.value}", supplierId, propertyCode, listOf(SearchableRoomType(RoomTypeId(if (supplierId == a) 11 else 22), roomCode, roomName, maxOccupancy)))

    private fun condition() = SearchCondition(StayPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)), GuestComposition(2, 0))

    private fun item(propertyCode: String, roomCode: String, inventories: List<DailyInventory> = defaultInventory()) =
        SupplierAvailabilityItem(SupplierPropertyCode(propertyCode), SupplierRoomTypeCode(roomCode), StayPrice(Money(10_000, "KRW")), inventories, breakfastIncluded = true)

    private fun completed(vararg items: SupplierAvailabilityItem) = SupplierAvailabilityOutcome.Completed(items.toList(), emptyList())
    private fun failed(type: SearchSupplierFailureType) = SupplierAvailabilityOutcome.Failed(listOf(SearchSupplierFailure(type)))
    private fun defaultInventory() = listOf(DailyInventory(LocalDate.of(2026, 9, 1), 3), DailyInventory(LocalDate.of(2026, 9, 2), 1))

    private companion object {
        val a = SupplierId("A")
        val b = SupplierId("B")
    }
}
