package com.staysupplierhub.catalog

import com.staysupplierhub.catalog.application.ApplyCatalogSnapshotService
import com.staysupplierhub.catalog.application.ReadSearchableCatalogService
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.domain.Property
import com.staysupplierhub.catalog.port.out.persistence.PropertyIdGenerator
import com.staysupplierhub.catalog.port.out.persistence.PropertyRepository
import com.staysupplierhub.catalog.port.out.persistence.RoomTypeIdGenerator
import com.staysupplierhub.catalog.port.out.persistence.SearchableCatalogReader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.registerBean
import org.springframework.core.env.MapPropertySource

class CatalogRuntimeCompositionTest : FunSpec({
    test("binds one Snowflake generator to Catalog identity ports and application services") {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.propertySources.addFirst(
                MapPropertySource("test", mapOf("stay-supplier-hub.catalog.snowflake-node-id" to "7")),
            )
            context.registerBean<PropertyRepository> { NoOpPropertyRepository }
            context.registerBean<SearchableCatalogReader> { SearchableCatalogReader { emptyList() } }
            context.register(CatalogRuntimeConfiguration::class.java)
            context.refresh()

            val propertyId = context.getBean(PropertyIdGenerator::class.java).next()
            val roomTypeId = context.getBean(RoomTypeIdGenerator::class.java).next()

            propertyId.value shouldBeGreaterThan 0L
            roomTypeId.value shouldBeGreaterThan 0L
            (propertyId.value == roomTypeId.value) shouldBe false
            context.getBeanNamesForType(ApplyCatalogSnapshotService::class.java).toList() shouldBe listOf("applyCatalogSnapshotService")
            context.getBeanNamesForType(ReadSearchableCatalogService::class.java).toList() shouldBe listOf("readSearchableCatalogService")
        }
    }
})

private object NoOpPropertyRepository : PropertyRepository {
    override fun findAllBySupplier(supplierId: SupplierId) = emptyList<Property>()

    override fun hasPersistedCatalogState(supplierId: SupplierId) = false

    override fun saveAll(properties: Collection<Property>) = Unit
}
