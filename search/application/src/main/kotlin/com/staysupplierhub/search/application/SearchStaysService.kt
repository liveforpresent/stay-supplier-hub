package com.staysupplierhub.search.application

import com.staysupplierhub.catalog.api.ReadSearchableCatalog
import com.staysupplierhub.catalog.api.SearchableProperty
import com.staysupplierhub.catalog.api.SearchableRoomType
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.search.domain.OfferConditions
import com.staysupplierhub.search.domain.SearchCondition
import com.staysupplierhub.search.domain.StayAvailability
import com.staysupplierhub.search.domain.StayOffer
import com.staysupplierhub.search.port.`in`.SearchOutcome
import com.staysupplierhub.search.port.`in`.SearchStaysUseCase
import com.staysupplierhub.search.port.`in`.SupplierFailure
import com.staysupplierhub.search.port.out.supplier.SearchSupplierFailureType
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityItem
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityOutcome
import com.staysupplierhub.search.port.out.supplier.SupplierAvailabilityPort
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyCode
import com.staysupplierhub.search.port.out.supplier.SupplierPropertyTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

class SearchStaysService(
    private val readSearchableCatalog: ReadSearchableCatalog,
    private val supplierPorts: Map<SupplierId, SupplierAvailabilityPort>,
) : SearchStaysUseCase {
    override fun search(condition: SearchCondition): SearchOutcome {
        val properties = readSearchableCatalog.read()
        if (properties.isEmpty()) return SearchOutcome.Result(emptyList(), emptyList())

        val groups = properties.groupBy(SearchableProperty::supplierId)
        val executions = runBlocking {
            groups.map { (supplierId, supplierProperties) ->
                async(Dispatchers.Default) {
                    val port = requireNotNull(supplierPorts[supplierId]) {
                        "No availability port registered for supplier ${supplierId.value}"
                    }
                    SupplierExecution(
                        supplierId,
                        supplierProperties,
                        port.search(supplierProperties.map { SupplierPropertyTarget(SupplierPropertyCode(it.supplierPropertyCode)) }, condition),
                    )
                }
            }.awaitAll()
        }

        val successful = executions.filter { it.outcome is SupplierAvailabilityOutcome.Completed }
        val failures = executions.flatMap { execution -> execution.outcome.failures().map { SupplierFailure(execution.supplierId, it.type) } }
        if (successful.isEmpty()) return SearchOutcome.Unavailable(failures)

        val offers = successful.flatMap { execution ->
            val completed = execution.outcome as SupplierAvailabilityOutcome.Completed
            val catalogRooms = execution.properties.flatMap { property -> property.roomTypes.map { room -> CatalogRoom(property, room) } }
            completed.items.mapNotNull { item -> item.toOfferOrNull(catalogRooms, condition) }
        }
        val invalidItemFailures = successful.flatMap { execution ->
            val completed = execution.outcome as SupplierAvailabilityOutcome.Completed
            val catalogRooms = execution.properties.flatMap { property -> property.roomTypes.map { room -> CatalogRoom(property, room) } }
            completed.items.filter { it.toOfferOrNull(catalogRooms, condition) == null }
                .map { SupplierFailure(execution.supplierId, SearchSupplierFailureType.INVALID_RESPONSE) }
        }
        return SearchOutcome.Result(offers, failures + invalidItemFailures)
    }

    private fun SupplierAvailabilityOutcome.failures() = when (this) {
        is SupplierAvailabilityOutcome.Completed -> failures
        is SupplierAvailabilityOutcome.Failed -> failures
    }

    private fun SupplierAvailabilityItem.toOfferOrNull(
        catalogRooms: List<CatalogRoom>,
        condition: SearchCondition,
    ): StayOffer? {
        val catalogRoom = catalogRooms.singleOrNull {
            it.property.supplierPropertyCode == supplierPropertyCode.value &&
                it.room.supplierRoomTypeCode == supplierRoomTypeCode.value
        } ?: return null
        if (condition.guestComposition.adults + condition.guestComposition.children > catalogRoom.room.maxOccupancy) return null
        val availability = runCatching { StayAvailability.from(condition.stayPeriod, dailyInventories) }.getOrNull() ?: return null
        return StayOffer(
            propertyId = catalogRoom.property.id,
            propertyName = catalogRoom.property.name,
            roomTypeId = catalogRoom.room.id,
            roomTypeName = catalogRoom.room.name,
            maxOccupancy = catalogRoom.room.maxOccupancy,
            supplierId = catalogRoom.property.supplierId,
            price = wholeStayPrice,
            availability = availability,
            conditions = OfferConditions(breakfastIncluded),
        )
    }

    private data class SupplierExecution(
        val supplierId: SupplierId,
        val properties: List<SearchableProperty>,
        val outcome: SupplierAvailabilityOutcome,
    )

    private data class CatalogRoom(
        val property: SearchableProperty,
        val room: SearchableRoomType,
    )
}
