package com.staysupplierhub.integration.suppliera

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailureType
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogOutcome
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress

class SupplierACatalogAdapterTest : FunSpec({
    test("normal catalog response becomes a complete neutral snapshot") {
        SupplierAStub(
            response =
                """
                {
                  "items": [
                    {
                      "hotelCode": "hotel-a",
                      "hotelName": "Supplier A Hotel",
                      "roomTypes": [
                        { "roomTypeCode": "standard", "roomTypeName": "Standard", "maxOccupancy": 2 }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
        ).use { stub ->
            val outcome = adapter(stub.baseUrl).fetchCatalog()

            val success = outcome.shouldBeInstanceOf<SupplierCatalogOutcome.Success>()
            success.snapshot.properties.single().let { property ->
                property.supplierPropertyCode.value shouldBe "hotel-a"
                property.name shouldBe "Supplier A Hotel"
                property.roomTypes.single().let { room ->
                    room.supplierRoomTypeCode.value shouldBe "standard"
                    room.name shouldBe "Standard"
                    room.maxOccupancy shouldBe 2
                }
            }
            stub.requestWasValid shouldBe true
        }
    }

    test("structurally invalid catalog fails closed without a partial snapshot") {
        SupplierAStub(
            response =
                """
                {
                  "items": [
                    { "hotelCode": "duplicate", "hotelName": "One", "roomTypes": [] },
                    { "hotelCode": "duplicate", "hotelName": "Two", "roomTypes": [] }
                  ]
                }
                """.trimIndent(),
        ).use { stub ->
            val outcome = adapter(stub.baseUrl).fetchCatalog()

            val failed = outcome.shouldBeInstanceOf<SupplierCatalogOutcome.Failed>()
            failed.failure.type shouldBe CatalogSupplierFailureType.INVALID_RESPONSE
        }
    }

    listOf(
        400 to CatalogSupplierFailureType.INVALID_REQUEST,
        401 to CatalogSupplierFailureType.AUTHENTICATION_FAILED,
        429 to CatalogSupplierFailureType.RATE_LIMITED,
        500 to CatalogSupplierFailureType.SUPPLIER_ERROR,
        503 to CatalogSupplierFailureType.SERVICE_UNAVAILABLE,
    ).forEach { (status, expectedFailure) ->
        test("HTTP $status catalog response becomes $expectedFailure") {
            SupplierAStub(
                response = "{\"error\":\"supplier failure\"}",
                status = status,
            ).use { stub ->
                val outcome = adapter(stub.baseUrl).fetchCatalog()

                val failed = outcome.shouldBeInstanceOf<SupplierCatalogOutcome.Failed>()
                failed.failure.type shouldBe expectedFailure
                stub.requestWasValid shouldBe true
            }
        }
    }
})

private fun adapter(baseUrl: String) = SupplierACatalogAdapter(
    webClient = WebClient.builder().baseUrl(baseUrl).build(),
    apiKey = "test-api-key",
    objectMapper = jacksonObjectMapper(),
)

private class SupplierAStub(
    private val response: String,
    private val status: Int = 200,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/a/v1/hotels") { exchange -> respond(exchange) }
        start()
    }

    var requestWasValid = false
        private set

    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    private fun respond(exchange: HttpExchange) {
        requestWasValid = exchange.requestMethod == "GET" &&
            exchange.requestURI.path == "/a/v1/hotels" &&
            exchange.requestHeaders.getFirst("X-Api-Key") == "test-api-key"
        val bytes = response.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() {
        server.stop(0)
    }
}
