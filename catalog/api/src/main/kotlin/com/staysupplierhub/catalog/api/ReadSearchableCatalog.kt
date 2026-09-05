package com.staysupplierhub.catalog.api

fun interface ReadSearchableCatalog {
    fun read(): List<SearchableProperty>
}

data class SearchableProperty(
    val id: PropertyId,
    val name: String,
    val supplierId: SupplierId,
    val supplierPropertyCode: String,
    val roomTypes: List<SearchableRoomType>,
)

data class SearchableRoomType(
    val id: RoomTypeId,
    val supplierRoomTypeCode: String,
    val name: String,
    val maxOccupancy: Int,
)
