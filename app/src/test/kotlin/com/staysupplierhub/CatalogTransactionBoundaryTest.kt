package com.staysupplierhub

import com.staysupplierhub.catalog.adapter.persistence.CatalogPersistenceAdapter
import com.staysupplierhub.catalog.adapter.persistence.PropertyJpaEntity
import com.staysupplierhub.catalog.adapter.persistence.SpringDataPropertyRepository
import com.staysupplierhub.catalog.application.ApplyCatalogSnapshotService
import com.staysupplierhub.catalog.application.SynchronizeSupplierCatalogService
import com.staysupplierhub.catalog.CatalogBootstrapConfiguration
import com.staysupplierhub.catalog.CatalogReadiness
import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.domain.Property
import com.staysupplierhub.catalog.port.out.persistence.PropertyIdGenerator
import com.staysupplierhub.catalog.port.out.persistence.PropertyRepository
import com.staysupplierhub.catalog.port.out.persistence.RoomTypeIdGenerator
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogOutcome
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogPort
import com.staysupplierhub.catalog.port.`in`.CatalogSynchronizationResult
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailure
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailureType
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogProperty
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogRoomType
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogSnapshot
import com.staysupplierhub.catalog.domain.SupplierPropertyCode
import com.staysupplierhub.catalog.domain.SupplierRoomTypeCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.boot.DefaultApplicationArguments
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

@SpringBootTest(classes = [CatalogTransactionTestApplication::class])
@Testcontainers
@Import(
    CatalogPersistenceAdapter::class,
    ApplyCatalogSnapshotService::class,
    SynchronizeSupplierCatalogService::class,
    CatalogTransactionTestConfiguration::class,
)
class CatalogTransactionBoundaryTest @Autowired constructor(
    private val synchronizeSupplierCatalogService: SynchronizeSupplierCatalogService,
    private val catalogPersistenceAdapter: CatalogPersistenceAdapter,
    private val suppliers: TransactionTestSuppliers,
    private val jdbcTemplate: JdbcTemplate,
) {
    @BeforeEach
    fun reset() {
        jdbcTemplate.update("delete from room_types")
        jdbcTemplate.update("delete from properties")
        suppliers.reset()
    }

    @Test
    fun `failed multi-property snapshot rolls back entirely`() {
        suppliers.outcomes[supplierA] = success("a-1", "a-room-1")
        suppliers.failOnSaveFor = supplierA

        assertFailsWith<IllegalStateException> {
            synchronizeSupplierCatalogService.synchronize(supplierA)
        }

        assertEquals(emptyList(), catalogPersistenceAdapter.findAllBySupplier(supplierA))
    }

    @Test
    fun `supplier fetch occurs outside a database transaction`() {
        suppliers.outcomes[supplierA] = success("a-1", "a-room-1")

        synchronizeSupplierCatalogService.synchronize(supplierA)

        assertFalse(suppliers.transactionWasActiveDuringFetch)
    }

    @Test
    fun `one supplier commit survives another supplier rollback`() {
        suppliers.outcomes[supplierA] = success("a-1", "a-room-1")
        suppliers.outcomes[supplierB] = success("b-1", "b-room-1")
        suppliers.failOnSaveFor = supplierB

        synchronizeSupplierCatalogService.synchronize(supplierA)
        assertFailsWith<IllegalStateException> {
            synchronizeSupplierCatalogService.synchronize(supplierB)
        }

        assertEquals(1, catalogPersistenceAdapter.findAllBySupplier(supplierA).size)
        assertEquals(emptyList(), catalogPersistenceAdapter.findAllBySupplier(supplierB))
    }

    @Test
    fun `failed startup sync retains an actual persisted supplier baseline`() {
        suppliers.outcomes[supplierA] = success("a-1", "a-room-1")
        synchronizeSupplierCatalogService.synchronize(supplierA)
        suppliers.outcomes[supplierA] = SupplierCatalogOutcome.Failed(
            CatalogSupplierFailure(CatalogSupplierFailureType.SERVICE_UNAVAILABLE),
        )
        val readiness = CatalogReadiness()

        CatalogBootstrapConfiguration().catalogBootstrapRunner(
            setOf(supplierA), synchronizeSupplierCatalogService, catalogPersistenceAdapter, readiness,
        ).run(DefaultApplicationArguments())

        assertEquals(true, readiness.isReady())
    }

    @Test
    fun `failed startup sync without persisted state closes readiness`() {
        suppliers.outcomes[supplierA] = SupplierCatalogOutcome.Failed(
            CatalogSupplierFailure(CatalogSupplierFailureType.SERVICE_UNAVAILABLE),
        )
        val readiness = CatalogReadiness()

        CatalogBootstrapConfiguration().catalogBootstrapRunner(
            setOf(supplierA), synchronizeSupplierCatalogService, catalogPersistenceAdapter, readiness,
        ).run(DefaultApplicationArguments())

        assertEquals(false, readiness.isReady())
        assertEquals(setOf(supplierA), readiness.unavailableSupplierIds())
    }

    @Test
    fun `successful startup sync for every configured supplier opens readiness`() {
        suppliers.outcomes[supplierA] = success("a-1", "a-room-1")
        suppliers.outcomes[supplierB] = success("b-1", "b-room-1")
        val readiness = CatalogReadiness()

        CatalogBootstrapConfiguration().catalogBootstrapRunner(
            setOf(supplierA, supplierB), synchronizeSupplierCatalogService, catalogPersistenceAdapter, readiness,
        ).run(DefaultApplicationArguments())

        assertEquals(true, readiness.isReady())
        assertEquals(emptySet(), readiness.unavailableSupplierIds())
        assertEquals(1, catalogPersistenceAdapter.findAllBySupplier(supplierA).size)
        assertEquals(1, catalogPersistenceAdapter.findAllBySupplier(supplierB).size)
    }

    @Test
    fun `successful empty startup snapshot establishes readiness`() {
        suppliers.outcomes[supplierA] = SupplierCatalogOutcome.Success(SupplierCatalogSnapshot(emptyList()))
        val readiness = CatalogReadiness()

        CatalogBootstrapConfiguration().catalogBootstrapRunner(
            setOf(supplierA), synchronizeSupplierCatalogService, catalogPersistenceAdapter, readiness,
        ).run(DefaultApplicationArguments())

        assertEquals(true, readiness.isReady())
        assertEquals(emptyList(), catalogPersistenceAdapter.findAllBySupplier(supplierA))
    }

    private fun success(propertyCode: String, roomCode: String) = SupplierCatalogOutcome.Success(
        SupplierCatalogSnapshot(
            listOf(
                SupplierCatalogProperty(
                    supplierPropertyCode = SupplierPropertyCode(propertyCode),
                    name = propertyCode,
                    roomTypes = listOf(
                        SupplierCatalogRoomType(SupplierRoomTypeCode(roomCode), roomCode, 2),
                    ),
                ),
            ),
        ),
    )

    companion object {
        private val supplierA = SupplierId("supplier-a")
        private val supplierB = SupplierId("supplier-b")

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }
}

@EnableAutoConfiguration
@EntityScan(basePackageClasses = [PropertyJpaEntity::class])
@EnableJpaRepositories(basePackageClasses = [SpringDataPropertyRepository::class])
class CatalogTransactionTestApplication

@TestConfiguration(proxyBeanMethods = false)
class CatalogTransactionTestConfiguration {
    @Bean
    fun suppliers() = TransactionTestSuppliers()

    @Bean
    fun supplierCatalogPorts(suppliers: TransactionTestSuppliers): Map<SupplierId, SupplierCatalogPort> =
        listOf(SupplierId("supplier-a"), SupplierId("supplier-b")).associateWith { supplierId ->
            SupplierCatalogPort { suppliers.fetch(supplierId) }
        }

    @Bean
    fun propertyRepository(
        delegate: CatalogPersistenceAdapter,
        suppliers: TransactionTestSuppliers,
    ): PropertyRepository = FailingPropertyRepository(delegate, suppliers)

    @Bean
    fun propertyIdGenerator(): PropertyIdGenerator = SequencePropertyIdGenerator()

    @Bean
    fun roomTypeIdGenerator(): RoomTypeIdGenerator = SequenceRoomTypeIdGenerator()
}

class TransactionTestSuppliers {
    val outcomes = mutableMapOf<SupplierId, SupplierCatalogOutcome>()
    var failOnSaveFor: SupplierId? = null
    var transactionWasActiveDuringFetch = false

    fun fetch(supplierId: SupplierId): SupplierCatalogOutcome {
        transactionWasActiveDuringFetch = TransactionSynchronizationManager.isActualTransactionActive()
        return requireNotNull(outcomes[supplierId])
    }

    fun reset() {
        outcomes.clear()
        failOnSaveFor = null
        transactionWasActiveDuringFetch = false
    }
}

private class FailingPropertyRepository(
    private val delegate: CatalogPersistenceAdapter,
    private val suppliers: TransactionTestSuppliers,
) : PropertyRepository {
    override fun findAllBySupplier(supplierId: SupplierId): List<Property> = delegate.findAllBySupplier(supplierId)

    override fun hasPersistedCatalogState(supplierId: SupplierId): Boolean = delegate.hasPersistedCatalogState(supplierId)

    override fun saveAll(properties: Collection<Property>) {
        delegate.saveAll(properties)
        if (properties.any { it.supplierPropertyIdentity.supplierId == suppliers.failOnSaveFor }) {
            throw IllegalStateException("Simulated persistence failure")
        }
    }
}

private class SequencePropertyIdGenerator : PropertyIdGenerator {
    private var next = 1_000L
    override fun next() = PropertyId(next++)
}

private class SequenceRoomTypeIdGenerator : RoomTypeIdGenerator {
    private var next = 2_000L
    override fun next() = RoomTypeId(next++)
}
