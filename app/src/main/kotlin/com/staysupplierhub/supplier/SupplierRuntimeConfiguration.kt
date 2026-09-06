package com.staysupplierhub.supplier

import com.fasterxml.jackson.databind.ObjectMapper
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogPort
import com.staysupplierhub.integration.suppliera.SupplierAAvailabilityAdapter
import com.staysupplierhub.integration.suppliera.SupplierACatalogAdapter
import com.staysupplierhub.integration.supplierb.SupplierBAvailabilityAdapter
import com.staysupplierhub.integration.supplierb.SupplierBCatalogAdapter
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityPort
import io.netty.channel.ChannelOption
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.net.URI
import java.time.Duration

@ConfigurationProperties("supplier-integration")
data class SupplierIntegrationProperties(
    val suppliers: Map<String, SupplierRuntimeProperties>,
) {
    fun configuredSuppliers(): Map<SupplierId, SupplierRuntimeProperties> = suppliers.mapKeys { (key, _) -> SupplierId(key) }
}

data class SupplierRuntimeProperties(
    val baseUrl: URI,
    val apiKey: String,
    val connectionTimeout: Duration,
    val responseTimeout: Duration,
    val batchConcurrency: Int,
) {
    init {
        require(baseUrl.isAbsolute) { "Supplier baseUrl must be absolute" }
        require(apiKey.isNotBlank()) { "Supplier apiKey must not be blank" }
        require(!connectionTimeout.isZero && !connectionTimeout.isNegative) { "Supplier connectionTimeout must be positive" }
        require(!responseTimeout.isZero && !responseTimeout.isNegative) { "Supplier responseTimeout must be positive" }
        require(batchConcurrency >= 1) { "Supplier batchConcurrency must be at least one" }
        require(connectionTimeout.toMillis() <= Int.MAX_VALUE) { "Supplier connectionTimeout is too large" }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SupplierIntegrationProperties::class)
class SupplierRuntimeConfiguration {
    @Bean
    fun supplierCatalogPorts(
        properties: SupplierIntegrationProperties,
        objectMapper: ObjectMapper,
    ): Map<SupplierId, SupplierCatalogPort> = properties.configuredSuppliers().mapValues { (supplierId, runtime) ->
        when (supplierId.value) {
            "A" -> SupplierACatalogAdapter(webClient(runtime), runtime.apiKey, objectMapper)
            "B" -> SupplierBCatalogAdapter(webClient(runtime), runtime.apiKey, objectMapper)
            else -> error("No Catalog adapter is registered for configured Supplier ${supplierId.value}")
        }
    }

    @Bean
    fun supplierAvailabilityPorts(
        properties: SupplierIntegrationProperties,
        objectMapper: ObjectMapper,
    ): Map<SupplierId, SupplierAvailabilityPort> = properties.configuredSuppliers().mapValues { (supplierId, runtime) ->
        when (supplierId.value) {
            "A" -> SupplierAAvailabilityAdapter(webClient(runtime), runtime.apiKey, objectMapper)
            "B" -> SupplierBAvailabilityAdapter(webClient(runtime), runtime.apiKey, objectMapper, runtime.batchConcurrency)
            else -> error("No Availability adapter is registered for configured Supplier ${supplierId.value}")
        }
    }

    @Bean
    fun supplierWiringValidator(
        properties: SupplierIntegrationProperties,
        supplierCatalogPorts: Map<SupplierId, SupplierCatalogPort>,
        supplierAvailabilityPorts: Map<SupplierId, SupplierAvailabilityPort>,
    ) = SupplierWiringValidator(properties.configuredSuppliers().keys, supplierCatalogPorts.keys, supplierAvailabilityPorts.keys)

    private fun webClient(runtime: SupplierRuntimeProperties): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, runtime.connectionTimeout.toMillis().toInt())
            .responseTimeout(runtime.responseTimeout)
        return WebClient.builder()
            .baseUrl(runtime.baseUrl.toString())
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}

class SupplierWiringValidator(
    configuredSupplierIds: Set<SupplierId>,
    catalogPortIds: Set<SupplierId>,
    availabilityPortIds: Set<SupplierId>,
) {
    init {
        require(configuredSupplierIds == catalogPortIds) {
            "Configured Suppliers and Catalog Port registrations must match"
        }
        require(configuredSupplierIds == availabilityPortIds) {
            "Configured Suppliers and Availability Port registrations must match"
        }
    }
}
