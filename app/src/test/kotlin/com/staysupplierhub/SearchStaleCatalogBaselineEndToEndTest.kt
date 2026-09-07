package com.staysupplierhub

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.staysupplierhub.catalog.CatalogReadiness
import com.staysupplierhub.catalog.adapter.persistence.CatalogPersistenceAdapter
import com.staysupplierhub.catalog.api.SupplierId
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class SearchStaleCatalogBaselineEndToEndTest {
    @Test
    fun `failed A refresh reuses persisted baseline and keeps search available`() {
        startApplication(WebApplicationType.NONE).use { firstContext ->
            assertTrue(firstContext.getBean(CatalogReadiness::class.java).isReady())
            assertEquals(1, firstContext.getBean(CatalogPersistenceAdapter::class.java).findAllBySupplier(SupplierId("A")).size)
        }

        startApplication(WebApplicationType.SERVLET).use { refreshedContext ->
            assertTrue(refreshedContext.getBean(CatalogReadiness::class.java).isReady())
            assertEquals(1, refreshedContext.getBean(CatalogPersistenceAdapter::class.java).findAllBySupplier(SupplierId("A")).size)
            val port = requireNotNull((refreshedContext as ServletWebServerApplicationContext).webServer).port
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(
                    URI("http://localhost:$port/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-03&adults=2&children=0"),
                ).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            assertEquals(200, response.statusCode())
            val body = jacksonObjectMapper().readTree(response.body())
            assertEquals("COMPLETE", body["searchStatus"].asText())
            assertEquals(2, body["offers"].size())
        }
    }

    private fun startApplication(webApplicationType: WebApplicationType): ConfigurableApplicationContext =
        SpringApplicationBuilder(Application::class.java)
            .web(webApplicationType)
            .environment(StandardEnvironment().apply {
                propertySources.addFirst(MapPropertySource("e2e", runtimeProperties()))
            })
            .run()

    private fun runtimeProperties(): Map<String, Any> = mapOf(
        "server.port" to 0,
        "spring.datasource.url" to postgres.jdbcUrl,
        "spring.datasource.username" to postgres.username,
        "spring.datasource.password" to postgres.password,
        "spring.jpa.hibernate.ddl-auto" to "validate",
        "stay-supplier-hub.catalog.snowflake-node-id" to 1L,
        "supplier-integration.suppliers.A.base-url" to mockSupplierBaseUrl(),
        "supplier-integration.suppliers.B.base-url" to mockSupplierBaseUrl(),
        "supplier-integration.suppliers.A.response-timeout" to "5s",
        "supplier-integration.suppliers.B.response-timeout" to "5s",
    )

    private fun mockSupplierBaseUrl(): String =
        requireNotNull(System.getProperty("e2e.mock-supplier.base-url")) {
            "e2e.mock-supplier.base-url must be configured by the e2eStaleCatalogBaselineTest task"
        }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
