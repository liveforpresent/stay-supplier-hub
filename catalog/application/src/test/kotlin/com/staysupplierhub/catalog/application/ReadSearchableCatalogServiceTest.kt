package com.staysupplierhub.catalog.application

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.api.SearchableProperty
import com.staysupplierhub.catalog.api.SearchableRoomType
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.port.out.persistence.SearchableCatalogReader
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

        val result = ReadSearchableCatalogService(FakeReader(expectedProjection)).read()

        assertEquals(expectedProjection, result)
    }

    @Test
    fun `excludes inactive properties`() {
        val result = ReadSearchableCatalogService(FakeReader(emptyList())).read()

        assertEquals(emptyList(), result)
    }

    @Test
    fun `excludes inactive room types`() {
        val result = ReadSearchableCatalogService(FakeReader(emptyList())).read()

        assertEquals(emptyList(), result)
    }

    @Test
    fun `excludes properties without active room types`() {
        val result = ReadSearchableCatalogService(FakeReader(emptyList())).read()

        assertEquals(emptyList(), result)
    }

    @Test
    fun `returns published projection types rather than catalog aggregate types`() {
        val result = ReadSearchableCatalogService(FakeReader(expectedProjection)).read()

        assertEquals(SearchableProperty::class, result.single()::class)
        assertEquals(SearchableRoomType::class, result.single().roomTypes.single()::class)
    }

    private val expectedProjection = listOf(
        SearchableProperty(
            id = PropertyId(1),
            name = "Property name",
            supplierId = supplierA,
            supplierPropertyCode = "property-a",
            roomTypes = listOf(SearchableRoomType(RoomTypeId(10), "room-a", "room-a name", 2)),
        ),
    )

    private class FakeReader(private val result: List<SearchableProperty>) : SearchableCatalogReader {
        override fun readSearchableCatalog(): List<SearchableProperty> = result
    }
}
