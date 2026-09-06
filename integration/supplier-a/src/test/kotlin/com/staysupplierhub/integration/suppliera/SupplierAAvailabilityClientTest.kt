package com.staysupplierhub.integration.suppliera

import com.staysupplierhub.search.domain.GuestComposition
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayPeriod
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyCode
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyTarget
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class SupplierAAvailabilityClientTest : FunSpec({
    test("availability request includes the Supplier A API key") {
        AvailabilityRequestStub().use { stub ->
            fetch(stub.baseUrl, targets("hotel-a"), condition()) shouldBe "{}"

            stub.apiKey shouldBe "test-api-key"
        }
    }

    test("availability request preserves stay dates and guest composition") {
        AvailabilityRequestStub().use { stub ->
            fetch(stub.baseUrl, targets("hotel-a"), condition())

            stub.query shouldBe mapOf(
                "hotelCodes" to "hotel-a",
                "checkIn" to "2026-09-01",
                "checkOut" to "2026-09-04",
                "adults" to "2",
                "children" to "1",
            )
        }
    }

    test("availability request joins external property codes with commas") {
        AvailabilityRequestStub().use { stub ->
            fetch(stub.baseUrl, targets("hotel-a", "hotel-b"), condition())

            stub.query.getValue("hotelCodes") shouldBe "hotel-a,hotel-b"
        }
    }

    test("a single availability request rejects more than fifty property codes") {
        AvailabilityRequestStub().use { stub ->
            shouldThrow<IllegalArgumentException> {
                fetch(stub.baseUrl, targets(*(1..51).map { "hotel-$it" }.toTypedArray()), condition())
            }

            stub.query shouldBe emptyMap()
        }
    }
})

private fun client(baseUrl: String) = SupplierAAvailabilityClient(
    webClient = WebClient.builder().baseUrl(baseUrl).build(),
    apiKey = "test-api-key",
)

private fun fetch(
    baseUrl: String,
    targets: List<SupplierPropertyTarget>,
    condition: SearchCondition,
): String = runBlocking { client(baseUrl).fetch(targets, condition) }

private fun targets(vararg codes: String): List<SupplierPropertyTarget> =
    codes.map { SupplierPropertyTarget(SupplierPropertyCode(it)) }

private fun condition() = SearchCondition(
    stayPeriod = StayPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04")),
    guestComposition = GuestComposition(adults = 2, children = 1),
)

private class AvailabilityRequestStub : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/a/v1/availability") { exchange -> respond(exchange) }
        start()
    }

    var apiKey: String? = null
        private set
    var query: Map<String, String> = emptyMap()
        private set

    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    private fun respond(exchange: HttpExchange) {
        apiKey = exchange.requestHeaders.getFirst("X-Api-Key")
        query = exchange.requestURI.rawQuery.orEmpty().split("&")
            .filter(String::isNotEmpty)
            .associate { pair ->
                val (key, value) = pair.split("=", limit = 2)
                decode(key) to decode(value)
            }
        val body = "{}".toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

    override fun close() {
        server.stop(0)
    }
}
