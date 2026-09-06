package com.staysupplierhub.integration.suppliera

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

internal class SupplierAAvailabilityNormalizer(
    private val objectMapper: ObjectMapper,
) {
    fun normalize(payload: String, condition: SearchCondition): SupplierAvailabilityOutcome {
        val response = runCatching { objectMapper.readValue<SupplierAAvailabilityResponse>(payload) }
            .getOrElse { return failed() }
        val items = response.items ?: return failed()
        if (items.isEmpty()) {
            return SupplierAvailabilityOutcome.Completed(emptyList(), emptyList())
        }

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

    private fun SupplierAAvailabilityItemResponse.normalize(condition: SearchCondition): SupplierAvailabilityItem {
        val propertyCode = SupplierPropertyCode(hotelCode?.takeIf(String::isNotBlank) ?: invalid())
        val roomTypeCode = SupplierRoomTypeCode(roomTypeCode?.takeIf(String::isNotBlank) ?: invalid())
        val breakfast = breakfastIncluded ?: invalid()
        val itemCurrency = currency?.takeIf(String::isNotBlank) ?: invalid()
        val rates = dailyRates ?: invalid()
        val normalizedRates = rates.map { it.normalize() }
        val rateDates = normalizedRates.map(NormalizedDailyRate::date)
        require(rateDates.distinct().size == rateDates.size) { "price dates must be unique" }
        require(rateDates.toSet() == condition.stayPeriod.requiredDates.toSet()) {
            "price dates must match the stay period"
        }
        val totalAmount = normalizedRates.fold(0L) { total, rate ->
            Math.addExact(total, Math.addExact(rate.nightlyRate, rate.taxAmount))
        }
        return SupplierAvailabilityItem(
            supplierPropertyCode = propertyCode,
            supplierRoomTypeCode = roomTypeCode,
            wholeStayPrice = StayPrice(Money(totalAmount, itemCurrency)),
            dailyInventories = normalizedRates.map { DailyInventory(it.date, it.remainingRooms) },
            breakfastIncluded = breakfast,
        )
    }

    private fun SupplierADailyRateResponse.normalize(): NormalizedDailyRate = NormalizedDailyRate(
        date = date?.let(LocalDate::parse) ?: invalid(),
        remainingRooms = remainingRooms ?: invalid(),
        nightlyRate = nightlyRate ?: invalid(),
        taxAmount = taxAmount ?: invalid(),
    )

    private fun failed(): SupplierAvailabilityOutcome.Failed =
        SupplierAvailabilityOutcome.Failed(listOf(invalidResponseFailure()))

    private fun invalidResponseFailure() = SearchSupplierFailure(SearchSupplierFailureType.INVALID_RESPONSE)

    private fun invalid(): Nothing = throw IllegalArgumentException("Supplier A availability response is invalid")
}

private data class NormalizedDailyRate(
    val date: LocalDate,
    val remainingRooms: Int,
    val nightlyRate: Long,
    val taxAmount: Long,
)

internal data class SupplierAAvailabilityResponse(
    val items: List<SupplierAAvailabilityItemResponse>? = null,
)

internal data class SupplierAAvailabilityItemResponse(
    val hotelCode: String? = null,
    val roomTypeCode: String? = null,
    val breakfastIncluded: Boolean? = null,
    val currency: String? = null,
    val dailyRates: List<SupplierADailyRateResponse>? = null,
)

internal data class SupplierADailyRateResponse(
    val date: String? = null,
    val remainingRooms: Int? = null,
    val nightlyRate: Long? = null,
    val taxAmount: Long? = null,
)
