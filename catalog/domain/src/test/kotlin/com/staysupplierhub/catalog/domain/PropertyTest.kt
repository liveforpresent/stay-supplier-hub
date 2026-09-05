package com.staysupplierhub.catalog.domain

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.api.SupplierId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PropertyTest {
    @Test
    fun `existing room type is updated and keeps its ID`() {
        val property = propertyWith(roomType(status = CatalogStatus.INACTIVE))

        property.reconcile(
            name = "Renewed Property",
            roomTypeInputs = listOf(roomTypeInput(name = "Renewed Room", maxOccupancy = 3)),
            nextRoomTypeId = { RoomTypeId(99) },
        )

        val reconciled = property.roomTypes.single()
        assertEquals(RoomTypeId(10), reconciled.id)
        assertEquals("Renewed Room", reconciled.name)
        assertEquals(3, reconciled.maxOccupancy)
        assertEquals(CatalogStatus.ACTIVE, reconciled.status)
    }

    @Test
    fun `new room type is added as active`() {
        val property = propertyWith()

        property.reconcile(
            name = "Property",
            roomTypeInputs = listOf(roomTypeInput()),
            nextRoomTypeId = { RoomTypeId(20) },
        )

        val added = property.roomTypes.single()
        assertEquals(RoomTypeId(20), added.id)
        assertEquals(CatalogStatus.ACTIVE, added.status)
    }

    @Test
    fun `missing existing room type becomes inactive`() {
        val property = propertyWith(roomType())

        property.reconcile(
            name = "Property",
            roomTypeInputs = emptyList(),
            nextRoomTypeId = { error("No new ID should be needed") },
        )

        assertEquals(CatalogStatus.INACTIVE, property.roomTypes.single().status)
    }

    @Test
    fun `inactive room type reappears with the same ID`() {
        val property = propertyWith(roomType(status = CatalogStatus.INACTIVE))

        property.reconcile(
            name = "Property",
            roomTypeInputs = listOf(roomTypeInput()),
            nextRoomTypeId = { error("Existing room type must not request an ID") },
        )

        val reactivated = property.roomTypes.single()
        assertEquals(RoomTypeId(10), reactivated.id)
        assertEquals(CatalogStatus.ACTIVE, reactivated.status)
    }

    @Test
    fun `duplicate supplier room type code is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            propertyWith(roomType(), roomType(id = RoomTypeId(11)))
        }
    }

    @Test
    fun `reconciliation preserves property ID for the same external identity`() {
        val property = propertyWith()

        property.reconcile(
            name = "Updated Property",
            roomTypeInputs = emptyList(),
            nextRoomTypeId = { error("No new ID should be needed") },
        )

        assertEquals(PropertyId(1), property.id)
        assertEquals(SupplierPropertyIdentity(SupplierId("supplier-a"), SupplierPropertyCode("property-1")), property.supplierPropertyIdentity)
    }

    @Test
    fun `inactive property reappears with the same ID and becomes active`() {
        val property = propertyWith(status = CatalogStatus.INACTIVE)

        property.reconcile(
            name = "Reactivated Property",
            roomTypeInputs = emptyList(),
            nextRoomTypeId = { error("No new ID should be needed") },
        )

        assertEquals(PropertyId(1), property.id)
        assertEquals(CatalogStatus.ACTIVE, property.status)
    }

    private fun propertyWith(
        vararg roomTypes: RoomType,
        status: CatalogStatus = CatalogStatus.ACTIVE,
    ) = Property(
        id = PropertyId(1),
        supplierPropertyIdentity = SupplierPropertyIdentity(
            supplierId = SupplierId("supplier-a"),
            supplierPropertyCode = SupplierPropertyCode("property-1"),
        ),
        name = "Property",
        status = status,
        roomTypes = roomTypes.toList(),
    )

    private fun roomType(
        id: RoomTypeId = RoomTypeId(10),
        status: CatalogStatus = CatalogStatus.ACTIVE,
    ) = RoomType(
        id = id,
        supplierRoomTypeCode = SupplierRoomTypeCode("room-1"),
        name = "Room",
        maxOccupancy = 2,
        status = status,
    )

    private fun roomTypeInput(
        name: String = "Room",
        maxOccupancy: Int = 2,
    ) = RoomTypeReconciliationInput(
        supplierRoomTypeCode = SupplierRoomTypeCode("room-1"),
        name = name,
        maxOccupancy = maxOccupancy,
    )
}
