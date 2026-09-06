package com.staysupplierhub.integration.suppliera

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.staysupplierhub.search.domain.GuestComposition
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayPeriod
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
import java.time.LocalDate

class SupplierAAvailabilityAdapterTest : FunSpec({
    test("availability adapter returns normalized items from a successful response") {
        AvailabilityAdapterStub(200, validResponse).use { stub ->
            val outcome = adapter(stub.baseUrl).search(adapterTargets("hotel-a"), adapterCondition())

            val completed = outcome as SupplierAvailabilityOutcome.Completed
            completed.failures shouldBe emptyList()
            completed.items.single().supplierPropertyCode.value shouldBe "hotel-a"
            completed.items.single().supplierRoomTypeCode.value shouldBe "room-a"
            completed.items.single().wholeStayPrice.total.amount shouldBe 660L
            completed.items.single().wholeStayPrice.total.currency shouldBe "KRW"
        }
    }

    listOf(
        400 to SearchSupplierFailureType.INVALID_REQUEST,
        401 to SearchSupplierFailureType.AUTHENTICATION_FAILED,
        429 to SearchSupplierFailureType.RATE_LIMITED,
        500 to SearchSupplierFailureType.SUPPLIER_ERROR,
        503 to SearchSupplierFailureType.SERVICE_UNAVAILABLE,
    ).forEach { (status, expectedFailure) ->
        test("availability adapter normalizes HTTP $status") {
            AvailabilityAdapterStub(status, "{\"error\":\"failed\"}").use { stub ->
                val outcome = adapter(stub.baseUrl).search(adapterTargets("hotel-a"), adapterCondition())

                outcome shouldBe SupplierAvailabilityOutcome.Failed(
                    failures = listOf(com.staysupplierhub.search.port.out.supplier.SearchSupplierFailure(expectedFailure)),
                )
            }
        }
    }

    test("availability adapter normalizes a request larger than Supplier A limit") {
        AvailabilityAdapterStub(200, validResponse).use { stub ->
            val outcome = adapter(stub.baseUrl).search(
                adapterTargets(*(1..51).map { "hotel-$it" }.toTypedArray()),
                adapterCondition(),
            )

            outcome shouldBe SupplierAvailabilityOutcome.Failed(
                failures = listOf(com.staysupplierhub.search.port.out.supplier.SearchSupplierFailure(SearchSupplierFailureType.INVALID_REQUEST)),
            )
            stub.requestCount shouldBe 0
        }
    }
})

private fun adapter(baseUrl: String) = SupplierAAvailabilityAdapter(
    webClient = WebClient.builder().baseUrl(baseUrl).build(),
    apiKey = "test-api-key",
    objectMapper = jacksonObjectMapper(),
)

private fun adapterTargets(vararg codes: String): List<SupplierPropertyTarget> =
    codes.map { SupplierPropertyTarget(SupplierPropertyCode(it)) }

private fun adapterCondition() = SearchCondition(
    stayPeriod = StayPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04")),
    guestComposition = GuestComposition(adults = 2, children = 1),
)

private class AvailabilityAdapterStub(
    private val status: Int,
    private val responseBody: String,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/a/v1/availability") { exchange -> respond(exchange) }
        start()
    }

    var requestCount: Int = 0
        private set

    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    private fun respond(exchange: HttpExchange) {
        requestCount += 1
        val body = responseBody.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    override fun close() {
        server.stop(0)
    }
}

private val validResponse = """
    {
      "items": [
        {
          "hotelCode": "hotel-a",
          "roomTypeCode": "room-a",
          "breakfastIncluded": true,
          "currency": "KRW",
          "dailyRates": [
            { "date": "2026-09-01", "remainingRooms": 3, "nightlyRate": 100, "taxAmount": 10 },
            { "date": "2026-09-02", "remainingRooms": 1, "nightlyRate": 200, "taxAmount": 20 },
            { "date": "2026-09-03", "remainingRooms": 5, "nightlyRate": 300, "taxAmount": 30 }
          ]
        }
      ]
    }
""".trimIndent()
