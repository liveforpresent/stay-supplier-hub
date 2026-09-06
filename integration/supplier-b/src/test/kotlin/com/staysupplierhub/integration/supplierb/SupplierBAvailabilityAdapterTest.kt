package com.staysupplierhub.integration.supplierb

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.staysupplierhub.search.domain.GuestComposition
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayPeriod
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailure
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailureType
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityOutcome
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyCode
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyTarget
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Collections

class SupplierBAvailabilityAdapterTest : FunSpec({
    test("availability adapter returns normalized items from a successful response") {
        AvailabilityAdapterStub(200, successfulResponse).use { stub ->
            val outcome = adapter(stub.baseUrl).search(targets("property-a"), condition())

            val completed = outcome as SupplierAvailabilityOutcome.Completed
            completed.failures shouldBe emptyList()
            completed.items.single().supplierPropertyCode.value shouldBe "property-a"
            completed.items.single().supplierRoomTypeCode.value shouldBe "room-a"
            completed.items.single().wholeStayPrice.total.amount shouldBe 125_000L
        }
    }

    test("availability adapter normalizes HTTP 200 resultCode E503") {
        AvailabilityAdapterStub(200, """{ "resultCode": "E503", "data": null }""").use { stub ->
            adapter(stub.baseUrl).search(targets("property-a"), condition()) shouldBe failed(SearchSupplierFailureType.SERVICE_UNAVAILABLE)
        }
    }

    test("availability adapter normalizes HTTP 503") {
        AvailabilityAdapterStub(503, "{\"error\":\"failed\"}").use { stub ->
            adapter(stub.baseUrl).search(targets("property-a"), condition()) shouldBe failed(SearchSupplierFailureType.SERVICE_UNAVAILABLE)
        }
    }

    test("one request handles up to fifty property targets") {
        AvailabilityAdapterStub(200, successfulResponse).use { stub ->
            adapter(stub.baseUrl).search(targets(*(1..50).map { "property-$it" }.toTypedArray()), condition())

            stub.batchSizes shouldBe listOf(50)
        }
    }

    test("fifty-one targets split into two requests") {
        AvailabilityAdapterStub(200, successfulResponse).use { stub ->
            adapter(stub.baseUrl).search(targets(*(1..51).map { "property-$it" }.toTypedArray()), condition())

            stub.batchSizes.sorted() shouldBe listOf(1, 50)
        }
    }

    test("one hundred twenty-one targets split into fifty fifty twenty-one") {
        AvailabilityAdapterStub(200, successfulResponse).use { stub ->
            adapter(stub.baseUrl).search(targets(*(1..121).map { "property-$it" }.toTypedArray()), condition())

            stub.batchSizes.sorted() shouldBe listOf(21, 50, 50)
        }
    }

    test("a failed middle batch preserves successful batch items") {
        AvailabilityAdapterStub(200, successfulResponse, failingPropertyCode = "property-51").use { stub ->
            val outcome = adapter(stub.baseUrl).search(targets(*(1..121).map { "property-$it" }.toTypedArray()), condition())

            val completed = outcome as SupplierAvailabilityOutcome.Completed
            completed.items.size shouldBe 2
            completed.failures.map { it.type } shouldBe listOf(SearchSupplierFailureType.SERVICE_UNAVAILABLE)
        }
    }

    test("availability adapter normalizes a refused connection") {
        val unavailablePort = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).let { server ->
            server.start()
            server.address.port.also { server.stop(0) }
        }

        adapter("http://127.0.0.1:$unavailablePort").search(targets("property-a"), condition()) shouldBe
            failed(SearchSupplierFailureType.CONNECTION_FAILED)
    }
})

private fun adapter(baseUrl: String) = SupplierBAvailabilityAdapter(
    webClient = WebClient.builder().baseUrl(baseUrl).build(),
    apiKey = "test-api-key",
    objectMapper = jacksonObjectMapper(),
)

private fun targets(vararg codes: String): List<SupplierPropertyTarget> =
    codes.map { SupplierPropertyTarget(SupplierPropertyCode(it)) }

private fun condition() = SearchCondition(
    stayPeriod = StayPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-03")),
    guestComposition = GuestComposition(adults = 2, children = 0),
)

private fun failed(type: SearchSupplierFailureType) =
    SupplierAvailabilityOutcome.Failed(listOf(SearchSupplierFailure(type)))

private class AvailabilityAdapterStub(
    private val status: Int,
    private val responseBody: String,
    private val failingPropertyCode: String? = null,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/b/api/search") { exchange -> respond(exchange) }
        start()
    }

    var requestCount: Int = 0
        private set
    val batchSizes = Collections.synchronizedList(mutableListOf<Int>())

    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    private fun respond(exchange: HttpExchange) {
        requestCount += 1
        val propertyIds = exchange.requestURI.rawQuery.orEmpty().split("&")
            .firstOrNull { it.startsWith("propertyIds=") }
            ?.substringAfter("=")
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            ?.split(",")
            .orEmpty()
        batchSizes += propertyIds.size
        if (failingPropertyCode != null && failingPropertyCode in propertyIds) {
            val failureBody = "{\"error\":\"failed\"}".toByteArray()
            exchange.sendResponseHeaders(503, failureBody.size.toLong())
            exchange.responseBody.use { it.write(failureBody) }
            return
        }
        val body = responseBody.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    override fun close() {
        server.stop(0)
    }
}

private val successfulResponse =
    """
    {
      "resultCode": "0000",
      "data": {
        "items": [
          {
            "propertyId": "property-a",
            "roomId": "room-a",
            "breakfastIncluded": true,
            "currency": "KRW",
            "totalPrice": 125000,
            "taxIncluded": true,
            "inventory": [
              { "date": "2026-09-01", "remainingRooms": 3 },
              { "date": "2026-09-02", "remainingRooms": 1 }
            ]
          }
        ]
      }
    }
    """.trimIndent()
