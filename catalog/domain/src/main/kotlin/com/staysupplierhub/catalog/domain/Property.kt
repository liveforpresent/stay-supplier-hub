package com.staysupplierhub.catalog.domain

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId

data class RoomTypeReconciliationInput(
    val supplierRoomTypeCode: SupplierRoomTypeCode,
    val name: String,
    val maxOccupancy: Int,
)

class Property(
    val id: PropertyId,
    val supplierPropertyIdentity: SupplierPropertyIdentity,
    name: String,
    status: CatalogStatus = CatalogStatus.ACTIVE,
    roomTypes: List<RoomType> = emptyList(),
) {
    var name: String = name
        private set

    var status: CatalogStatus = status
        private set

    private val roomTypesBySupplierCode = roomTypes.associateByTo(LinkedHashMap()) { it.supplierRoomTypeCode }

    val roomTypes: List<RoomType>
        get() = roomTypesBySupplierCode.values.toList()

    init {
        require(roomTypesBySupplierCode.size == roomTypes.size) {
            "SupplierRoomTypeCode must be unique within a Property"
        }
    }

    fun reconcile(
        name: String,
        roomTypeInputs: List<RoomTypeReconciliationInput>,
        nextRoomTypeId: () -> RoomTypeId,
    ) {
        require(roomTypeInputs.map { it.supplierRoomTypeCode }.distinct().size == roomTypeInputs.size) {
            "SupplierRoomTypeCode must be unique within a Property reconciliation"
        }

        this.name = name
        status = CatalogStatus.ACTIVE

        val incomingCodes = roomTypeInputs.mapTo(mutableSetOf()) { it.supplierRoomTypeCode }
        roomTypeInputs.forEach { input ->
            val existing = roomTypesBySupplierCode[input.supplierRoomTypeCode]
            if (existing == null) {
                roomTypesBySupplierCode[input.supplierRoomTypeCode] = RoomType(
                    id = nextRoomTypeId(),
                    supplierRoomTypeCode = input.supplierRoomTypeCode,
                    name = input.name,
                    maxOccupancy = input.maxOccupancy,
                )
            } else {
                existing.reconcile(input.name, input.maxOccupancy)
            }
        }

        roomTypesBySupplierCode
            .filterKeys { it !in incomingCodes }
            .values
            .forEach(RoomType::deactivate)
    }

    fun deactivate() {
        status = CatalogStatus.INACTIVE
    }
}
