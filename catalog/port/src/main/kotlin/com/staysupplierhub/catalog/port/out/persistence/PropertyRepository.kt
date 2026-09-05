package com.staysupplierhub.catalog.port.out.persistence

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.domain.Property

interface PropertyRepository {
    fun findAll(): List<Property>

    fun findAllBySupplier(supplierId: SupplierId): List<Property>

    fun hasPersistedCatalogState(supplierId: SupplierId): Boolean

    fun saveAll(properties: Collection<Property>)
}

fun interface PropertyIdGenerator {
    fun next(): PropertyId
}

fun interface RoomTypeIdGenerator {
    fun next(): RoomTypeId
}
