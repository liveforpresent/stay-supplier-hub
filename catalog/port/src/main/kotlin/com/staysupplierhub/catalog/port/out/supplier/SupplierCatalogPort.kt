package com.staysupplierhub.catalog.port.out.supplier

import com.staysupplierhub.catalog.domain.SupplierPropertyCode
import com.staysupplierhub.catalog.domain.SupplierRoomTypeCode

fun interface SupplierCatalogPort {
    fun fetchCatalog(): SupplierCatalogOutcome
}

sealed interface SupplierCatalogOutcome {
    data class Success(val snapshot: SupplierCatalogSnapshot) : SupplierCatalogOutcome

    data class Failed(val failure: CatalogSupplierFailure) : SupplierCatalogOutcome
}

data class SupplierCatalogSnapshot(
    val properties: List<SupplierCatalogProperty>,
)

data class SupplierCatalogProperty(
    val supplierPropertyCode: SupplierPropertyCode,
    val name: String,
    val roomTypes: List<SupplierCatalogRoomType>,
)

data class SupplierCatalogRoomType(
    val supplierRoomTypeCode: SupplierRoomTypeCode,
    val name: String,
    val maxOccupancy: Int,
)

data class CatalogSupplierFailure(
    val type: CatalogSupplierFailureType,
)

enum class CatalogSupplierFailureType {
    INVALID_REQUEST,
    AUTHENTICATION_FAILED,
    RATE_LIMITED,
    SUPPLIER_ERROR,
    SERVICE_UNAVAILABLE,
    CONNECTION_FAILED,
    TIMEOUT,
    INVALID_RESPONSE,
}
