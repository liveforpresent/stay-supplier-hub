package com.staysupplierhub.catalog.application

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.domain.CatalogStatus
import com.staysupplierhub.catalog.domain.Property
import com.staysupplierhub.catalog.domain.RoomType
import com.staysupplierhub.catalog.domain.SupplierPropertyCode
import com.staysupplierhub.catalog.domain.SupplierPropertyIdentity
import com.staysupplierhub.catalog.domain.SupplierRoomTypeCode
import com.staysupplierhub.catalog.port.`in`.CatalogSynchronizationResult
import com.staysupplierhub.catalog.port.out.persistence.PropertyIdGenerator
import com.staysupplierhub.catalog.port.out.persistence.PropertyRepository
import com.staysupplierhub.catalog.port.out.persistence.RoomTypeIdGenerator
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailure
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailureType
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogOutcome
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogPort
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogProperty
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogRoomType
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SynchronizeSupplierCatalogServiceTest {
    private val supplierA = SupplierId("supplier-a")
    private val supplierB = SupplierId("supplier-b")

    @Test
    fun `first sync creates active stable identities`() {
        val repository = FakePropertyRepository()
        val service = service(repository, supplierA to success(snapshot("property-1", "room-1")))

        val result = service.synchronize(supplierA)

        assertIs<CatalogSynchronizationResult.Synchronized>(result)
        val saved = repository.propertiesFor(supplierA).single()
        assertEquals(PropertyId(100), saved.id)
        assertEquals(CatalogStatus.ACTIVE, saved.status)
        assertEquals(RoomTypeId(200), saved.roomTypes.single().id)
        assertEquals(CatalogStatus.ACTIVE, saved.roomTypes.single().status)
    }

    @Test
    fun `same complete snapshot preserves property and room type IDs`() {
        val repository = FakePropertyRepository()
        val service = service(repository, supplierA to success(snapshot("property-1", "room-1")))

        service.synchronize(supplierA)
        service.synchronize(supplierA)

        val saved = repository.propertiesFor(supplierA).single()
        assertEquals(PropertyId(100), saved.id)
        assertEquals(RoomTypeId(200), saved.roomTypes.single().id)
    }

    @Test
    fun `property missing from valid snapshot becomes inactive`() {
        val repository = FakePropertyRepository(existingProperty(supplierA))
        val service = service(repository, supplierA to success(SupplierCatalogSnapshot(emptyList())))

        service.synchronize(supplierA)

        assertEquals(CatalogStatus.INACTIVE, repository.propertiesFor(supplierA).single().status)
    }

    @Test
    fun `inactive property returns with the same ID`() {
        val existing = existingProperty(supplierA).also { it.deactivate() }
        val repository = FakePropertyRepository(existing)
        val service = service(repository, supplierA to success(snapshot("property-1", "room-1")))

        service.synchronize(supplierA)

        val saved = repository.propertiesFor(supplierA).single()
        assertEquals(PropertyId(10), saved.id)
        assertEquals(CatalogStatus.ACTIVE, saved.status)
    }

    @Test
    fun `catalog fetch failure leaves state untouched`() {
        val existing = existingProperty(supplierA)
        val repository = FakePropertyRepository(existing)
        val service = service(repository, supplierA to SupplierCatalogOutcome.Failed(failure(CatalogSupplierFailureType.TIMEOUT)))

        val result = service.synchronize(supplierA)

        assertEquals(CatalogSynchronizationResult.Failed(failure(CatalogSupplierFailureType.TIMEOUT)), result)
        assertEquals(0, repository.saveCalls)
        assertEquals(CatalogStatus.ACTIVE, repository.propertiesFor(supplierA).single().status)
    }

    @Test
    fun `invalid snapshot leaves state untouched`() {
        val existing = existingProperty(supplierA)
        val repository = FakePropertyRepository(existing)
        val invalid = SupplierCatalogSnapshot(listOf(snapshotProperty("property-1", "room-1"), snapshotProperty("property-1", "room-2")))
        val service = service(repository, supplierA to success(invalid))

        val result = service.synchronize(supplierA)

        assertEquals(CatalogSynchronizationResult.Failed(failure(CatalogSupplierFailureType.INVALID_RESPONSE)), result)
        assertEquals(0, repository.saveCalls)
        assertEquals(CatalogStatus.ACTIVE, repository.propertiesFor(supplierA).single().status)
    }

    @Test
    fun `valid empty snapshot deactivates every known property`() {
        val repository = FakePropertyRepository(existingProperty(supplierA), existingProperty(supplierA, "property-2", PropertyId(11)))
        val service = service(repository, supplierA to success(SupplierCatalogSnapshot(emptyList())))

        service.synchronize(supplierA)

        assertEquals(listOf(CatalogStatus.INACTIVE, CatalogStatus.INACTIVE), repository.propertiesFor(supplierA).map { it.status })
    }

    @Test
    fun `one supplier failure does not prevent another supplier synchronization`() {
        val repository = FakePropertyRepository(existingProperty(supplierA))
        val service = service(
            repository,
            supplierA to SupplierCatalogOutcome.Failed(failure(CatalogSupplierFailureType.TIMEOUT)),
            supplierB to success(snapshot("property-b", "room-b")),
        )

        service.synchronize(supplierA)
        val result = service.synchronize(supplierB)

        assertIs<CatalogSynchronizationResult.Synchronized>(result)
        assertEquals(CatalogStatus.ACTIVE, repository.propertiesFor(supplierA).single().status)
        assertEquals(PropertyId(100), repository.propertiesFor(supplierB).single().id)
    }

    private fun service(repository: FakePropertyRepository, vararg outcomes: Pair<SupplierId, SupplierCatalogOutcome>) =
        SynchronizeSupplierCatalogService(
            supplierCatalogPorts = outcomes.associate { (supplierId, outcome) -> supplierId to SupplierCatalogPort { outcome } },
            applyCatalogSnapshotService = ApplyCatalogSnapshotService(
                propertyRepository = repository,
                propertyIdGenerator = SequencePropertyIdGenerator(),
                roomTypeIdGenerator = SequenceRoomTypeIdGenerator(),
            ),
        )

    private fun success(snapshot: SupplierCatalogSnapshot) = SupplierCatalogOutcome.Success(snapshot)

    private fun failure(type: CatalogSupplierFailureType) = CatalogSupplierFailure(type)

    private fun snapshot(propertyCode: String, roomCode: String) = SupplierCatalogSnapshot(listOf(snapshotProperty(propertyCode, roomCode)))

    private fun snapshotProperty(propertyCode: String, roomCode: String) = SupplierCatalogProperty(
        supplierPropertyCode = SupplierPropertyCode(propertyCode),
        name = propertyCode,
        roomTypes = listOf(
            SupplierCatalogRoomType(
                supplierRoomTypeCode = SupplierRoomTypeCode(roomCode),
                name = roomCode,
                maxOccupancy = 2,
            ),
        ),
    )

    private fun existingProperty(supplierId: SupplierId, propertyCode: String = "property-1", id: PropertyId = PropertyId(10)) =
        Property(
            id = id,
            supplierPropertyIdentity = SupplierPropertyIdentity(supplierId, SupplierPropertyCode(propertyCode)),
            name = propertyCode,
            roomTypes = listOf(
                RoomType(RoomTypeId(20), SupplierRoomTypeCode("room-1"), "room-1", 2),
            ),
        )

    private class FakePropertyRepository(vararg initialProperties: Property) : PropertyRepository {
        private val properties = initialProperties.toMutableList()
        var saveCalls = 0
            private set

        override fun findAllBySupplier(supplierId: SupplierId): List<Property> =
            properties.filter { it.supplierPropertyIdentity.supplierId == supplierId }

        override fun hasPersistedCatalogState(supplierId: SupplierId): Boolean = properties.any {
            it.supplierPropertyIdentity.supplierId == supplierId
        }

        override fun saveAll(properties: Collection<Property>) {
            saveCalls++
            properties.forEach { saved ->
                this.properties.removeIf { it.id == saved.id }
                this.properties += saved
            }
        }

        fun propertiesFor(supplierId: SupplierId): List<Property> = findAllBySupplier(supplierId)
    }

    private class SequencePropertyIdGenerator : PropertyIdGenerator {
        private var next = 100L
        override fun next() = PropertyId(next++)
    }

    private class SequenceRoomTypeIdGenerator : RoomTypeIdGenerator {
        private var next = 200L
        override fun next() = RoomTypeId(next++)
    }
}
