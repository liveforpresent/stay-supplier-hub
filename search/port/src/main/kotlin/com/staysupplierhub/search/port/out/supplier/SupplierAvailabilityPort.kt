package com.staysupplierhub.search.port.out.supplier

import com.staysupplierhub.search.domain.DailyInventory
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayPrice

fun interface SupplierAvailabilityPort {
    fun search(
        targets: List<SupplierPropertyTarget>,
        condition: SearchCondition,
    ): SupplierAvailabilityOutcome
}

@JvmInline
value class SupplierPropertyCode(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Supplier property code must not be blank" }
    }
}

@JvmInline
value class SupplierRoomTypeCode(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Supplier room type code must not be blank" }
    }
}

data class SupplierPropertyTarget(
    val supplierPropertyCode: SupplierPropertyCode,
)

data class SupplierAvailabilityItem(
    val supplierPropertyCode: SupplierPropertyCode,
    val supplierRoomTypeCode: SupplierRoomTypeCode,
    val wholeStayPrice: StayPrice,
    val dailyInventories: List<DailyInventory>,
    val breakfastIncluded: Boolean,
)

sealed interface SupplierAvailabilityOutcome {
    data class Completed(
        val items: List<SupplierAvailabilityItem>,
        val failures: List<SearchSupplierFailure>,
    ) : SupplierAvailabilityOutcome

    data class Failed(
        val failures: List<SearchSupplierFailure>,
    ) : SupplierAvailabilityOutcome
}

data class SearchSupplierFailure(
    val type: SearchSupplierFailureType,
)

enum class SearchSupplierFailureType {
    INVALID_REQUEST,
    AUTHENTICATION_FAILED,
    RATE_LIMITED,
    SUPPLIER_ERROR,
    SERVICE_UNAVAILABLE,
    CONNECTION_FAILED,
    TIMEOUT,
    INVALID_RESPONSE,
}
