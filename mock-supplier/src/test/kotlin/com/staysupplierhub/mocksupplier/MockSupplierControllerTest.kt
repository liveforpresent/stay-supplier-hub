package com.staysupplierhub.mocksupplier

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus

class MockSupplierControllerTest : FunSpec({
    test("V-MCK-01 and V-MCK-03: Supplier A catalog and availability provide normal responses") {
        val controller = controller()

        controller.supplierACatalog().statusCode shouldBe HttpStatus.OK
        controller.supplierACatalog().body!!.contains("hotelCode") shouldBe true
        controller.supplierAAvailability().statusCode shouldBe HttpStatus.OK
        controller.supplierAAvailability().body!!.contains("dailyRates") shouldBe true
    }

    test("V-MCK-02 and V-MCK-06: Supplier B catalog and search provide normal envelopes") {
        val controller = controller()

        controller.supplierBCatalog().body!!.contains("\"resultCode\":\"0000\"") shouldBe true
        controller.supplierBSearch().body!!.contains("\"resultCode\":\"0000\"") shouldBe true
    }

    test("V-MCK-04: Supplier A availability reproduces a transport error") {
        controller(supplierA = MockSupplierMode.SUPPLIER_ERROR).supplierAAvailability().statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
    }

    test("V-MCK-09: Supplier A availability provides a valid zero-inventory date") {
        val response = controller(supplierA = MockSupplierMode.ZERO_INVENTORY).supplierAAvailability()

        response.statusCode shouldBe HttpStatus.OK
        response.body!!.contains("\"remainingRooms\":0") shouldBe true
    }

    test("V-MCK-10: Supplier A can provide 51 distinct catalog properties for batching verification") {
        val response = controller(supplierAPropertyCount = 51).supplierACatalog()

        response.statusCode shouldBe HttpStatus.OK
        response.body!!.contains("\"hotelCode\":\"hotel-a-51\"") shouldBe true
    }

    test("V-MCK-11: Supplier A catalog reproduces a transport error") {
        controller(supplierACatalog = MockCatalogMode.SUPPLIER_ERROR).supplierACatalog().statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
    }

    test("V-MCK-12: Supplier A catalog succeeds once then reproduces a refresh error") {
        val controller = controller(supplierACatalog = MockCatalogMode.SUPPLIER_ERROR_AFTER_FIRST_REQUEST)

        controller.supplierACatalog().statusCode shouldBe HttpStatus.OK
        controller.supplierACatalog().statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
    }

    test("V-MCK-07: Supplier B search reproduces a body-level error over HTTP 200") {
        val response = controller(supplierB = MockSupplierMode.SUPPLIER_ERROR).supplierBSearch()
        response.statusCode shouldBe HttpStatus.OK
        response.body!!.contains("\"resultCode\":\"E503\"") shouldBe true
    }

    test("V-MCK-05: Supplier A availability withholds its response after connection") {
        val hold = MockResponseHold()
        val worker = Thread { MockSupplierController(MockSupplierMode.NO_RESPONSE, MockSupplierMode.NORMAL, 1, hold, MockCatalogMode.NORMAL).supplierAAvailability() }.apply { start() }
        hold.awaitEntry()
        worker.isAlive shouldBe true
        hold.release()
        worker.join(5_000)
        worker.isAlive shouldBe false
    }

    test("V-MCK-08: Supplier B search withholds its response after connection") {
        val hold = MockResponseHold()
        val worker = Thread { MockSupplierController(MockSupplierMode.NORMAL, MockSupplierMode.NO_RESPONSE, 1, hold, MockCatalogMode.NORMAL).supplierBSearch() }.apply { start() }
        hold.awaitEntry()
        worker.isAlive shouldBe true
        hold.release()
        worker.join(5_000)
        worker.isAlive shouldBe false
    }
})

private fun controller(
    supplierA: MockSupplierMode = MockSupplierMode.NORMAL,
    supplierB: MockSupplierMode = MockSupplierMode.NORMAL,
    supplierAPropertyCount: Int = 1,
    supplierACatalog: MockCatalogMode = MockCatalogMode.NORMAL,
) = MockSupplierController(supplierA, supplierB, supplierAPropertyCount, MockResponseHold(), supplierACatalog)
