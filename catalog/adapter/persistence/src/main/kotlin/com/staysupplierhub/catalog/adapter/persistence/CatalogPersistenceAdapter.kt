package com.staysupplierhub.catalog.adapter.persistence

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.api.SearchableProperty
import com.staysupplierhub.catalog.api.SearchableRoomType
import com.staysupplierhub.catalog.api.SupplierId
import com.staysupplierhub.catalog.domain.CatalogStatus
import com.staysupplierhub.catalog.domain.Property
import com.staysupplierhub.catalog.domain.RoomType
import com.staysupplierhub.catalog.domain.SupplierPropertyCode
import com.staysupplierhub.catalog.domain.SupplierPropertyIdentity
import com.staysupplierhub.catalog.domain.SupplierRoomTypeCode
import com.staysupplierhub.catalog.port.out.persistence.PropertyRepository
import com.staysupplierhub.catalog.port.out.persistence.SearchableCatalogReader
import jakarta.persistence.EntityManager
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

interface SpringDataPropertyRepository : JpaRepository<PropertyJpaEntity, Long> {
    @Query("select distinct p from PropertyJpaEntity p left join fetch p.roomTypes where p.supplierId = :supplierId")
    fun findAllBySupplierWithRoomTypes(@Param("supplierId") supplierId: String): List<PropertyJpaEntity>

    fun existsBySupplierId(supplierId: String): Boolean

    @Query(
        """
        select new com.staysupplierhub.catalog.adapter.persistence.SearchableCatalogRow(
            p.id, p.name, p.supplierId, p.supplierPropertyCode,
            r.id, r.supplierRoomTypeCode, r.name, r.maxOccupancy
        )
        from PropertyJpaEntity p join p.roomTypes r
        where p.status = 'ACTIVE'
          and r.status = 'ACTIVE'
        order by p.id, r.id
        """,
    )
    fun findSearchableRows(): List<SearchableCatalogRow>
}

data class SearchableCatalogRow(
    val propertyId: Long,
    val propertyName: String,
    val supplierId: String,
    val supplierPropertyCode: String,
    val roomTypeId: Long,
    val supplierRoomTypeCode: String,
    val roomTypeName: String,
    val maxOccupancy: Int,
)

@Repository
class CatalogPersistenceAdapter(
    private val repository: SpringDataPropertyRepository,
    private val entityManager: EntityManager,
) : PropertyRepository, SearchableCatalogReader {
    override fun findAllBySupplier(supplierId: SupplierId): List<Property> =
        repository.findAllBySupplierWithRoomTypes(supplierId.value).map { it.toDomain() }

    override fun hasPersistedCatalogState(supplierId: SupplierId): Boolean =
        repository.existsBySupplierId(supplierId.value)

    override fun saveAll(properties: Collection<Property>) {
        repository.saveAll(properties.map { it.toEntity() })
        entityManager.flush()
    }

    override fun readSearchableCatalog(): List<SearchableProperty> =
        repository.findSearchableRows()
            .groupBy { it.propertyId }
            .values
            .map { rows ->
                val first = rows.first()
                SearchableProperty(
                    id = PropertyId(first.propertyId),
                    name = first.propertyName,
                    supplierId = SupplierId(first.supplierId),
                    supplierPropertyCode = first.supplierPropertyCode,
                    roomTypes = rows.map {
                        SearchableRoomType(
                            id = RoomTypeId(it.roomTypeId),
                            supplierRoomTypeCode = it.supplierRoomTypeCode,
                            name = it.roomTypeName,
                            maxOccupancy = it.maxOccupancy,
                        )
                    },
                )
            }

    private fun PropertyJpaEntity.toDomain() = Property(
        id = PropertyId(id),
        supplierPropertyIdentity = SupplierPropertyIdentity(SupplierId(supplierId), SupplierPropertyCode(supplierPropertyCode)),
        name = name,
        status = status,
        roomTypes = roomTypes.map { roomType ->
            RoomType(
                id = RoomTypeId(roomType.id),
                supplierRoomTypeCode = SupplierRoomTypeCode(roomType.supplierRoomTypeCode),
                name = roomType.name,
                maxOccupancy = roomType.maxOccupancy,
                status = roomType.status,
            )
        },
    )

    private fun Property.toEntity(): PropertyJpaEntity {
        val now = Instant.now()
        val entity = PropertyJpaEntity(
            id = id.value,
            supplierId = supplierPropertyIdentity.supplierId.value,
            supplierPropertyCode = supplierPropertyIdentity.supplierPropertyCode.value,
            name = name,
            status = status,
            createdAt = now,
            updatedAt = now,
        )
        entity.roomTypes = roomTypes.map { roomType ->
            RoomTypeJpaEntity(
                id = roomType.id.value,
                property = entity,
                supplierRoomTypeCode = roomType.supplierRoomTypeCode.value,
                name = roomType.name,
                maxOccupancy = roomType.maxOccupancy,
                status = roomType.status,
                createdAt = now,
                updatedAt = now,
            )
        }.toMutableList()
        return entity
    }
}
