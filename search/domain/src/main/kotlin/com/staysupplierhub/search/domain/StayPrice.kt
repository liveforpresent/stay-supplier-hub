package com.staysupplierhub.search.domain

data class Money(
    val amount: Long,
    val currency: String,
)

data class StayPrice(
    val total: Money,
)
