package com.staysupplierhub.search.domain

import java.time.LocalDate

data class DailyInventory(
    val date: LocalDate,
    val remainingRooms: Int,
) {
    init {
        require(remainingRooms >= 0) { "remaining rooms must not be negative" }
    }
}

class StayAvailability private constructor(
    val availableRooms: Int,
) {
    init {
        require(availableRooms >= 0) { "available rooms must not be negative" }
    }

    companion object {
        fun from(stayPeriod: StayPeriod, dailyInventories: List<DailyInventory>): StayAvailability {
            val inventoryDates = dailyInventories.map(DailyInventory::date)
            require(inventoryDates.distinct().size == inventoryDates.size) {
                "daily inventory dates must be unique"
            }
            require(inventoryDates.toSet() == stayPeriod.requiredDates.toSet()) {
                "daily inventory dates must match the stay period"
            }
            return StayAvailability(dailyInventories.minOf(DailyInventory::remainingRooms))
        }
    }
}
