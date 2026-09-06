package com.staysupplierhub.integration.suppliera

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.staysupplierhub.search.domain.GuestComposition
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayPeriod
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailureType
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityOutcome
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate

class SupplierAAvailabilityNormalizerTest : FunSpec({
    test("complete daily rates become a whole-stay price and daily inventory") {
        val outcome = normalizer().normalize(response(dailyRates = validDailyRates()), condition())

        val completed = outcome.shouldBeInstanceOf<SupplierAvailabilityOutcome.Completed>()
        completed.failures shouldBe emptyList()
        completed.items.single().let { item ->
            item.supplierPropertyCode.value shouldBe "hotel-a"
            item.supplierRoomTypeCode.value shouldBe "room-a"
            item.wholeStayPrice.total.amount shouldBe 660L
            item.wholeStayPrice.total.currency shouldBe "KRW"
            item.dailyInventories.map { it.remainingRooms } shouldBe listOf(3, 1, 5)
            item.breakfastIncluded shouldBe true
        }
    }

    test("missing required price date fails as an invalid response") {
        val outcome = normalizer().normalize(response(dailyRates = validDailyRates().dropLast(1)), condition())

        outcome.shouldBeInvalidResponse()
    }

    test("duplicate price date fails as an invalid response") {
        val duplicate = validDailyRates().toMutableList().apply { this[2] = this[0] }

        val outcome = normalizer().normalize(response(dailyRates = duplicate), condition())

        outcome.shouldBeInvalidResponse()
    }

    test("out-of-period price date fails as an invalid response") {
        val outOfPeriod = validDailyRates().toMutableList().apply {
            this[2] = this[2].replace("2026-09-03", "2026-09-04")
        }

        val outcome = normalizer().normalize(response(dailyRates = outOfPeriod), condition())

        outcome.shouldBeInvalidResponse()
    }

    test("invalid items do not discard valid sibling items") {
        val payload =
            """
            { "items": [
              ${item(dailyRates = validDailyRates())},
              ${item(hotelCode = "", dailyRates = validDailyRates())}
            ] }
            """.trimIndent()

        val outcome = normalizer().normalize(payload, condition())

        val completed = outcome.shouldBeInstanceOf<SupplierAvailabilityOutcome.Completed>()
        completed.items.size shouldBe 1
        completed.failures.map { it.type } shouldBe listOf(SearchSupplierFailureType.INVALID_RESPONSE)
    }
})

private fun normalizer() = SupplierAAvailabilityNormalizer(jacksonObjectMapper())

private fun condition() = SearchCondition(
    stayPeriod = StayPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04")),
    guestComposition = GuestComposition(adults = 2, children = 0),
)

private fun response(
    hotelCode: String = "hotel-a",
    dailyRates: List<String>,
): String = """{ "items": [${item(hotelCode, dailyRates)}] }"""

private fun item(
    hotelCode: String = "hotel-a",
    dailyRates: List<String>,
): String =
    """
    {
      "hotelCode": "$hotelCode",
      "roomTypeCode": "room-a",
      "breakfastIncluded": true,
      "currency": "KRW",
      "dailyRates": [${dailyRates.joinToString(",")}]
    }
    """.trimIndent()

private fun validDailyRates(): List<String> = listOf(
    """{ "date": "2026-09-01", "remainingRooms": 3, "nightlyRate": 100, "taxAmount": 10 }""",
    """{ "date": "2026-09-02", "remainingRooms": 1, "nightlyRate": 200, "taxAmount": 20 }""",
    """{ "date": "2026-09-03", "remainingRooms": 5, "nightlyRate": 300, "taxAmount": 30 }""",
)

private fun SupplierAvailabilityOutcome.shouldBeInvalidResponse() {
    val failed = shouldBeInstanceOf<SupplierAvailabilityOutcome.Failed>()
    failed.failures.map { it.type } shouldBe listOf(SearchSupplierFailureType.INVALID_RESPONSE)
}
