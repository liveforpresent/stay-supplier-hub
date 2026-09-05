package com.staysupplierhub.catalog.application

import com.staysupplierhub.catalog.api.ReadSearchableCatalog
import com.staysupplierhub.catalog.api.SearchableProperty
import com.staysupplierhub.catalog.api.SearchableRoomType
import com.staysupplierhub.catalog.domain.CatalogStatus
import com.staysupplierhub.catalog.domain.Property
import com.staysupplierhub.catalog.port.out.persistence.PropertyRepository

class ReadSearchableCatalogService(
    private val propertyRepository: PropertyRepository,
) : ReadSearchableCatalog {
    override fun read(): List<SearchableProperty> =
        propertyRepository.findAll()
            .asSequence()
            .filter { it.status == CatalogStatus.ACTIVE }
            .mapNotNull { it.toSearchableProperty() }
            .toList()

    private fun Property.toSearchableProperty(): SearchableProperty? {
        val searchableRoomTypes = roomTypes
            .asSequence()
            .filter { it.status == CatalogStatus.ACTIVE }
            .map {
                SearchableRoomType(
                    id = it.id,
                    supplierRoomTypeCode = it.supplierRoomTypeCode.value,
                    name = it.name,
                    maxOccupancy = it.maxOccupancy,
                )
            }
            .toList()

        return searchableRoomTypes.takeIf { it.isNotEmpty() }?.let {
            SearchableProperty(
                id = id,
                name = name,
                supplierId = supplierPropertyIdentity.supplierId,
                supplierPropertyCode = supplierPropertyIdentity.supplierPropertyCode.value,
                roomTypes = it,
            )
        }
    }
}
