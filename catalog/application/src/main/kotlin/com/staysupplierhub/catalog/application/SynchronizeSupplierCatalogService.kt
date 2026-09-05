package com.staysupplierhub.catalog.application

import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.domain.Property
import com.staysupplierhub.catalog.domain.RoomTypeReconciliationInput
import com.staysupplierhub.catalog.domain.SupplierPropertyIdentity
import com.staysupplierhub.catalog.port.`in`.CatalogSynchronizationResult
import com.staysupplierhub.catalog.port.`in`.SynchronizeSupplierCatalogUseCase
import com.staysupplierhub.catalog.port.out.persistence.PropertyIdGenerator
import com.staysupplierhub.catalog.port.out.persistence.PropertyRepository
import com.staysupplierhub.catalog.port.out.persistence.RoomTypeIdGenerator
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailure
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailureType
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogOutcome
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogPort
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogProperty
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogSnapshot

class SynchronizeSupplierCatalogService(
    private val supplierCatalogPorts: Map<SupplierId, SupplierCatalogPort>,
    private val propertyRepository: PropertyRepository,
    private val propertyIdGenerator: PropertyIdGenerator,
    private val roomTypeIdGenerator: RoomTypeIdGenerator,
) : SynchronizeSupplierCatalogUseCase {
    override fun synchronize(supplierId: SupplierId): CatalogSynchronizationResult {
        val port = requireNotNull(supplierCatalogPorts[supplierId]) {
            "No SupplierCatalogPort is configured for $supplierId"
        }

        return when (val outcome = port.fetchCatalog()) {
            is SupplierCatalogOutcome.Failed -> CatalogSynchronizationResult.Failed(outcome.failure)
            is SupplierCatalogOutcome.Success -> reconcile(supplierId, outcome.snapshot)
        }
    }

    private fun reconcile(
        supplierId: SupplierId,
        snapshot: SupplierCatalogSnapshot,
    ): CatalogSynchronizationResult {
        if (!snapshot.isValid()) {
            return CatalogSynchronizationResult.Failed(
                CatalogSupplierFailure(CatalogSupplierFailureType.INVALID_RESPONSE),
            )
        }

        val existingByCode = propertyRepository.findAllBySupplier(supplierId)
            .associateBy { it.supplierPropertyIdentity.supplierPropertyCode }
            .toMutableMap()
        val reconciled = snapshot.properties.map { incoming ->
            existingByCode.remove(incoming.supplierPropertyCode)
                ?.also { it.reconcile(incoming.name, incoming.roomTypeInputs(), roomTypeIdGenerator::next) }
                ?: Property(
                    id = propertyIdGenerator.next(),
                    supplierPropertyIdentity = SupplierPropertyIdentity(supplierId, incoming.supplierPropertyCode),
                    name = incoming.name,
                ).also { it.reconcile(incoming.name, incoming.roomTypeInputs(), roomTypeIdGenerator::next) }
        }

        existingByCode.values.forEach(Property::deactivate)
        propertyRepository.saveAll(reconciled + existingByCode.values)
        return CatalogSynchronizationResult.Synchronized
    }

    private fun SupplierCatalogSnapshot.isValid(): Boolean =
        properties.map { it.supplierPropertyCode }.distinct().size == properties.size &&
            properties.all { property ->
                property.supplierPropertyCode.value.isNotBlank() &&
                    property.roomTypes.map { it.supplierRoomTypeCode }.distinct().size == property.roomTypes.size &&
                    property.roomTypes.all { roomType ->
                        roomType.supplierRoomTypeCode.value.isNotBlank() && roomType.maxOccupancy > 0
                    }
            }

    private fun SupplierCatalogProperty.roomTypeInputs(): List<RoomTypeReconciliationInput> =
        roomTypes.map {
            RoomTypeReconciliationInput(
                supplierRoomTypeCode = it.supplierRoomTypeCode,
                name = it.name,
                maxOccupancy = it.maxOccupancy,
            )
        }
}
