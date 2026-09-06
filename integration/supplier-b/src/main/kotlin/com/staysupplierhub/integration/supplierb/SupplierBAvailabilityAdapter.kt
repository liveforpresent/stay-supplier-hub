package com.staysupplierhub.integration.supplierb

import com.fasterxml.jackson.databind.ObjectMapper
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailure
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailureType
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityOutcome
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityPort
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyTarget
import kotlinx.coroutines.runBlocking
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.TimeoutException

class SupplierBAvailabilityAdapter(
    webClient: WebClient,
    apiKey: String,
    objectMapper: ObjectMapper,
) : SupplierAvailabilityPort {
    private val client = SupplierBAvailabilityClient(webClient, apiKey)
    private val normalizer = SupplierBAvailabilityNormalizer(objectMapper)

    override fun search(
        targets: List<SupplierPropertyTarget>,
        condition: SearchCondition,
    ): SupplierAvailabilityOutcome = runBlocking {
        try {
            normalizer.normalize(client.fetch(targets, condition), condition)
        } catch (_: IllegalArgumentException) {
            failure(SearchSupplierFailureType.INVALID_REQUEST)
        } catch (exception: WebClientResponseException) {
            failure(exception.statusCode.value().toFailureType())
        } catch (exception: WebClientRequestException) {
            failure(
                if (exception.cause is TimeoutException) {
                    SearchSupplierFailureType.TIMEOUT
                } else {
                    SearchSupplierFailureType.CONNECTION_FAILED
                },
            )
        } catch (_: Exception) {
            failure(SearchSupplierFailureType.INVALID_RESPONSE)
        }
    }

    private fun Int.toFailureType(): SearchSupplierFailureType = when (this) {
        400 -> SearchSupplierFailureType.INVALID_REQUEST
        401 -> SearchSupplierFailureType.AUTHENTICATION_FAILED
        429 -> SearchSupplierFailureType.RATE_LIMITED
        500 -> SearchSupplierFailureType.SUPPLIER_ERROR
        503 -> SearchSupplierFailureType.SERVICE_UNAVAILABLE
        in 500..599 -> SearchSupplierFailureType.SUPPLIER_ERROR
        else -> SearchSupplierFailureType.INVALID_RESPONSE
    }

    private fun failure(type: SearchSupplierFailureType): SupplierAvailabilityOutcome.Failed =
        SupplierAvailabilityOutcome.Failed(listOf(SearchSupplierFailure(type)))
}
