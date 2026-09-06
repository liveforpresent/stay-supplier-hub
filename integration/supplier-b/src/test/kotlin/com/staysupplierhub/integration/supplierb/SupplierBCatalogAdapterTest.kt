package com.staysupplierhub.integration.supplierb

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

class SupplierBCatalogAdapterTest : FunSpec({
    test("normal Supplier B catalog response becomes a complete neutral snapshot") {
        CatalogStub(successfulCatalogResponse).use { stub ->
            val outcome = adapter(stub.baseUrl).fetchCatalog()

            val success = outcome.shouldBeInstanceOf<SupplierCatalogOutcome.Success>()
            success.snapshot.properties.single().let { property ->
                property.supplierPropertyCode.value shouldBe "property-a"
                property.name shouldBe "Supplier B Property"
                property.roomTypes.single().let { room ->
                    room.supplierRoomTypeCode.value shouldBe "room-a"
                    room.name shouldBe "Standard"
                    room.maxOccupancy shouldBe 2
                }
            }
            stub.requestWasValid shouldBe true
        }
    }

    test("successful empty Supplier B catalog is a complete empty snapshot") {
        CatalogStub("""{ "resultCode": "0000", "data": { "items": [] } }""").use { stub ->
            val outcome = adapter(stub.baseUrl).fetchCatalog()

            outcome.shouldBeInstanceOf<SupplierCatalogOutcome.Success>().snapshot.properties shouldBe emptyList()
        }
    }

    test("structurally invalid Supplier B catalog fails closed") {
        CatalogStub(
            """
            { "resultCode": "0000", "data": { "items": [
              { "propertyId": "duplicate", "propertyName": "One", "rooms": [] },
              { "propertyId": "duplicate", "propertyName": "Two", "rooms": [] }
            ] } }
            """.trimIndent(),
        ).use { stub ->
            val outcome = adapter(stub.baseUrl).fetchCatalog()

            outcome.shouldBeInstanceOf<SupplierCatalogOutcome.Failed>().failure.type shouldBe
                CatalogSupplierFailureType.INVALID_RESPONSE
        }
    }

    test("HTTP 200 Supplier B resultCode E503 becomes service unavailable") {
        CatalogStub("""{ "resultCode": "E503", "data": null }""").use { stub ->
            val outcome = adapter(stub.baseUrl).fetchCatalog()

            outcome.shouldBeInstanceOf<SupplierCatalogOutcome.Failed>().failure.type shouldBe
                CatalogSupplierFailureType.SERVICE_UNAVAILABLE
        }
    }
})

private fun adapter(baseUrl: String) = SupplierBCatalogAdapter(
    webClient = WebClient.builder().baseUrl(baseUrl).build(),
    apiKey = "test-api-key",
    objectMapper = jacksonObjectMapper(),
)

private class CatalogStub(
    private val response: String,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/b/api/properties") { exchange -> respond(exchange) }
        start()
    }

    var requestWasValid = false
        private set
    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    private fun respond(exchange: HttpExchange) {
        requestWasValid = exchange.requestMethod == "GET" &&
            exchange.requestURI.path == "/b/api/properties" &&
            exchange.requestHeaders.getFirst("X-Api-Key") == "test-api-key"
        val body = response.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    override fun close() {
        server.stop(0)
    }
}

private val successfulCatalogResponse =
    """
    {
      "resultCode": "0000",
      "data": {
        "items": [
          {
            "propertyId": "property-a",
            "propertyName": "Supplier B Property",
            "rooms": [
              { "roomId": "room-a", "roomName": "Standard", "maxOccupancy": 2 }
            ]
          }
        ]
      }
    }
    """.trimIndent()
