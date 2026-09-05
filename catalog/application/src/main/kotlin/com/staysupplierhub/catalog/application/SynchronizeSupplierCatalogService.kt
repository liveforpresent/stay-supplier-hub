package com.staysupplierhub.catalog.application

import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.port.`in`.CatalogSynchronizationResult
import com.staysupplierhub.catalog.port.`in`.SynchronizeSupplierCatalogUseCase
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailure
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailureType
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogOutcome
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogPort

class SynchronizeSupplierCatalogService(
    private val supplierCatalogPorts: Map<SupplierId, SupplierCatalogPort>,
    private val applyCatalogSnapshotService: ApplyCatalogSnapshotService,
) : SynchronizeSupplierCatalogUseCase {
    override fun synchronize(supplierId: SupplierId): CatalogSynchronizationResult {
        val port = requireNotNull(supplierCatalogPorts[supplierId]) {
            "No SupplierCatalogPort is configured for $supplierId"
        }

        return when (val outcome = port.fetchCatalog()) {
            is SupplierCatalogOutcome.Failed -> CatalogSynchronizationResult.Failed(outcome.failure)
            is SupplierCatalogOutcome.Success -> {
                if (!applyCatalogSnapshotService.canApply(outcome.snapshot)) {
                    CatalogSynchronizationResult.Failed(
                        CatalogSupplierFailure(CatalogSupplierFailureType.INVALID_RESPONSE),
                    )
                } else {
                    applyCatalogSnapshotService.apply(supplierId, outcome.snapshot)
                }
            }
        }
    }
}
