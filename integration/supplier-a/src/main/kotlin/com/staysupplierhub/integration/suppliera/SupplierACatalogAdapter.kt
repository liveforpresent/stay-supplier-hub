package com.staysupplierhub.integration.suppliera

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

class SupplierACatalogAdapter(
    private val webClient: WebClient,
    private val apiKey: String,
    private val objectMapper: ObjectMapper,
) : SupplierCatalogPort {
    override fun fetchCatalog(): SupplierCatalogOutcome = runBlocking {
        try {
            webClient.get()
                .uri("/a/v1/hotels")
                .header(API_KEY_HEADER, apiKey)
                .exchangeToMono { response ->
                    if (!response.statusCode().is2xxSuccessful) {
                        mono { failure(response.statusCode()) }
                    } else {
                        response.bodyToMono(String::class.java)
                            .map { objectMapper.readValue<SupplierACatalogResponse>(it).toOutcome() }
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

    private fun SupplierACatalogResponse.toOutcome(): SupplierCatalogOutcome {
        val hotels = items ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
        val properties = buildList {
            hotels.forEach { hotel ->
                val hotelCode = hotel.hotelCode?.takeIf(String::isNotBlank)
                    ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                val hotelName = hotel.hotelName?.takeIf(String::isNotBlank)
                    ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                val rooms = hotel.roomTypes ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                val normalizedRooms = buildList {
                    rooms.forEach { room ->
                        val roomCode = room.roomTypeCode?.takeIf(String::isNotBlank)
                            ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                        val roomName = room.roomTypeName?.takeIf(String::isNotBlank)
                            ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                        val maxOccupancy = room.maxOccupancy?.takeIf { it > 0 }
                            ?: return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                        add(SupplierCatalogRoomType(SupplierRoomTypeCode(roomCode), roomName, maxOccupancy))
                    }
                }
                if (normalizedRooms.map { it.supplierRoomTypeCode }.distinct().size != normalizedRooms.size) {
                    return failure(CatalogSupplierFailureType.INVALID_RESPONSE)
                }
                add(SupplierCatalogProperty(SupplierPropertyCode(hotelCode), hotelName, normalizedRooms))
            }
        }
        return if (properties.map { it.supplierPropertyCode }.distinct().size == properties.size) {
            SupplierCatalogOutcome.Success(SupplierCatalogSnapshot(properties))
        } else {
            failure(CatalogSupplierFailureType.INVALID_RESPONSE)
        }
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
        const val API_KEY_HEADER = "X-Api-Key"
    }
}

internal data class SupplierACatalogResponse(
    val items: List<SupplierAHotelResponse>? = null,
)

internal data class SupplierAHotelResponse(
    val hotelCode: String? = null,
    val hotelName: String? = null,
    val roomTypes: List<SupplierARoomTypeResponse>? = null,
)

internal data class SupplierARoomTypeResponse(
    val roomTypeCode: String? = null,
    val roomTypeName: String? = null,
    val maxOccupancy: Int? = null,
)
