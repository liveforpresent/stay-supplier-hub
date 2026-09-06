package com.staysupplierhub.search.port.`in`

import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayOffer
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailureType

fun interface SearchStaysUseCase {
    fun search(condition: SearchCondition): SearchOutcome
}

sealed interface SearchOutcome {
    data class Result(
        val offers: List<StayOffer>,
        val failures: List<SupplierFailure>,
    ) : SearchOutcome

    data class Unavailable(
        val failures: List<SupplierFailure>,
    ) : SearchOutcome
}

data class SupplierFailure(
    val supplierId: SupplierId,
    val type: SearchSupplierFailureType,
)
