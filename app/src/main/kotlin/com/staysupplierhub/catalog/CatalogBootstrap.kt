package com.staysupplierhub.catalog

import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.port.`in`.CatalogSynchronizationResult
import com.staysupplierhub.catalog.port.`in`.SynchronizeSupplierCatalogUseCase
import com.staysupplierhub.catalog.port.out.persistence.PropertyRepository
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.port.`in`.SearchOutcome
import com.staysupplierhub.search.port.`in`.SearchStaysUseCase
import com.staysupplierhub.search.port.`in`.SupplierFailure
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailureType
import com.staysupplierhub.search.application.SearchStaysService
import com.staysupplierhub.catalog.application.ReadSearchableCatalogService
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityPort
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.atomic.AtomicReference

class CatalogReadiness {
    private val unavailable = AtomicReference<Set<SupplierId>>(emptySet())

    fun mark(unavailableSupplierIds: Set<SupplierId>) {
        unavailable.set(unavailableSupplierIds)
    }

    fun unavailableSupplierIds(): Set<SupplierId> = unavailable.get()

    fun isReady(): Boolean = unavailableSupplierIds().isEmpty()
}

class ReadinessGatedSearchStaysUseCase(
    private val catalogReadiness: CatalogReadiness,
    private val delegate: SearchStaysUseCase,
) : SearchStaysUseCase {
    override fun search(condition: SearchCondition): SearchOutcome =
        if (catalogReadiness.isReady()) {
            delegate.search(condition)
        } else {
            SearchOutcome.Unavailable(
                catalogReadiness.unavailableSupplierIds().map { supplierId ->
                    SupplierFailure(supplierId, SearchSupplierFailureType.SERVICE_UNAVAILABLE)
                },
            )
        }
}

@Component
class SearchReadinessGateFilter(
    private val catalogReadiness: CatalogReadiness,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI != "/api/v1/stays/search"

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (catalogReadiness.isReady()) {
            filterChain.doFilter(request, response)
            return
        }
        response.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
        response.contentType = "application/json"
        response.setHeader("Cache-Control", "no-store")
        val suppliers = catalogReadiness.unavailableSupplierIds().joinToString(",") { "\"${it.value}\"" }
        response.writer.write("{\"code\":\"SEARCH_UNAVAILABLE\",\"message\":\"Availability information is temporarily unavailable.\",\"suppliers\":[$suppliers]}")
    }
}

@Configuration(proxyBeanMethods = false)
class CatalogBootstrapConfiguration {
    @Bean
    fun catalogReadiness() = CatalogReadiness()

    @Bean
    fun searchStaysUseCase(
        readSearchableCatalogService: ReadSearchableCatalogService,
        supplierAvailabilityPorts: Map<SupplierId, SupplierAvailabilityPort>,
        catalogReadiness: CatalogReadiness,
    ): SearchStaysUseCase = ReadinessGatedSearchStaysUseCase(
        catalogReadiness,
        SearchStaysService(readSearchableCatalogService, supplierAvailabilityPorts),
    )

    @Bean
    fun catalogBootstrapRunner(
        configuredSupplierIds: Set<SupplierId>,
        synchronizeSupplierCatalog: SynchronizeSupplierCatalogUseCase,
        propertyRepository: PropertyRepository,
        catalogReadiness: CatalogReadiness,
    ) = ApplicationRunner {
        val unavailable = configuredSupplierIds.filterTo(mutableSetOf()) { supplierId ->
            when (synchronizeSupplierCatalog.synchronize(supplierId)) {
                CatalogSynchronizationResult.Synchronized -> false
                is CatalogSynchronizationResult.Failed -> !propertyRepository.hasPersistedCatalogState(supplierId)
            }
        }
        catalogReadiness.mark(unavailable)
    }
}
