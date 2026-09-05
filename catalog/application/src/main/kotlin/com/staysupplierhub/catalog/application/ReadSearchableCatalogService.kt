package com.staysupplierhub.catalog.application

import com.staysupplierhub.catalog.api.ReadSearchableCatalog
import com.staysupplierhub.catalog.api.SearchableProperty
import com.staysupplierhub.catalog.api.SearchableRoomType
import com.staysupplierhub.catalog.port.out.persistence.SearchableCatalogReader

class ReadSearchableCatalogService(
    private val searchableCatalogReader: SearchableCatalogReader,
) : ReadSearchableCatalog {
    override fun read(): List<SearchableProperty> = searchableCatalogReader.readSearchableCatalog()
}
