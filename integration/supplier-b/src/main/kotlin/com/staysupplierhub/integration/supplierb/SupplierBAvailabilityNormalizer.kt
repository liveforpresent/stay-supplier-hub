package com.staysupplierhub.integration.supplierb

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.staysupplierhub.search.domain.DailyInventory
import com.staysupplierhub.search.domain.Money
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayPrice
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailure
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailureType
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityItem
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityOutcome
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyCode
import com.staysupplierhub.search.port.out.supplier.SupplierRoomTypeCode
import java.time.LocalDate

internal class SupplierBAvailabilityNormalizer(
    private val objectMapper: ObjectMapper,
) {
    fun normalize(payload: String, condition: SearchCondition): SupplierAvailabilityOutcome {
        val response = runCatching { objectMapper.readValue<SupplierBAvailabilityResponse>(payload) }
            .getOrElse { return failed(SearchSupplierFailureType.INVALID_RESPONSE) }
        val resultCode = response.resultCode ?: return failed(SearchSupplierFailureType.INVALID_RESPONSE)
        if (resultCode != SUCCESS_CODE) return failed(resultCode.toFailureType())

        val items = response.data?.items ?: return failed(SearchSupplierFailureType.INVALID_RESPONSE)
        if (items.isEmpty()) return SupplierAvailabilityOutcome.Completed(emptyList(), emptyList())

        val normalizedItems = mutableListOf<SupplierAvailabilityItem>()
        val failures = mutableListOf<SearchSupplierFailure>()
        items.forEach { item ->
            runCatching { item.normalize(condition) }
                .onSuccess(normalizedItems::add)
                .onFailure { failures += invalidResponseFailure() }
        }
        return if (normalizedItems.isNotEmpty()) {
            SupplierAvailabilityOutcome.Completed(normalizedItems, failures)
        } else {
            SupplierAvailabilityOutcome.Failed(failures.ifEmpty { listOf(invalidResponseFailure()) })
        }
    }

    private fun SupplierBAvailabilityItemResponse.normalize(condition: SearchCondition): SupplierAvailabilityItem =
        SupplierAvailabilityItem(
            supplierPropertyCode = SupplierPropertyCode(propertyId?.takeIf(String::isNotBlank) ?: invalid()),
            supplierRoomTypeCode = SupplierRoomTypeCode(roomId?.takeIf(String::isNotBlank) ?: invalid()),
            wholeStayPrice = StayPrice(Money(totalPrice ?: invalid(), currency?.takeIf(String::isNotBlank) ?: invalid())),
            dailyInventories = inventory?.map { it.normalize() } ?: invalid(),
            breakfastIncluded = breakfastIncluded ?: invalid(),
        ).also {
            require(taxIncluded == true) { "Supplier B total price must include tax" }
        }

    private fun SupplierBInventoryResponse.normalize() = DailyInventory(
        date = date?.let(LocalDate::parse) ?: invalid(),
        remainingRooms = remainingRooms ?: invalid(),
    )

    private fun String.toFailureType(): SearchSupplierFailureType = when (this) {
        "E400" -> SearchSupplierFailureType.INVALID_REQUEST
        "E401" -> SearchSupplierFailureType.AUTHENTICATION_FAILED
        "E429" -> SearchSupplierFailureType.RATE_LIMITED
        "E500" -> SearchSupplierFailureType.SUPPLIER_ERROR
        "E503" -> SearchSupplierFailureType.SERVICE_UNAVAILABLE
        else -> SearchSupplierFailureType.INVALID_RESPONSE
    }

    private fun failed(type: SearchSupplierFailureType) =
        SupplierAvailabilityOutcome.Failed(listOf(SearchSupplierFailure(type)))

    private fun invalidResponseFailure() = SearchSupplierFailure(SearchSupplierFailureType.INVALID_RESPONSE)

    private fun invalid(): Nothing = throw IllegalArgumentException("Supplier B availability response is invalid")

    private companion object {
        const val SUCCESS_CODE = "0000"
    }
}

internal data class SupplierBAvailabilityResponse(
    val resultCode: String? = null,
    val resultMessage: String? = null,
    val data: SupplierBAvailabilityDataResponse? = null,
)

internal data class SupplierBAvailabilityDataResponse(
    val items: List<SupplierBAvailabilityItemResponse>? = null,
)

internal data class SupplierBAvailabilityItemResponse(
    val propertyId: String? = null,
    val roomId: String? = null,
    val breakfastIncluded: Boolean? = null,
    val currency: String? = null,
    val totalPrice: Long? = null,
    val taxIncluded: Boolean? = null,
    val inventory: List<SupplierBInventoryResponse>? = null,
)

internal data class SupplierBInventoryResponse(
    val date: String? = null,
    val remainingRooms: Int? = null,
)
