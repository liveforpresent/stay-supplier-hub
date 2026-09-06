package com.staysupplierhub.integration.supplierb

import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyTarget
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.web.reactive.function.client.WebClient

internal class SupplierBAvailabilityClient(
    private val webClient: WebClient,
    private val apiKey: String,
) {
    suspend fun fetch(
        targets: List<SupplierPropertyTarget>,
        condition: SearchCondition,
    ): String {
        require(targets.size <= MAX_PROPERTY_CODES) {
            "A single Supplier B request must contain at most $MAX_PROPERTY_CODES property codes"
        }
        return webClient.get()
            .uri { builder ->
                builder.path(AVAILABILITY_PATH)
                    .queryParam(PROPERTY_IDS_PARAMETER, targets.joinToString(",") { it.supplierPropertyCode.value })
                    .queryParam(CHECK_IN_PARAMETER, condition.stayPeriod.checkIn)
                    .queryParam(CHECK_OUT_PARAMETER, condition.stayPeriod.checkOut)
                    .queryParam(ADULTS_PARAMETER, condition.guestComposition.adults)
                    .queryParam(CHILDREN_PARAMETER, condition.guestComposition.children)
                    .build()
            }
            .header(API_KEY_HEADER, apiKey)
            .retrieve()
            .bodyToMono(String::class.java)
            .awaitSingle()
    }

    private companion object {
        const val AVAILABILITY_PATH = "/b/api/search"
        const val API_KEY_HEADER = "X-Api-Key"
        const val PROPERTY_IDS_PARAMETER = "propertyIds"
        const val CHECK_IN_PARAMETER = "checkIn"
        const val CHECK_OUT_PARAMETER = "checkOut"
        const val ADULTS_PARAMETER = "adults"
        const val CHILDREN_PARAMETER = "children"
        const val MAX_PROPERTY_CODES = 50
    }
}
