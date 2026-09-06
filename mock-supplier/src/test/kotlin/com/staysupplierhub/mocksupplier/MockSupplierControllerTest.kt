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

    test("V-MCK-07: Supplier B search reproduces a body-level error over HTTP 200") {
        val response = controller(supplierB = MockSupplierMode.SUPPLIER_ERROR).supplierBSearch()
        response.statusCode shouldBe HttpStatus.OK
        response.body!!.contains("\"resultCode\":\"E503\"") shouldBe true
    }

    test("V-MCK-05: Supplier A availability withholds its response after connection") {
        val hold = MockResponseHold()
        val worker = Thread { MockSupplierController(MockSupplierMode.NO_RESPONSE, MockSupplierMode.NORMAL, hold).supplierAAvailability() }.apply { start() }
        hold.awaitEntry()
        worker.isAlive shouldBe true
        hold.release()
        worker.join(5_000)
        worker.isAlive shouldBe false
    }

    test("V-MCK-08: Supplier B search withholds its response after connection") {
        val hold = MockResponseHold()
        val worker = Thread { MockSupplierController(MockSupplierMode.NORMAL, MockSupplierMode.NO_RESPONSE, hold).supplierBSearch() }.apply { start() }
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
) = MockSupplierController(supplierA, supplierB, MockResponseHold())
