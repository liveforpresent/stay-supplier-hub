package com.staysupplierhub.catalog.port.`in`

import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailure

interface SynchronizeSupplierCatalogUseCase {
    fun synchronize(supplierId: SupplierId): CatalogSynchronizationResult
}

sealed interface CatalogSynchronizationResult {
    data object Synchronized : CatalogSynchronizationResult

    data class Failed(val failure: CatalogSupplierFailure) : CatalogSynchronizationResult
}
