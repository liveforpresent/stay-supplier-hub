package com.staysupplierhub.catalog.api

@JvmInline
value class PropertyId(val value: Long) {
    init {
        require(value > 0) { "PropertyId must be positive" }
    }
}

@JvmInline
value class RoomTypeId(val value: Long) {
    init {
        require(value > 0) { "RoomTypeId must be positive" }
    }
}

@JvmInline
value class SupplierId(val value: String) {
    init {
        require(value.isNotBlank()) { "SupplierId must not be blank" }
    }
}
