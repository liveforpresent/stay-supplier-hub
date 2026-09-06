package com.staysupplierhub.search.domain

import java.time.LocalDate
import kotlin.streams.toList

data class SearchCondition(
    val stayPeriod: StayPeriod,
    val guestComposition: GuestComposition,
)

data class StayPeriod(
    val checkIn: LocalDate,
    val checkOut: LocalDate,
) {
    init {
        require(checkIn.isBefore(checkOut)) { "checkIn must be before checkOut" }
    }

    val requiredDates: List<LocalDate> = checkIn.datesUntil(checkOut).toList()
}

data class GuestComposition(
    val adults: Int,
    val children: Int,
) {
    init {
        require(adults >= 1) { "adults must be at least one" }
        require(children >= 0) { "children must not be negative" }
    }
}
