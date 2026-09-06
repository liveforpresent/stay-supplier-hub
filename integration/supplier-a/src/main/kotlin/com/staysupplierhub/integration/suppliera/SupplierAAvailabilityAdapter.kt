package com.staysupplierhub.integration.suppliera

import com.fasterxml.jackson.databind.ObjectMapper
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailure
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailureType
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityOutcome
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityPort
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyTarget
import io.netty.handler.timeout.ReadTimeoutException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.TimeoutException

class SupplierAAvailabilityAdapter(
    webClient: WebClient,
    apiKey: String,
    objectMapper: ObjectMapper,
    maxBatchConcurrency: Int = DEFAULT_MAX_BATCH_CONCURRENCY,
) : SupplierAvailabilityPort {
    private val client = SupplierAAvailabilityClient(webClient, apiKey)
    private val normalizer = SupplierAAvailabilityNormalizer(objectMapper)
    private val batchConcurrency = Semaphore(maxBatchConcurrency.also {
        require(it > 0) { "Supplier A batch concurrency must be positive" }
    })

    override fun search(
        targets: List<SupplierPropertyTarget>,
        condition: SearchCondition,
    ): SupplierAvailabilityOutcome = runBlocking {
        val batchOutcomes = targets.chunked(MAX_PROPERTY_CODES)
            .map { batch -> async { batchConcurrency.withPermit { searchBatch(batch, condition) } } }
            .awaitAll()
        val completed = batchOutcomes.filterIsInstance<SupplierAvailabilityOutcome.Completed>()
        val failures = batchOutcomes.flatMap { it.failures() }
        if (completed.isNotEmpty()) {
            SupplierAvailabilityOutcome.Completed(
                items = completed.flatMap { it.items },
                failures = failures,
            )
        } else {
            SupplierAvailabilityOutcome.Failed(failures.ifEmpty { listOf(SearchSupplierFailure(SearchSupplierFailureType.INVALID_RESPONSE)) })
        }
    }

    private suspend fun searchBatch(
        targets: List<SupplierPropertyTarget>,
        condition: SearchCondition,
    ): SupplierAvailabilityOutcome =
        try {
            normalizer.normalize(client.fetch(targets, condition), condition)
        } catch (_: IllegalArgumentException) {
            failure(SearchSupplierFailureType.INVALID_REQUEST)
        } catch (exception: WebClientResponseException) {
            failure(exception.statusCode.value().toFailureType())
        } catch (exception: WebClientRequestException) {
            failure(
                if (exception.hasTimeoutCause()) {
                    SearchSupplierFailureType.TIMEOUT
                } else {
                    SearchSupplierFailureType.CONNECTION_FAILED
                },
            )
        } catch (_: Exception) {
            failure(SearchSupplierFailureType.INVALID_RESPONSE)
        }

    private fun SupplierAvailabilityOutcome.failures(): List<SearchSupplierFailure> = when (this) {
        is SupplierAvailabilityOutcome.Completed -> failures
        is SupplierAvailabilityOutcome.Failed -> failures
    }

    private fun Throwable.hasTimeoutCause(): Boolean = generateSequence(this) { it.cause }
        .any { it is TimeoutException || it is ReadTimeoutException }

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

    private companion object {
        const val MAX_PROPERTY_CODES = 50
        const val DEFAULT_MAX_BATCH_CONCURRENCY = 5
    }
}
