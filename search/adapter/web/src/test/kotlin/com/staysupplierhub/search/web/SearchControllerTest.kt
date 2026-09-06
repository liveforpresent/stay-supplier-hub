package com.staysupplierhub.search.web

import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.search.domain.DailyInventory
import com.staysupplierhub.search.domain.Money
import com.staysupplierhub.search.domain.OfferConditions
import com.staysupplierhub.search.domain.StayAvailability
import com.staysupplierhub.search.domain.StayOffer
import com.staysupplierhub.search.domain.StayPeriod
import com.staysupplierhub.search.domain.StayPrice
import com.staysupplierhub.search.port.`in`.SearchOutcome
import com.staysupplierhub.search.port.`in`.SearchStaysUseCase
import com.staysupplierhub.search.port.`in`.SupplierFailure
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailureType
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate

class SearchControllerTest {
    @Test
    fun `offer maps internal ids price and availability to public response`() {
        mvc { SearchOutcome.Result(listOf(offer()), emptyList()) }
            .perform(validRequest())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.offers[0].propertyId").value("101"))
            .andExpect(jsonPath("$.offers[0].roomTypeId").value("202"))
            .andExpect(jsonPath("$.offers[0].supplier").value("A"))
            .andExpect(jsonPath("$.offers[0].price.amount").value(330))
            .andExpect(jsonPath("$.offers[0].price.currency").value("KRW"))
            .andExpect(jsonPath("$.offers[0].availableRooms").value(2))
            .andExpect(jsonPath("$.offers[0].breakfastIncluded").value(true))
    }

    @Test
    fun `zero availability remains a normal offer`() {
        mvc { SearchOutcome.Result(listOf(offer(availableRooms = 0)), emptyList()) }
            .perform(validRequest())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.searchStatus").value("COMPLETE"))
            .andExpect(jsonPath("$.offers[0].availableRooms").value(0))
    }

    @Test
    fun `degraded suppliers are distinct`() {
        mvc {
            SearchOutcome.Result(emptyList(), listOf(
                SupplierFailure(SupplierId("A"), SearchSupplierFailureType.TIMEOUT),
                SupplierFailure(SupplierId("A"), SearchSupplierFailureType.SERVICE_UNAVAILABLE),
            ))
        }
            .perform(validRequest())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.degradedSuppliers.length()").value(1))
            .andExpect(jsonPath("$.degradedSuppliers[0]").value("A"))
    }
    @Test
    fun `empty successful search maps to complete 200 response`() {
        mvc { SearchOutcome.Result(emptyList(), emptyList()) }
            .perform(validRequest())
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.searchStatus").value("COMPLETE"))
            .andExpect(jsonPath("$.offers").isEmpty)
            .andExpect(jsonPath("$.degradedSuppliers").isEmpty)
    }

    @Test
    fun `empty degraded search maps to partial 200 response`() {
        mvc { SearchOutcome.Result(emptyList(), listOf(SupplierFailure(SupplierId("B"), SearchSupplierFailureType.TIMEOUT))) }
            .perform(validRequest())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.searchStatus").value("PARTIAL"))
            .andExpect(jsonPath("$.degradedSuppliers[0]").value("B"))
    }

    @Test
    fun `unavailable search maps to canonical 503`() {
        mvc { SearchOutcome.Unavailable(listOf(SupplierFailure(SupplierId("A"), SearchSupplierFailureType.SERVICE_UNAVAILABLE))) }
            .perform(validRequest())
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.code").value("SEARCH_UNAVAILABLE"))
            .andExpect(jsonPath("$.suppliers[0]").value("A"))
    }

    @Test
    fun `missing parameter maps to canonical 400`() {
        mvc { SearchOutcome.Result(emptyList(), emptyList()) }
            .perform(get("/api/v1/stays/search"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_SEARCH_CONDITION"))
    }

    @Test
    fun `invalid date format maps to canonical 400`() {
        mvc { SearchOutcome.Result(emptyList(), emptyList()) }
            .perform(searchRequest(checkIn = "not-a-date"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_SEARCH_CONDITION"))
    }

    @Test
    fun `non-positive stay period maps to canonical 400`() {
        mvc { SearchOutcome.Result(emptyList(), emptyList()) }
            .perform(searchRequest(checkOut = "2026-09-01"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_SEARCH_CONDITION"))
    }

    @Test
    fun `invalid guest counts map to canonical 400`() {
        mvc { SearchOutcome.Result(emptyList(), emptyList()) }
            .perform(searchRequest(adults = "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_SEARCH_CONDITION"))
        mvc { SearchOutcome.Result(emptyList(), emptyList()) }
            .perform(searchRequest(children = "-1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_SEARCH_CONDITION"))
    }

    @Test
    fun `unexpected failure maps to canonical 500`() {
        mvc { error("unexpected") }
            .perform(validRequest())
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
    }
}

private fun mvc(outcome: (com.staysupplierhub.search.domain.SearchCondition) -> SearchOutcome): MockMvc =
    MockMvcBuilders.standaloneSetup(SearchController(SearchStaysUseCase(outcome)))
        .setControllerAdvice(SearchErrorHandler())
        .build()

private fun validRequest() = searchRequest()

private fun searchRequest(
    checkIn: String = "2026-09-01",
    checkOut: String = "2026-09-02",
    adults: String = "1",
    children: String = "0",
) = get("/api/v1/stays/search")
    .param("checkIn", checkIn)
    .param("checkOut", checkOut)
    .param("adults", adults)
    .param("children", children)

private fun offer(availableRooms: Int = 2) = StayOffer(
    propertyId = PropertyId(101), propertyName = "Property", roomTypeId = RoomTypeId(202), roomTypeName = "Room",
    maxOccupancy = 2, supplierId = SupplierId("A"), price = StayPrice(Money(330, "KRW")),
    availability = StayAvailability.from(
        StayPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-02")),
        listOf(DailyInventory(LocalDate.parse("2026-09-01"), availableRooms)),
    ),
    conditions = OfferConditions(breakfastIncluded = true),
)
