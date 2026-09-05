package com.staysupplierhub.catalog.adapter.persistence

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.domain.CatalogStatus
import com.staysupplierhub.catalog.domain.Property
import com.staysupplierhub.catalog.domain.RoomType
import com.staysupplierhub.catalog.domain.SupplierPropertyCode
import com.staysupplierhub.catalog.domain.SupplierPropertyIdentity
import com.staysupplierhub.catalog.domain.SupplierRoomTypeCode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@EnableAutoConfiguration
@EntityScan(basePackageClasses = [PropertyJpaEntity::class])
@EnableJpaRepositories(basePackageClasses = [SpringDataPropertyRepository::class])
class PersistenceTestApplication

@SpringBootTest(classes = [PersistenceTestApplication::class])
@Testcontainers
@Import(CatalogPersistenceAdapter::class)
@Transactional
class CatalogPersistenceAdapterTest @Autowired constructor(
    private val adapter: CatalogPersistenceAdapter,
) {
    @Test
    fun `persists and reloads complete catalog state`() {
        val property = property()

        adapter.saveAll(listOf(property))

        val reloaded = adapter.findAllBySupplier(supplierA).single()

        assertEquals(property.id, reloaded.id)
        assertEquals(property.status, reloaded.status)
        assertEquals(property.roomTypes.map { it.id }, reloaded.roomTypes.map { it.id })
        assertEquals(property.roomTypes.map { it.status }, reloaded.roomTypes.map { it.status })
    }

    @Test
    fun `search projection includes only active property and room types`() {
        adapter.saveAll(
            listOf(
                property(
                    roomTypes = listOf(
                        roomType(RoomTypeId(10), "active-room", CatalogStatus.ACTIVE),
                        roomType(RoomTypeId(11), "inactive-room", CatalogStatus.INACTIVE),
                    ),
                ),
                property(id = PropertyId(2), status = CatalogStatus.INACTIVE, roomTypes = emptyList()),
            ),
        )

        val result = adapter.readSearchableCatalog()

        assertEquals(1, result.size)
        assertEquals(PropertyId(1), result.single().id)
        assertEquals(listOf(RoomTypeId(10)), result.single().roomTypes.map { it.id })
    }

    @Test
    fun `persisted inactive rows count as catalog baseline`() {
        adapter.saveAll(listOf(property(status = CatalogStatus.INACTIVE)))

        assertEquals(true, adapter.hasPersistedCatalogState(supplierA))
        assertEquals(false, adapter.hasPersistedCatalogState(SupplierId("supplier-b")))
    }

    companion object {
        private val supplierA = SupplierId("supplier-a")

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    private fun property(
        id: PropertyId = PropertyId(1),
        status: CatalogStatus = CatalogStatus.ACTIVE,
        roomTypes: List<RoomType> = listOf(roomType(RoomTypeId(10), "room-a", CatalogStatus.ACTIVE)),
    ) = Property(
        id = id,
        supplierPropertyIdentity = SupplierPropertyIdentity(supplierA, SupplierPropertyCode("property-${id.value}")),
        name = "Property ${id.value}",
        status = status,
        roomTypes = roomTypes,
    )

    private fun roomType(id: RoomTypeId, code: String, status: CatalogStatus) =
        RoomType(id, SupplierRoomTypeCode(code), "$code name", 2, status)
}
