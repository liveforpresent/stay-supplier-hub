package com.staysupplierhub.catalog.port.out.persistence

import com.staysupplierhub.catalog.api.SearchableProperty

fun interface SearchableCatalogReader {
    fun readSearchableCatalog(): List<SearchableProperty>
}
