package com.staysupplierhub.catalog.application

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.api.SearchableProperty
import com.staysupplierhub.catalog.api.SearchableRoomType
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.domain.CatalogStatus
import com.staysupplierhub.catalog.domain.Property
import com.staysupplierhub.catalog.domain.RoomType
import com.staysupplierhub.catalog.domain.SupplierPropertyCode
import com.staysupplierhub.catalog.domain.SupplierPropertyIdentity
import com.staysupplierhub.catalog.domain.SupplierRoomTypeCode
import com.staysupplierhub.catalog.port.out.persistence.PropertyRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadSearchableCatalogServiceTest {
    private val supplierA = SupplierId("supplier-a")

    @Test
    fun `returns an active property with its active room types as a searchable projection`() {
        val property = property(
            roomTypes = listOf(
                roomType(RoomTypeId(10), "active-room", CatalogStatus.ACTIVE),
                roomType(RoomTypeId(11), "inactive-room", CatalogStatus.INACTIVE),
            ),
        )

        val result = ReadSearchableCatalogService(FakePropertyRepository(property)).read()

        assertEquals(
            listOf(
                SearchableProperty(
                    id = PropertyId(1),
                    name = "Property name",
                    supplierId = supplierA,
                    supplierPropertyCode = "property-a",
                    roomTypes = listOf(
                        SearchableRoomType(RoomTypeId(10), "active-room", "active-room name", 2),
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun `excludes inactive properties`() {
        val inactiveProperty = property(status = CatalogStatus.INACTIVE)

        val result = ReadSearchableCatalogService(FakePropertyRepository(inactiveProperty)).read()

        assertEquals(emptyList(), result)
    }

    @Test
    fun `excludes inactive room types`() {
        val property = property(roomTypes = listOf(roomType(RoomTypeId(10), "inactive-room", CatalogStatus.INACTIVE)))

        val result = ReadSearchableCatalogService(FakePropertyRepository(property)).read()

        assertEquals(emptyList(), result)
    }

    @Test
    fun `excludes properties without active room types`() {
        val property = property(roomTypes = emptyList())

        val result = ReadSearchableCatalogService(FakePropertyRepository(property)).read()

        assertEquals(emptyList(), result)
    }

    @Test
    fun `returns published projection types rather than catalog aggregate types`() {
        val result = ReadSearchableCatalogService(FakePropertyRepository(property())).read()

        assertEquals(SearchableProperty::class, result.single()::class)
        assertEquals(SearchableRoomType::class, result.single().roomTypes.single()::class)
    }

    private fun property(
        status: CatalogStatus = CatalogStatus.ACTIVE,
        roomTypes: List<RoomType> = listOf(roomType(RoomTypeId(10), "room-a", CatalogStatus.ACTIVE)),
    ) = Property(
        id = PropertyId(1),
        supplierPropertyIdentity = SupplierPropertyIdentity(supplierA, SupplierPropertyCode("property-a")),
        name = "Property name",
        status = status,
        roomTypes = roomTypes,
    )

    private fun roomType(id: RoomTypeId, code: String, status: CatalogStatus) =
        RoomType(id, SupplierRoomTypeCode(code), "$code name", 2, status)

    private class FakePropertyRepository(vararg properties: Property) : PropertyRepository {
        private val stored = properties.toList()

        override fun findAll(): List<Property> = stored

        override fun findAllBySupplier(supplierId: SupplierId): List<Property> =
            stored.filter { it.supplierPropertyIdentity.supplierId == supplierId }

        override fun hasPersistedCatalogState(supplierId: SupplierId): Boolean =
            stored.any { it.supplierPropertyIdentity.supplierId == supplierId }

        override fun saveAll(properties: Collection<Property>) = Unit
    }
}
