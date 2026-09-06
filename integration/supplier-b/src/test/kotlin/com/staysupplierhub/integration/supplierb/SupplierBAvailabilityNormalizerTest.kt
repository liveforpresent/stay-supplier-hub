package com.staysupplierhub.integration.supplierb

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

class SupplierBAvailabilityNormalizerTest : FunSpec({
    test("resultCode 0000 normalizes a successful item") {
        val outcome = normalizer().normalize(successResponse(), condition())

        val completed = outcome.shouldBeInstanceOf<SupplierAvailabilityOutcome.Completed>()
        completed.failures shouldBe emptyList()
        completed.items.single().let { item ->
            item.supplierPropertyCode.value shouldBe "property-a"
            item.supplierRoomTypeCode.value shouldBe "room-a"
            item.wholeStayPrice.total.amount shouldBe 125_000L
            item.wholeStayPrice.total.currency shouldBe "KRW"
            item.dailyInventories.map { it.remainingRooms } shouldBe listOf(3, 1)
            item.breakfastIncluded shouldBe true
        }
    }

    listOf(
        "E400" to SearchSupplierFailureType.INVALID_REQUEST,
        "E401" to SearchSupplierFailureType.AUTHENTICATION_FAILED,
        "E429" to SearchSupplierFailureType.RATE_LIMITED,
        "E500" to SearchSupplierFailureType.SUPPLIER_ERROR,
        "E503" to SearchSupplierFailureType.SERVICE_UNAVAILABLE,
    ).forEach { (resultCode, failureType) ->
        test("resultCode $resultCode maps to $failureType") {
            normalizer().normalize("""{ "resultCode": "$resultCode", "data": null }""", condition())
                .shouldHaveSingleFailure(failureType)
        }
    }

    test("total price is preserved without a fabricated breakdown") {
        val outcome = normalizer().normalize(successResponse(totalPrice = 95_001), condition())

        val item = outcome.shouldBeInstanceOf<SupplierAvailabilityOutcome.Completed>().items.single()
        item.wholeStayPrice.total.amount shouldBe 95_001L
        item.wholeStayPrice.total.currency shouldBe "KRW"
    }

    test("tax excluded price is rejected as invalid response") {
        normalizer().normalize(successResponse(taxIncluded = false), condition())
            .shouldHaveSingleFailure(SearchSupplierFailureType.INVALID_RESPONSE)
    }
})

private fun normalizer() = SupplierBAvailabilityNormalizer(jacksonObjectMapper())

private fun condition() = SearchCondition(
    stayPeriod = StayPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-03")),
    guestComposition = GuestComposition(adults = 2, children = 0),
)

private fun successResponse(totalPrice: Long = 125_000, taxIncluded: Boolean = true): String =
    """
    {
      "resultCode": "0000",
      "resultMessage": "success",
      "data": {
        "items": [
          {
            "propertyId": "property-a",
            "roomId": "room-a",
            "breakfastIncluded": true,
            "currency": "KRW",
            "totalPrice": $totalPrice,
            "taxIncluded": $taxIncluded,
            "inventory": [
              { "date": "2026-09-01", "remainingRooms": 3 },
              { "date": "2026-09-02", "remainingRooms": 1 }
            ]
          }
        ]
      }
    }
    """.trimIndent()

private fun SupplierAvailabilityOutcome.shouldHaveSingleFailure(type: SearchSupplierFailureType) {
    val failed = shouldBeInstanceOf<SupplierAvailabilityOutcome.Failed>()
    failed.failures.map { it.type } shouldBe listOf(type)
}
