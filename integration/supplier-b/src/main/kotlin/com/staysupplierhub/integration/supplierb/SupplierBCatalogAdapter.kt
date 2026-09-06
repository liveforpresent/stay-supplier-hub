package com.staysupplierhub.integration.supplierb

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.staysupplierhub.catalog.domain.SupplierPropertyCode
import com.staysupplierhub.catalog.domain.SupplierRoomTypeCode
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailure
import com.staysupplierhub.catalog.port.out.supplier.CatalogSupplierFailureType
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogOutcome
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogPort
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogProperty
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogRoomType
import com.staysupplierhub.catalog.port.out.supplier.SupplierCatalogSnapshot
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import java.util.concurrent.TimeoutException

class SupplierBCatalogAdapter(
    private val webClient: WebClient,
    private val apiKey: String,
    private val objectMapper: ObjectMapper,
) : SupplierCatalogPort {
    override fun fetchCatalog(): SupplierCatalogOutcome = runBlocking {
        try {
            webClient.get()
                .uri(CATALOG_PATH)
                .header(API_KEY_HEADER, apiKey)
                .exchangeToMono { response ->
                    if (!response.statusCode().is2xxSuccessful) {
                        mono { failure(response.statusCode()) }
                    } else {
                        response.bodyToMono(String::class.java)
                            .map { objectMapper.readValue<SupplierBCatalogResponse>(it).toOutcome() }
                    }
                }
                .awaitSingle()
        } catch (exception: WebClientRequestException) {
            failure(
                if (exception.cause is TimeoutException) {
                    CatalogSupplierFailureType.TIMEOUT
                } else {
                    CatalogSupplierFailureType.CONNECTION_FAILED
                },
            )
        } catch (_: Exception) {
            failure(CatalogSupplierFailureType.INVALID_RESPONSE)
        }
    }

    private fun SupplierBCatalogResponse.toOutcome(): SupplierCatalogOutcome {
        val resultCode = resultCode ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
        if (resultCode != SUCCESS_CODE) return failure(resultCode.toFailureType())
        val properties = data?.items ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
        val normalizedProperties = buildList {
            properties.forEach { property ->
                val propertyCode = property.propertyId?.takeIf(String::isNotBlank)
                    ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                val propertyName = property.propertyName?.takeIf(String::isNotBlank)
                    ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                val rooms = property.rooms ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                val normalizedRooms = buildList {
                    rooms.forEach { room ->
                        val roomCode = room.roomId?.takeIf(String::isNotBlank)
                            ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                        val roomName = room.roomName?.takeIf(String::isNotBlank)
                            ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                        val maxOccupancy = room.maxOccupancy?.takeIf { it > 0 }
                            ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                        add(SupplierCatalogRoomType(SupplierRoomTypeCode(roomCode), roomName, maxOccupancy))
                    }
                }
                if (normalizedRooms.map { it.supplierRoomTypeCode }.distinct().size != normalizedRooms.size) {
                    return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                }
                add(SupplierCatalogProperty(SupplierPropertyCode(propertyCode), propertyName, normalizedRooms))
            }
        }
        return if (normalizedProperties.map { it.supplierPropertyCode }.distinct().size == normalizedProperties.size) {
            SupplierCatalogOutcome.Success(SupplierCatalogSnapshot(normalizedProperties))
        } else {
            failure(CatalogSupplierFailureType.INVALID_RESPONSE)
        }
    }

    private fun String.toFailureType(): CatalogSupplierFailureType = when (this) {
        "E400" -> CatalogSupplierFailureType.INVALID_REQUEST
        "E401" -> CatalogSupplierFailureType.AUTHENTICATION_FAILED
        "E429" -> CatalogSupplierFailureType.RATE_LIMITED
        "E500" -> CatalogSupplierFailureType.SUPPLIER_ERROR
        "E503" -> CatalogSupplierFailureType.SERVICE_UNAVAILABLE
        else -> CatalogSupplierFailureType.INVALID_RESPONSE
    }

    private fun failure(status: HttpStatusCode): SupplierCatalogOutcome = failure(
        when (status.value()) {
            400 -> CatalogSupplierFailureType.INVALID_REQUEST
            401 -> CatalogSupplierFailureType.AUTHENTICATION_FAILED
            429 -> CatalogSupplierFailureType.RATE_LIMITED
            500 -> CatalogSupplierFailureType.SUPPLIER_ERROR
            503 -> CatalogSupplierFailureType.SERVICE_UNAVAILABLE
            in 500..599 -> CatalogSupplierFailureType.SUPPLIER_ERROR
            else -> CatalogSupplierFailureType.INVALID_RESPONSE
        },
    )

    private fun failure(type: CatalogSupplierFailureType): SupplierCatalogOutcome =
        SupplierCatalogOutcome.Failed(CatalogSupplierFailure(type))

    private companion object {
        const val CATALOG_PATH = "/b/api/properties"
        const val API_KEY_HEADER = "X-Api-Key"
        const val SUCCESS_CODE = "0000"
    }
}

internal data class SupplierBCatalogResponse(
    val resultCode: String? = null,
    val resultMessage: String? = null,
    val data: SupplierBCatalogDataResponse? = null,
)

internal data class SupplierBCatalogDataResponse(
    val items: List<SupplierBCatalogPropertyResponse>? = null,
)

internal data class SupplierBCatalogPropertyResponse(
    val propertyId: String? = null,
    val propertyName: String? = null,
    val rooms: List<SupplierBCatalogRoomResponse>? = null,
)

internal data class SupplierBCatalogRoomResponse(
    val roomId: String? = null,
    val roomName: String? = null,
    val maxOccupancy: Int? = null,
)
