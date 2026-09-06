package com.staysupplierhub.mocksupplier

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CountDownLatch

@RestController
class MockSupplierController(
    @Value("\${mock-supplier.a-availability-mode:NORMAL}") private val supplierAAvailabilityMode: MockSupplierMode,
    @Value("\${mock-supplier.b-search-mode:NORMAL}") private val supplierBSearchMode: MockSupplierMode,
    private val responseHold: MockResponseHold,
) {
    @GetMapping("/a/v1/hotels")
    fun supplierACatalog() = supplierAResponse("catalog")

    @GetMapping("/b/api/properties")
    fun supplierBCatalog() = supplierBResponse("catalog")

    @GetMapping("/a/v1/availability")
    fun supplierAAvailability(): ResponseEntity<String> = when (supplierAAvailabilityMode) {
        MockSupplierMode.NORMAL -> supplierAResponse("availability")
        MockSupplierMode.SUPPLIER_ERROR -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"error\":\"unavailable\"}")
        MockSupplierMode.NO_RESPONSE -> withholdResponse()
    }

    @GetMapping("/b/api/search")
    fun supplierBSearch(): ResponseEntity<String> = when (supplierBSearchMode) {
        MockSupplierMode.NORMAL -> supplierBResponse("search")
        MockSupplierMode.SUPPLIER_ERROR -> ResponseEntity.ok("{\"resultCode\":\"E503\",\"resultMessage\":\"unavailable\",\"data\":null}")
        MockSupplierMode.NO_RESPONSE -> withholdResponse()
    }

    private fun supplierAResponse(kind: String): ResponseEntity<String> = ResponseEntity.ok(
        if (kind == "catalog") A_CATALOG else A_AVAILABILITY,
    )

    private fun supplierBResponse(kind: String): ResponseEntity<String> = ResponseEntity.ok(
        if (kind == "catalog") B_CATALOG else B_AVAILABILITY,
    )

    private fun withholdResponse(): ResponseEntity<String> {
        responseHold.awaitRelease()
        error("mock response latch must not be released")
    }

    private companion object {
        val A_CATALOG = """{"items":[{"hotelCode":"hotel-a","hotelName":"Mock A Hotel","roomTypes":[{"roomTypeCode":"room-a","roomTypeName":"Standard","maxOccupancy":2}]}]}"""
        val A_AVAILABILITY = """{"items":[{"hotelCode":"hotel-a","hotelName":"Mock A Hotel","roomTypeCode":"room-a","roomTypeName":"Standard","maxOccupancy":2,"breakfastIncluded":true,"currency":"KRW","dailyRates":[{"date":"2026-09-01","remainingRooms":3,"nightlyRate":100,"taxAmount":10},{"date":"2026-09-02","remainingRooms":3,"nightlyRate":100,"taxAmount":10}]}]}"""
        val B_CATALOG = """{"resultCode":"0000","resultMessage":"ok","data":{"items":[{"propertyId":"property-b","propertyName":"Mock B Property","rooms":[{"roomId":"room-b","roomName":"Standard","maxOccupancy":2}]}]}}"""
        val B_AVAILABILITY = """{"resultCode":"0000","resultMessage":"ok","data":{"items":[{"propertyId":"property-b","propertyName":"Mock B Property","roomId":"room-b","roomName":"Standard","maxOccupancy":2,"breakfastIncluded":true,"currency":"KRW","totalPrice":220,"taxIncluded":true,"inventory":[{"date":"2026-09-01","remainingRooms":3},{"date":"2026-09-02","remainingRooms":3}]}]}}"""
    }
}

enum class MockSupplierMode {
    NORMAL,
    SUPPLIER_ERROR,
    NO_RESPONSE,
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
