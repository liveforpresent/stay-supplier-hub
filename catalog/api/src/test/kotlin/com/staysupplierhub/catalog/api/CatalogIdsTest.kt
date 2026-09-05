package com.staysupplierhub.catalog.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogIdsTest {
    @Test
    fun `positive internal IDs retain their values`() {
        assertEquals(1L, PropertyId(1).value)
        assertEquals(2L, RoomTypeId(2).value)
    }

    @Test
    fun `non-positive internal IDs are rejected`() {
        assertFailsWith<IllegalArgumentException> { PropertyId(0) }
        assertFailsWith<IllegalArgumentException> { RoomTypeId(-1) }
    }

    @Test
    fun `supplier ID is open but cannot be blank`() {
        assertEquals("supplier-c", SupplierId("supplier-c").value)
        assertFailsWith<IllegalArgumentException> { SupplierId("  ") }
    }
}
