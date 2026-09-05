package com.staysupplierhub.catalog.adapter.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import com.staysupplierhub.catalog.domain.CatalogStatus

@Entity
@Table(
    name = "properties",
    uniqueConstraints = [UniqueConstraint(name = "uk_properties_supplier_code", columnNames = ["supplier_id", "supplier_property_code"])],
)
open class PropertyJpaEntity(
    @Id
    @Column(name = "id", nullable = false)
    open var id: Long = 0,
    @Column(name = "supplier_id", nullable = false)
    open var supplierId: String = "",
    @Column(name = "supplier_property_code", nullable = false)
    open var supplierPropertyCode: String = "",
    @Column(name = "name", nullable = false)
    open var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    open var status: CatalogStatus = CatalogStatus.ACTIVE,
    @OneToMany(mappedBy = "property", cascade = [CascadeType.PERSIST, CascadeType.MERGE], fetch = FetchType.LAZY)
    open var roomTypes: MutableList<RoomTypeJpaEntity> = mutableListOf(),
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(
    name = "room_types",
    uniqueConstraints = [UniqueConstraint(name = "uk_room_types_property_code", columnNames = ["property_id", "supplier_room_type_code"])],
)
open class RoomTypeJpaEntity(
    @Id
    @Column(name = "id", nullable = false)
    open var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    open var property: PropertyJpaEntity? = null,
    @Column(name = "supplier_room_type_code", nullable = false)
    open var supplierRoomTypeCode: String = "",
    @Column(name = "name", nullable = false)
    open var name: String = "",
    @Column(name = "max_occupancy", nullable = false)
    open var maxOccupancy: Int = 1,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    open var status: CatalogStatus = CatalogStatus.ACTIVE,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)
