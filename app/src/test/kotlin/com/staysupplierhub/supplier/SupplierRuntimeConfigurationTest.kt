package com.staysupplierhub.supplier

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogPort
import com.staysupplierhub.search.domain.GuestComposition
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayPeriod
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityPort
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityOutcome
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyCode
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyTarget
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
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
import java.net.InetSocketAddress
import java.time.LocalDate

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

    test("V-WIRE-SUP-06: Supplier A and B use their own runtime settings") {
        RuntimeSupplierStub("/a/v1/availability", supplierAResponse).use { supplierA ->
            RuntimeSupplierStub("/b/api/search", supplierBResponse).use { supplierB ->
                supplierContext(supplierA.baseUrl, supplierB.baseUrl).use { context ->
                    val ports = availabilityPorts(context)

                    (ports.getValue(SupplierId("A")).search(targets("hotel-a"), condition()) is SupplierAvailabilityOutcome.Completed) shouldBe true
                    (ports.getValue(SupplierId("B")).search(targets("property-b"), condition()) is SupplierAvailabilityOutcome.Completed) shouldBe true
                }

                supplierA.apiKeys shouldBe listOf("mock-a")
                supplierB.apiKeys shouldBe listOf("mock-b")
            }
        }
    }
})

private fun supplierContext(
    supplierABaseUrl: String = "http://supplier-a.test",
    supplierBBaseUrl: String = "http://supplier-b.test",
): AnnotationConfigApplicationContext = AnnotationConfigApplicationContext().also { context ->
    context.environment.propertySources.addFirst(
        MapPropertySource(
            "test",
            mapOf(
                "supplier-integration.suppliers.A.base-url" to supplierABaseUrl,
                "supplier-integration.suppliers.A.api-key" to "mock-a",
                "supplier-integration.suppliers.A.connection-timeout" to "1s",
                "supplier-integration.suppliers.A.response-timeout" to "2s",
                "supplier-integration.suppliers.A.batch-concurrency" to "5",
                "supplier-integration.suppliers.B.base-url" to supplierBBaseUrl,
                "supplier-integration.suppliers.B.api-key" to "mock-b",
                "supplier-integration.suppliers.B.connection-timeout" to "3s",
                "supplier-integration.suppliers.B.response-timeout" to "4s",
                "supplier-integration.suppliers.B.batch-concurrency" to "2",
            ),
        ),
    )
    context.registerBean<ObjectMapper> { jacksonObjectMapper() }
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

private fun targets(code: String) = listOf(SupplierPropertyTarget(SupplierPropertyCode(code)))

private fun condition() = SearchCondition(
    stayPeriod = StayPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04")),
    guestComposition = GuestComposition(adults = 2, children = 1),
)

private class RuntimeSupplierStub(
    path: String,
    private val response: String,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext(path) { exchange -> respond(exchange) }
        start()
    }
    val apiKeys = mutableListOf<String?>()
    val baseUrl = "http://127.0.0.1:${server.address.port}"

    private fun respond(exchange: HttpExchange) {
        apiKeys += exchange.requestHeaders.getFirst("X-Api-Key")
        val body = response.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    override fun close() {
        server.stop(0)
    }
}

private val supplierAResponse =
    """
    { "items": [{ "hotelCode": "hotel-a", "roomTypeCode": "room-a", "breakfastIncluded": true, "currency": "KRW",
      "dailyRates": [
        { "date": "2026-09-01", "remainingRooms": 3, "nightlyRate": 100, "taxAmount": 10 },
        { "date": "2026-09-02", "remainingRooms": 3, "nightlyRate": 100, "taxAmount": 10 },
        { "date": "2026-09-03", "remainingRooms": 3, "nightlyRate": 100, "taxAmount": 10 }
      ] }] }
    """.trimIndent()

private val supplierBResponse =
    """
    { "resultCode": "0000", "data": { "items": [{ "propertyId": "property-b", "roomId": "room-b",
      "breakfastIncluded": true, "currency": "KRW", "totalPrice": 330, "taxIncluded": true,
      "inventory": [
        { "date": "2026-09-01", "remainingRooms": 3 },
        { "date": "2026-09-02", "remainingRooms": 3 },
        { "date": "2026-09-03", "remainingRooms": 3 }
      ] }] } }
    """.trimIndent()

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
