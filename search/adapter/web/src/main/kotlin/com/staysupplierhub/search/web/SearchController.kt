package com.staysupplierhub.search.web

import com.staysupplierhub.search.domain.GuestComposition
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayOffer
import com.staysupplierhub.search.domain.StayPeriod
import com.staysupplierhub.search.port.`in`.SearchOutcome
import com.staysupplierhub.search.port.`in`.SearchStaysUseCase
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.LocalDate

@RestController
class SearchController(
    private val searchStays: SearchStaysUseCase,
) {
    @GetMapping("/api/v1/stays/search")
    fun search(
        @RequestParam("checkIn") checkIn: String,
        @RequestParam("checkOut") checkOut: String,
        @RequestParam("adults") adults: Int,
        @RequestParam("children") children: Int,
    ): ResponseEntity<Any> {
        val condition = SearchCondition(StayPeriod(LocalDate.parse(checkIn), LocalDate.parse(checkOut)), GuestComposition(adults, children))
        return when (val outcome = searchStays.search(condition)) {
            is SearchOutcome.Result -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(outcome.toResponse())
            is SearchOutcome.Unavailable -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(SearchUnavailableResponse(suppliers = outcome.failures.map { it.supplierId.value }.distinct()))
        }
    }
}

data class SearchResponse(val searchStatus: String, val offers: List<OfferResponse>, val degradedSuppliers: List<String>)
data class OfferResponse(val propertyId: String, val propertyName: String, val roomTypeId: String, val roomTypeName: String, val maxOccupancy: Int, val availableRooms: Int, val supplier: String, val price: PriceResponse, val breakfastIncluded: Boolean)
data class PriceResponse(val amount: Long, val currency: String)
data class ApiErrorResponse(val code: String, val message: String)
data class SearchUnavailableResponse(val code: String = "SEARCH_UNAVAILABLE", val message: String = "Availability information is temporarily unavailable.", val suppliers: List<String>)

private fun SearchOutcome.Result.toResponse() = SearchResponse(
    searchStatus = if (failures.isEmpty()) "COMPLETE" else "PARTIAL",
    offers = offers.map(StayOffer::toResponse),
    degradedSuppliers = failures.map { it.supplierId.value }.distinct(),
)

private fun StayOffer.toResponse() = OfferResponse(
    propertyId = propertyId.value.toString(), propertyName = propertyName,
    roomTypeId = roomTypeId.value.toString(), roomTypeName = roomTypeName,
    maxOccupancy = maxOccupancy, availableRooms = availability.availableRooms,
    supplier = supplierId.value, price = PriceResponse(price.total.amount, price.total.currency),
    breakfastIncluded = conditions.breakfastIncluded,
)

@RestControllerAdvice
class SearchErrorHandler {
    @ExceptionHandler(
        IllegalArgumentException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        java.time.format.DateTimeParseException::class,
    )
    fun invalidSearchCondition(): ResponseEntity<ApiErrorResponse> = ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(ApiErrorResponse("INVALID_SEARCH_CONDITION", "Invalid search condition."))

    @ExceptionHandler(Exception::class)
    fun internalError(): ResponseEntity<ApiErrorResponse> = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .cacheControl(CacheControl.noStore())
        .body(ApiErrorResponse("INTERNAL_ERROR", "An unexpected error occurred."))
}
