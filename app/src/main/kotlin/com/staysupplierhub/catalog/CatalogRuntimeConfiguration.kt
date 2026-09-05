package com.staysupplierhub.catalog

import com.staysupplierhub.catalog.api.PropertyId
import com.staysupplierhub.catalog.api.RoomTypeId
import com.staysupplierhub.catalog.application.ApplyCatalogSnapshotService
import com.staysupplierhub.catalog.application.ReadSearchableCatalogService
import com.staysupplierhub.catalog.port.out.persistence.PropertyIdGenerator
import com.staysupplierhub.catalog.port.out.persistence.PropertyRepository
import com.staysupplierhub.catalog.port.out.persistence.RoomTypeIdGenerator
import com.staysupplierhub.catalog.port.out.persistence.SearchableCatalogReader
import com.staysupplierhub.shared.infrastructure.id.SnowflakeIdGenerator
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("stay-supplier-hub.catalog")
data class CatalogIdGeneratorProperties(
    val snowflakeNodeId: Long,
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogIdGeneratorProperties::class)
class CatalogRuntimeConfiguration {
    @Bean
    fun snowflakeIdGenerator(properties: CatalogIdGeneratorProperties) =
        SnowflakeIdGenerator(nodeId = properties.snowflakeNodeId)

    @Bean
    fun propertyIdGenerator(snowflakeIdGenerator: SnowflakeIdGenerator): PropertyIdGenerator =
        PropertyIdGenerator { PropertyId(snowflakeIdGenerator.nextId()) }

    @Bean
    fun roomTypeIdGenerator(snowflakeIdGenerator: SnowflakeIdGenerator): RoomTypeIdGenerator =
        RoomTypeIdGenerator { RoomTypeId(snowflakeIdGenerator.nextId()) }

    @Bean
    fun applyCatalogSnapshotService(
        propertyRepository: PropertyRepository,
        propertyIdGenerator: PropertyIdGenerator,
        roomTypeIdGenerator: RoomTypeIdGenerator,
    ) = ApplyCatalogSnapshotService(propertyRepository, propertyIdGenerator, roomTypeIdGenerator)

    @Bean
    fun readSearchableCatalogService(searchableCatalogReader: SearchableCatalogReader) =
        ReadSearchableCatalogService(searchableCatalogReader)
}
