package com.staysupplierhub

import com.staysupplierhub.catalog.CatalogReadiness
import com.staysupplierhub.catalog.adapter.persistence.CatalogPersistenceAdapter
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class CatalogIdStabilityEndToEndTest {
    @Test
    fun `restarting against the same catalog snapshot preserves public internal ids`() {
        val firstIds = startApplication().use { context ->
            assertTrue(context.getBean(CatalogReadiness::class.java).isReady())
            catalogIds(context)
        }

        startApplication().use { context ->
            assertTrue(context.getBean(CatalogReadiness::class.java).isReady())
            assertEquals(firstIds, catalogIds(context))
        }
    }

    private fun startApplication(): ConfigurableApplicationContext =
        SpringApplicationBuilder(Application::class.java)
            .web(WebApplicationType.NONE)
            .environment(StandardEnvironment().apply {
                propertySources.addFirst(MapPropertySource("e2e", runtimeProperties()))
            })
            .run()

    private fun catalogIds(context: ConfigurableApplicationContext): Map<String, CatalogIds> =
        context.getBean(CatalogPersistenceAdapter::class.java).readSearchableCatalog().associate { property ->
            "${property.supplierId.value}:${property.supplierPropertyCode}" to CatalogIds(
                propertyId = property.id.value,
                roomTypeIds = property.roomTypes.associate { roomType -> roomType.supplierRoomTypeCode to roomType.id.value },
            )
        }

    private fun runtimeProperties(): Map<String, Any> = mapOf(
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
            "e2e.mock-supplier.base-url must be configured by the e2eCatalogIdStabilityTest task"
        }

    private data class CatalogIds(
        val propertyId: Long,
        val roomTypeIds: Map<String, Long>,
    )

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
