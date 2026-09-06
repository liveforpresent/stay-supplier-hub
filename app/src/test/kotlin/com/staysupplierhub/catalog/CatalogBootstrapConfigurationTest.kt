package com.staysupplierhub.catalog

import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.domain.Property
import com.staysupplierhub.catalog.port.`in`.CatalogSynchronizationResult
import com.staysupplierhub.catalog.port.`in`.SynchronizeSupplierCatalogUseCase
import com.staysupplierhub.catalog.port.out.persistence.PropertyRepository
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailure
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailureType
import com.staysupplierhub.search.domain.GuestComposition
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayPeriod
import com.staysupplierhub.search.port.`in`.SearchOutcome
import com.staysupplierhub.search.port.`in`.SearchStaysUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.LocalDate

class CatalogBootstrapConfigurationTest : FunSpec({
    test("V-CAT-BOOT-01: successful startup synchronization opens readiness") {
        val readiness = runBootstrap(setOf(SupplierId("A"), SupplierId("B")), mapOf(), setOf())

        readiness.isReady() shouldBe true
    }

    test("V-CAT-BOOT-02: failed Supplier without a baseline closes readiness") {
        val supplierA = SupplierId("A")
        val readiness = runBootstrap(setOf(supplierA, SupplierId("B")), mapOf(supplierA to failure()), setOf())

        readiness.isReady() shouldBe false
        readiness.unavailableSupplierIds().shouldContainExactlyInAnyOrder(supplierA)
    }

    test("V-CAT-BOOT-03: failed Supplier with persisted state keeps readiness open") {
        val supplierA = SupplierId("A")
        val readiness = runBootstrap(setOf(supplierA, SupplierId("B")), mapOf(supplierA to failure()), setOf(supplierA))

        readiness.isReady() shouldBe true
    }

    test("V-CAT-BOOT-04: a successful empty snapshot still establishes readiness") {
        runBootstrap(setOf(SupplierId("A")), mapOf(), setOf()).isReady() shouldBe true
    }

    test("V-CAT-BOOT-05: closed readiness does not execute normal Search") {
        val readiness = CatalogReadiness().also { it.mark(setOf(SupplierId("A"))) }
        var invoked = false
        val gated = ReadinessGatedSearchStaysUseCase(readiness, SearchStaysUseCase { invoked = true; SearchOutcome.Result(emptyList(), emptyList()) })

        val outcome = gated.search(SearchCondition(StayPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-02")), GuestComposition(1, 0)))

        invoked shouldBe false
        (outcome is SearchOutcome.Unavailable) shouldBe true
    }

    test("V-API-ERR-05: closed readiness returns canonical 503 before the controller chain") {
        val readiness = CatalogReadiness().also { it.mark(setOf(SupplierId("A"))) }
        val response = MockHttpServletResponse()
        var chainInvoked = false

        SearchReadinessGateFilter(readiness).doFilter(
            MockHttpServletRequest("GET", "/api/v1/stays/search"),
            response,
        ) { _, _ -> chainInvoked = true }

        response.status shouldBe 503
        response.getHeader("Cache-Control") shouldBe "no-store"
        response.contentAsString.contains("\"code\":\"SEARCH_UNAVAILABLE\"") shouldBe true
        response.contentAsString.contains("\"A\"") shouldBe true
        chainInvoked shouldBe false
    }
})

private fun runBootstrap(
    configured: Set<SupplierId>,
    failures: Map<SupplierId, CatalogSynchronizationResult.Failed>,
    persisted: Set<SupplierId>,
): CatalogReadiness {
    val readiness = CatalogReadiness()
    CatalogBootstrapConfiguration().catalogBootstrapRunner(
        configured,
        object : SynchronizeSupplierCatalogUseCase {
            override fun synchronize(supplierId: SupplierId) = failures[supplierId] ?: CatalogSynchronizationResult.Synchronized
        },
        object : PropertyRepository {
            override fun findAllBySupplier(supplierId: SupplierId) = emptyList<Property>()
            override fun hasPersistedCatalogState(supplierId: SupplierId) = supplierId in persisted
            override fun saveAll(properties: Collection<Property>) = Unit
        },
        readiness,
    ).run(DefaultApplicationArguments())
    return readiness
}

private fun failure() = CatalogSynchronizationResult.Failed(
    CatalogSupplierFailure(CatalogSupplierFailureType.SERVICE_UNAVAILABLE),
)
