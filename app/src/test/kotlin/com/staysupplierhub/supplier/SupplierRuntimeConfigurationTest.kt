package com.staysupplierhub.supplier

import com.fasterxml.jackson.databind.ObjectMapper
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogPort
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityPort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.BeanCreationException
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.registerBean
import org.springframework.core.env.MapPropertySource

class SupplierRuntimeConfigurationTest : FunSpec({
    test("V-WIRE-SUP-01: configured Suppliers have matching Catalog and Availability Port maps") {
        supplierContext().use { context ->
            val configuredSupplierIds = context.getBean(SupplierIntegrationProperties::class.java).configuredSuppliers().keys
            val catalogPortIds = catalogPorts(context).keys
            val availabilityPortIds = availabilityPorts(context).keys

            configuredSupplierIds.shouldContainExactlyInAnyOrder(SupplierId("A"), SupplierId("B"))
            catalogPortIds shouldBe configuredSupplierIds
            availabilityPortIds shouldBe configuredSupplierIds
        }
    }

    test("V-WIRE-SUP-02: missing Catalog Port fails application startup") {
        shouldThrow<BeanCreationException> {
            AnnotationConfigApplicationContext(MissingCatalogPortConfiguration::class.java).close()
        }
    }

    test("V-WIRE-SUP-03: missing Availability Port fails application startup") {
        shouldThrow<BeanCreationException> {
            AnnotationConfigApplicationContext(MissingAvailabilityPortConfiguration::class.java).close()
        }
    }

    test("V-WIRE-SUP-07: configuration keys map directly to SupplierId") {
        SupplierIntegrationProperties(mapOf("A" to runtimeProperties())).configuredSuppliers().keys.single() shouldBe SupplierId("A")
        SupplierIntegrationProperties(mapOf(" A " to runtimeProperties())).configuredSuppliers().keys.single() shouldBe SupplierId(" A ")
    }
})

private fun supplierContext(): AnnotationConfigApplicationContext = AnnotationConfigApplicationContext().also { context ->
    context.environment.propertySources.addFirst(
        MapPropertySource(
            "test",
            mapOf(
                "supplier-integration.suppliers.A.base-url" to "http://supplier-a.test",
                "supplier-integration.suppliers.A.api-key" to "mock-a",
                "supplier-integration.suppliers.A.connection-timeout" to "1s",
                "supplier-integration.suppliers.A.response-timeout" to "2s",
                "supplier-integration.suppliers.A.batch-concurrency" to "5",
                "supplier-integration.suppliers.B.base-url" to "http://supplier-b.test",
                "supplier-integration.suppliers.B.api-key" to "mock-b",
                "supplier-integration.suppliers.B.connection-timeout" to "3s",
                "supplier-integration.suppliers.B.response-timeout" to "4s",
                "supplier-integration.suppliers.B.batch-concurrency" to "2",
            ),
        ),
    )
    context.registerBean<ObjectMapper> { ObjectMapper() }
    context.register(SupplierRuntimeConfiguration::class.java)
    context.refresh()
}

@Suppress("UNCHECKED_CAST")
private fun catalogPorts(context: AnnotationConfigApplicationContext): Map<SupplierId, SupplierCatalogPort> =
    context.getBean("supplierCatalogPorts") as Map<SupplierId, SupplierCatalogPort>

@Suppress("UNCHECKED_CAST")
private fun availabilityPorts(context: AnnotationConfigApplicationContext): Map<SupplierId, SupplierAvailabilityPort> =
    context.getBean("supplierAvailabilityPorts") as Map<SupplierId, SupplierAvailabilityPort>

private fun runtimeProperties() = SupplierRuntimeProperties(
    baseUrl = java.net.URI("http://supplier.test"),
    apiKey = "mock",
    connectionTimeout = java.time.Duration.ofSeconds(1),
    responseTimeout = java.time.Duration.ofSeconds(2),
    batchConcurrency = 1,
)

@Configuration(proxyBeanMethods = false)
private class MissingCatalogPortConfiguration {
    @Bean
    fun supplierWiringValidator() = SupplierWiringValidator(
        configuredSupplierIds = setOf(SupplierId("A"), SupplierId("B")),
        catalogPortIds = setOf(SupplierId("A")),
        availabilityPortIds = setOf(SupplierId("A"), SupplierId("B")),
    )
}

@Configuration(proxyBeanMethods = false)
private class MissingAvailabilityPortConfiguration {
    @Bean
    fun supplierWiringValidator() = SupplierWiringValidator(
        configuredSupplierIds = setOf(SupplierId("A"), SupplierId("B")),
        catalogPortIds = setOf(SupplierId("A"), SupplierId("B")),
        availabilityPortIds = setOf(SupplierId("A")),
    )
}
