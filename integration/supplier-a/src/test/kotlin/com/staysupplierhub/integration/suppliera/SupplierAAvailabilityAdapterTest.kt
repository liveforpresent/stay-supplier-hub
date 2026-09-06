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
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    test("one request handles up to fifty hotel targets") {
        AvailabilityAdapterStub(200, validResponse).use { stub ->
            adapter(stub.baseUrl).search(adapterTargets(*(1..50).map { "hotel-$it" }.toTypedArray()), adapterCondition())

            stub.batchSizes shouldBe listOf(50)
        }
    }

    test("fifty-one hotel targets split into two requests") {
        AvailabilityAdapterStub(200, validResponse).use { stub ->
            adapter(stub.baseUrl).search(adapterTargets(*(1..51).map { "hotel-$it" }.toTypedArray()), adapterCondition())

            stub.batchSizes.sorted() shouldBe listOf(1, 50)
        }
    }

    test("one hundred twenty-one hotel targets split into fifty fifty twenty-one") {
        AvailabilityAdapterStub(200, validResponse).use { stub ->
            adapter(stub.baseUrl).search(adapterTargets(*(1..121).map { "hotel-$it" }.toTypedArray()), adapterCondition())

            stub.batchSizes.sorted() shouldBe listOf(21, 50, 50)
        }
    }

    test("a failed middle batch preserves successful batch items") {
        AvailabilityAdapterStub(200, validResponse, failingHotelCode = "hotel-51").use { stub ->
            val outcome = adapter(stub.baseUrl).search(
                adapterTargets(*(1..121).map { "hotel-$it" }.toTypedArray()),
                adapterCondition(),
            )

            val completed = outcome as SupplierAvailabilityOutcome.Completed
            completed.items.size shouldBe 2
            completed.failures.map { it.type } shouldBe listOf(SearchSupplierFailureType.SERVICE_UNAVAILABLE)
        }
    }

    test("batch requests never exceed configured concurrency and more than one may progress") {
        ConcurrencyStub(expectedInitialRequests = 2).use { stub ->
            val worker = Thread {
                adapter(stub.baseUrl, maxBatchConcurrency = 2)
                    .search(adapterTargets(*(1..121).map { "hotel-$it" }.toTypedArray()), adapterCondition())
            }.apply { start() }

            stub.awaitInitialRequests()
            stub.maxObservedConcurrency.get() shouldBe 2
            stub.releaseRequests()
            worker.join(5_000)
            worker.isAlive shouldBe false
        }
    }

    test("concurrent searches share one adapter concurrency limit") {
        ConcurrencyStub(expectedInitialRequests = 2).use { stub ->
            val availabilityAdapter = adapter(stub.baseUrl, maxBatchConcurrency = 2)
            val first = Thread {
                availabilityAdapter.search(adapterTargets(*(1..121).map { "first-$it" }.toTypedArray()), adapterCondition())
            }.apply { start() }
            val second = Thread {
                availabilityAdapter.search(adapterTargets(*(1..121).map { "second-$it" }.toTypedArray()), adapterCondition())
            }.apply { start() }

            stub.awaitInitialRequests()
            stub.maxObservedConcurrency.get() shouldBe 2
            stub.releaseRequests()
            first.join(5_000)
            second.join(5_000)
            first.isAlive shouldBe false
            second.isAlive shouldBe false
        }
    }

    test("default batch concurrency is five") {
        ConcurrencyStub(expectedInitialRequests = 5).use { stub ->
            val worker = Thread {
                adapter(stub.baseUrl).search(adapterTargets(*(1..251).map { "hotel-$it" }.toTypedArray()), adapterCondition())
            }.apply { start() }

            stub.awaitInitialRequests()
            stub.maxObservedConcurrency.get() shouldBe 5
            stub.releaseRequests()
            worker.join(5_000)
            worker.isAlive shouldBe false
        }
    }

    test("a connected upstream that does not respond becomes a timeout") {
        NoResponseStub().use { stub ->
            val outcome = timeoutAdapter(stub.baseUrl).search(adapterTargets("hotel-a"), adapterCondition())

            outcome shouldBe SupplierAvailabilityOutcome.Failed(
                listOf(com.staysupplierhub.search.port.out.supplier.SearchSupplierFailure(SearchSupplierFailureType.TIMEOUT)),
            )
            stub.awaitRequest()
        }
    }
})

private fun adapter(baseUrl: String, maxBatchConcurrency: Int = 5) = SupplierAAvailabilityAdapter(
    webClient = WebClient.builder().baseUrl(baseUrl).build(),
    apiKey = "test-api-key",
    objectMapper = jacksonObjectMapper(),
    maxBatchConcurrency = maxBatchConcurrency,
)

private fun timeoutAdapter(baseUrl: String) = SupplierAAvailabilityAdapter(
    webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofMillis(100))))
        .build(),
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
    private val failingHotelCode: String? = null,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/a/v1/availability") { exchange -> respond(exchange) }
        start()
    }

    var requestCount: Int = 0
        private set
    val batchSizes = Collections.synchronizedList(mutableListOf<Int>())

    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    private fun respond(exchange: HttpExchange) {
        requestCount += 1
        val hotelCodes = exchange.requestURI.rawQuery.orEmpty().split("&")
            .firstOrNull { it.startsWith("hotelCodes=") }
            ?.substringAfter("=")
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            ?.split(",")
            .orEmpty()
        batchSizes += hotelCodes.size
        if (failingHotelCode != null && failingHotelCode in hotelCodes) {
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

private class ConcurrencyStub(
    expectedInitialRequests: Int,
) : AutoCloseable {
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = this@ConcurrencyStub.executor
        createContext("/a/v1/availability") { exchange -> respond(exchange) }
        start()
    }
    private val initialRequests = CountDownLatch(expectedInitialRequests)
    private val release = CountDownLatch(1)
    private val inFlight = AtomicInteger(0)
    val maxObservedConcurrency = AtomicInteger(0)
    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    fun awaitInitialRequests() {
        check(initialRequests.await(5, TimeUnit.SECONDS)) { "expected batch requests did not enter" }
    }

    fun releaseRequests() {
        release.countDown()
    }

    private fun respond(exchange: HttpExchange) {
        val active = inFlight.incrementAndGet()
        maxObservedConcurrency.updateAndGet { maxOf(it, active) }
        initialRequests.countDown()
        try {
            check(release.await(5, TimeUnit.SECONDS)) { "test did not release batch requests" }
            val body = validResponse.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } finally {
            inFlight.decrementAndGet()
        }
    }

    override fun close() {
        release.countDown()
        server.stop(0)
        executor.shutdownNow()
    }
}

private class NoResponseStub : AutoCloseable {
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = this@NoResponseStub.executor
        createContext("/a/v1/availability") { exchange -> awaitRelease(exchange) }
        start()
    }
    private val requestEntered = CountDownLatch(1)
    private val release = CountDownLatch(1)
    val baseUrl = "http://127.0.0.1:${server.address.port}"

    fun awaitRequest() {
        check(requestEntered.await(5, TimeUnit.SECONDS)) { "expected request did not enter" }
    }

    private fun awaitRelease(exchange: HttpExchange) {
        requestEntered.countDown()
        try {
            release.await(5, TimeUnit.SECONDS)
        } finally {
            exchange.close()
        }
    }

    override fun close() {
        release.countDown()
        server.stop(0)
        executor.shutdownNow()
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
