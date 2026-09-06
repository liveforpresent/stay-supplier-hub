package com.staysupplierhub.search.domain

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.api.SupplierId

data class OfferConditions(
    val breakfastIncluded: Boolean,
)

data class StayOffer(
    val propertyId: PropertyId,
    val propertyName: String,
    val roomTypeId: RoomTypeId,
    val roomTypeName: String,
    val maxOccupancy: Int,
    val supplierId: SupplierId,
    val price: StayPrice,
    val availability: StayAvailability,
    val conditions: OfferConditions,
)
