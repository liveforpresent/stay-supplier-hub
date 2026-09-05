package com.staysupplierhub.catalog.domain

import com.staysupplierhub.catalog.api.SupplierId

@JvmInline
value class SupplierPropertyCode(val value: String)

@JvmInline
value class SupplierRoomTypeCode(val value: String)

data class SupplierPropertyIdentity(
    val supplierId: SupplierId,
    val supplierPropertyCode: SupplierPropertyCode,
)
