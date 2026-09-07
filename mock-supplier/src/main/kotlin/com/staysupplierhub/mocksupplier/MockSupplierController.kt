package com.staysupplierhub.mocksupplier

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

@RestController
class MockSupplierController(
    @Value("\${mock-supplier.a-availability-mode:NORMAL}") private val supplierAAvailabilityMode: MockSupplierMode,
    @Value("\${mock-supplier.b-search-mode:NORMAL}") private val supplierBSearchMode: MockSupplierMode,
    @Value("\${mock-supplier.a-catalog-property-count:1}") private val supplierACatalogPropertyCount: Int,
    private val responseHold: MockResponseHold,
    @Value("\${mock-supplier.a-catalog-mode:NORMAL}") private val supplierACatalogMode: MockCatalogMode,
) {
    private val supplierAAvailabilityRequestCount = AtomicInteger()
    private val supplierACatalogRequestCount = AtomicInteger()

    @GetMapping("/a/v1/hotels")
    fun supplierACatalog(): ResponseEntity<String> {
        val requestCount = supplierACatalogRequestCount.incrementAndGet()
        return when (supplierACatalogMode) {
        MockCatalogMode.NORMAL -> ResponseEntity.ok(
            if (supplierACatalogPropertyCount == 1) A_CATALOG else supplierACatalog(supplierACatalogPropertyCount),
        )
        MockCatalogMode.SUPPLIER_ERROR -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"error\":\"unavailable\"}")
        MockCatalogMode.SUPPLIER_ERROR_AFTER_FIRST_REQUEST ->
            if (requestCount == 1) {
                ResponseEntity.ok(if (supplierACatalogPropertyCount == 1) A_CATALOG else supplierACatalog(supplierACatalogPropertyCount))
            } else {
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"error\":\"unavailable\"}")
            }
        }
    }

    @GetMapping("/b/api/properties")
    fun supplierBCatalog() = supplierBResponse("catalog")

    @GetMapping("/a/v1/availability")
    fun supplierAAvailability(@RequestParam(required = false) hotelCodes: String? = null): ResponseEntity<String> {
        supplierAAvailabilityRequestCount.incrementAndGet()
        return when (supplierAAvailabilityMode) {
        MockSupplierMode.NORMAL -> ResponseEntity.ok(supplierAAvailabilityPayload(hotelCodes))
        MockSupplierMode.ZERO_INVENTORY -> ResponseEntity.ok(A_ZERO_INVENTORY_AVAILABILITY)
        MockSupplierMode.SUPPLIER_ERROR -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"error\":\"unavailable\"}")
        MockSupplierMode.NO_RESPONSE -> withholdResponse()
        }
    }

    @GetMapping("/b/api/search")
    fun supplierBSearch(): ResponseEntity<String> = when (supplierBSearchMode) {
        MockSupplierMode.NORMAL -> supplierBResponse("search")
        MockSupplierMode.ZERO_INVENTORY -> ResponseEntity.ok(B_ZERO_INVENTORY_AVAILABILITY)
        MockSupplierMode.SUPPLIER_ERROR -> ResponseEntity.ok("{\"resultCode\":\"E503\",\"resultMessage\":\"unavailable\",\"data\":null}")
        MockSupplierMode.NO_RESPONSE -> withholdResponse()
    }

    @GetMapping("/mock-diagnostics/a-availability-request-count")
    fun supplierAAvailabilityRequestCount(): Map<String, Int> =
        mapOf("count" to supplierAAvailabilityRequestCount.get())

    private fun supplierAResponse(kind: String): ResponseEntity<String> = ResponseEntity.ok(
        if (kind == "catalog") A_CATALOG else A_AVAILABILITY,
    )

    private fun supplierACatalog(propertyCount: Int): String =
        """{"items":[${(1..propertyCount).joinToString(",") { index ->
            """{"hotelCode":"hotel-a-$index","hotelName":"Mock A Hotel $index","roomTypes":[{"roomTypeCode":"room-a-$index","roomTypeName":"Standard","maxOccupancy":2}]}"""
        }}]}"""

    private fun supplierAAvailabilityPayload(hotelCodes: String?): String {
        if (supplierACatalogPropertyCount == 1) return A_AVAILABILITY
        val items = hotelCodes.orEmpty().split(',').filter(String::isNotBlank).joinToString(",") { hotelCode ->
            val roomCode = hotelCode.removePrefix("hotel-a-").let { "room-a-$it" }
            """{"hotelCode":"$hotelCode","roomTypeCode":"$roomCode","breakfastIncluded":true,"currency":"KRW","dailyRates":[{"date":"2026-09-01","remainingRooms":3,"nightlyRate":100,"taxAmount":10},{"date":"2026-09-02","remainingRooms":3,"nightlyRate":100,"taxAmount":10}]}"""
        }
        return """{"items":[$items]}"""
    }

    private fun supplierBResponse(kind: String): ResponseEntity<String> = ResponseEntity.ok(
        if (kind == "catalog") B_CATALOG else B_AVAILABILITY,
    )

    private fun withholdResponse(): ResponseEntity<String> {
        responseHold.awaitRelease()
        error("mock response latch must not be released")
    }

    private companion object {
        val A_CATALOG = """{"items":[{"hotelCode":"hotel-a","hotelName":"Mock A Hotel","roomTypes":[{"roomTypeCode":"room-a","roomTypeName":"Standard","maxOccupancy":2}]}]}"""
        val A_AVAILABILITY = """{"items":[{"hotelCode":"hotel-a","roomTypeCode":"room-a","breakfastIncluded":true,"currency":"KRW","dailyRates":[{"date":"2026-09-01","remainingRooms":3,"nightlyRate":100,"taxAmount":10},{"date":"2026-09-02","remainingRooms":3,"nightlyRate":100,"taxAmount":10}]}]}"""
        val A_ZERO_INVENTORY_AVAILABILITY = """{"items":[{"hotelCode":"hotel-a","roomTypeCode":"room-a","breakfastIncluded":true,"currency":"KRW","dailyRates":[{"date":"2026-09-01","remainingRooms":3,"nightlyRate":100,"taxAmount":10},{"date":"2026-09-02","remainingRooms":0,"nightlyRate":100,"taxAmount":10}]}]}"""
        val B_CATALOG = """{"resultCode":"0000","resultMessage":"ok","data":{"items":[{"propertyId":"property-b","propertyName":"Mock B Property","rooms":[{"roomId":"room-b","roomName":"Standard","maxOccupancy":2}]}]}}"""
        val B_AVAILABILITY = """{"resultCode":"0000","resultMessage":"ok","data":{"items":[{"propertyId":"property-b","roomId":"room-b","breakfastIncluded":true,"currency":"KRW","totalPrice":220,"taxIncluded":true,"inventory":[{"date":"2026-09-01","remainingRooms":3},{"date":"2026-09-02","remainingRooms":3}]}]}}"""
        val B_ZERO_INVENTORY_AVAILABILITY = """{"resultCode":"0000","resultMessage":"ok","data":{"items":[{"propertyId":"property-b","roomId":"room-b","breakfastIncluded":true,"currency":"KRW","totalPrice":220,"taxIncluded":true,"inventory":[{"date":"2026-09-01","remainingRooms":3},{"date":"2026-09-02","remainingRooms":0}]}]}}"""
    }
}

enum class MockSupplierMode {
    NORMAL,
    ZERO_INVENTORY,
    SUPPLIER_ERROR,
    NO_RESPONSE,
}

enum class MockCatalogMode {
    NORMAL,
    SUPPLIER_ERROR,
    SUPPLIER_ERROR_AFTER_FIRST_REQUEST,
}

@Component
class MockResponseHold {
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)

    fun awaitRelease() {
        entered.countDown()
        release.await()
    }

    internal fun awaitEntry() {
        check(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "expected no-response request did not enter" }
    }

    internal fun release() {
        release.countDown()
    }
}
