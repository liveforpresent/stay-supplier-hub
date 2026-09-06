package com.staysupplierhub.search.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class StayPriceTest {
    @Test
    fun `stay price preserves the whole-stay amount and currency`() {
        val price = StayPrice(Money(amount = 429_000, currency = "KRW"))

        assertEquals(429_000L, price.total.amount)
        assertEquals("KRW", price.total.currency)
    }

    @Test
    fun `prices in different currencies remain separate without conversion`() {
        val won = StayPrice(Money(amount = 429_000, currency = "KRW"))
        val dollar = StayPrice(Money(amount = 300, currency = "USD"))

        assertEquals(429_000L, won.total.amount)
        assertEquals("KRW", won.total.currency)
        assertEquals(300L, dollar.total.amount)
        assertEquals("USD", dollar.total.currency)
    }
}
