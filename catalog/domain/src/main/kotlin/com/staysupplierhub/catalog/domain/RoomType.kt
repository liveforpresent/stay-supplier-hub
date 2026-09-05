package com.staysupplierhub.catalog.domain

import com.staysupplierhub.catalog.api.RoomTypeId

class RoomType(
    val id: RoomTypeId,
    val supplierRoomTypeCode: SupplierRoomTypeCode,
    name: String,
    maxOccupancy: Int,
    status: CatalogStatus = CatalogStatus.ACTIVE,
) {
    var name: String = name
        private set

    var maxOccupancy: Int = maxOccupancy
        private set

    var status: CatalogStatus = status
        private set

    init {
        require(maxOccupancy > 0) { "maxOccupancy must be positive" }
    }

    internal fun reconcile(name: String, maxOccupancy: Int) {
        require(maxOccupancy > 0) { "maxOccupancy must be positive" }

        this.name = name
        this.maxOccupancy = maxOccupancy
        status = CatalogStatus.ACTIVE
    }

    internal fun deactivate() {
        status = CatalogStatus.INACTIVE
    }
}
