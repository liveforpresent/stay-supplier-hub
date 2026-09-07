package com.staysupplierhub

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.staysupplierhub.catalog.CatalogReadiness
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SearchSupplierBProtocolFailureEndToEndTest @Autowired constructor(
    private val catalogReadiness: CatalogReadiness,
) {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `supplier B body-level E503 preserves supplier A offer as partial search`() {
        assertTrue(catalogReadiness.isReady(), "unavailable suppliers: ${catalogReadiness.unavailableSupplierIds()}")

        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(
                URI("http://localhost:$port/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-03&adults=2&children=0"),
            ).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(200, response.statusCode())
        val body = jacksonObjectMapper().readTree(response.body())
        assertEquals("PARTIAL", body["searchStatus"].asText())
        assertEquals(listOf("B"), body["degradedSuppliers"].map { it.asText() })
        assertEquals(1, body["offers"].size())
        assertEquals("A", body["offers"][0]["supplier"]?.asText(), response.body())
        assertTrue(body["offers"][0]["propertyId"].isTextual)
        assertTrue(body["offers"][0]["roomTypeId"].isTextual)
        assertEquals(3, body["offers"][0]["availableRooms"].asInt())
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun runtimeProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("stay-supplier-hub.catalog.snowflake-node-id") { 1L }
            registry.add("supplier-integration.suppliers.A.base-url", ::mockSupplierBaseUrl)
            registry.add("supplier-integration.suppliers.B.base-url", ::mockSupplierBaseUrl)
            registry.add("supplier-integration.suppliers.A.response-timeout") { "5s" }
            registry.add("supplier-integration.suppliers.B.response-timeout") { "5s" }
        }

        private fun mockSupplierBaseUrl(): String =
            requireNotNull(System.getProperty("e2e.mock-supplier.base-url")) {
                "e2e.mock-supplier.base-url must be configured by the e2eSupplierBProtocolFailureTest task"
            }
    }
}
