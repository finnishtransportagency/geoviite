package fi.fta.geoviite.infra.ratko

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.ratko.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClientRequest
import java.math.RoundingMode
import java.time.Duration

val defaultRequestTimeout: Duration = Duration.ofMinutes(5L)
val metersCalculationTimeout: Duration = Duration.ofMinutes(15L)

@Component
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoClient @Autowired constructor(private val client: WebClient) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val ratkoJsonMapper =
        jsonMapper { addModule(kotlinModule { configure(KotlinFeature.NullIsSameAsDefault, true) }) }
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    data class RatkoStatus(val statusCode: HttpStatus  ) {
        val isOnline = statusCode == HttpStatus.OK
    }

    fun ratkoIsOnline(): RatkoStatus {
        return client
            .get()
            .uri("/api/versions/v1.0/version")
            .retrieve()
            .toBodilessEntity()
            .thenReturn(RatkoStatus(HttpStatus.OK))
            .onErrorResume(WebClientResponseException::class.java) {
                Mono.just(RatkoStatus(it.statusCode))
            }
            .block(defaultRequestTimeout)!!
    }

    fun getLocationTrack(locationTrackOid: RatkoOid<RatkoLocationTrack>): RatkoLocationTrack? {
        logger.serviceCall(
            "getLocationTrack",
            "locationTrackOid" to locationTrackOid
        )

        return client
            .get()
            .uri("/api/locations/v1.1/locationtracks/${locationTrackOid}")
            .retrieve()
            .bodyToMono<String>()
            .block(defaultRequestTimeout)
            ?.let { response ->
                ratkoJsonMapper.readTree(response).firstOrNull()?.let { locationTrackJsonNode ->
                    replaceKmM(locationTrackJsonNode.get("nodecollection"))
                    ratkoJsonMapper.treeToValue(locationTrackJsonNode, RatkoLocationTrack::class.java)
                }
            }
    }

    fun updateLocationTrackProperties(locationTrack: RatkoLocationTrack) {
        logger.serviceCall("updateLocationTrackProperties", "locationTrack" to locationTrack)

        client
            .put()
            .uri("/api/infra/v1.0/locationtracks")
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(locationTrack)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultRequestTimeout)
    }

    fun deleteLocationTrackPoints(locationTrackOid: RatkoOid<RatkoLocationTrack>, km: KmNumber?) {
        logger.serviceCall(
            "deleteLocationTrackPoints",
            "locationTrackOid" to locationTrackOid,
            "km" to km
        )

        client
            .delete()
            .uri("/api/infra/v1.0/points/${locationTrackOid}/${km ?: ""}")
            .retrieve()
            .toBodilessEntity()
            .onErrorResume(WebClientResponseException::class.java) {
                if (HttpStatus.NOT_FOUND == it.statusCode || HttpStatus.BAD_REQUEST == it.statusCode) Mono.empty()
                else Mono.error(RatkoPushException(RatkoPushErrorType.GEOMETRY, RatkoOperation.DELETE, it.responseBodyAsString, it))
            }
            .block(defaultRequestTimeout)
    }

    fun deleteRouteNumberPoints(routeNumberOid: RatkoOid<RatkoRouteNumber>, km: KmNumber?) {
        logger.serviceCall(
            "deleteRouteNumberPoints",
            "routeNumberOid" to routeNumberOid,
            "km" to km
        )

        client
            .delete()
            .uri("/api/infra/v1.0/routenumber/points/${routeNumberOid}/${km ?: ""}")
            .retrieve()
            .toBodilessEntity()
            .onErrorResume(WebClientResponseException::class.java) {
                if (HttpStatus.NOT_FOUND == it.statusCode || HttpStatus.BAD_REQUEST == it.statusCode) Mono.empty()
                else Mono.error(RatkoPushException(RatkoPushErrorType.GEOMETRY, RatkoOperation.DELETE, it.responseBodyAsString, it))
            }
            .block(defaultRequestTimeout)
    }

    fun updateRouteNumberPoints(routeNumberOid: RatkoOid<RatkoRouteNumber>, points: List<RatkoPoint>) {
        logger.serviceCall(
            "updateRouteNumberPoints",
            "routeNumberOid" to routeNumberOid,
            "points" to points
        )

        points.chunked(1000).map { chunk ->
            client
                .patch()
                .uri("/api/infra/v1.0/routenumber/points/${routeNumberOid}")
                .bodyValue(chunk)
                .retrieve()
                .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.UPDATE)
                .toBodilessEntity()
                .block(defaultRequestTimeout)
        }
    }

    fun createRouteNumberPoints(routeNumberOid: RatkoOid<RatkoRouteNumber>, points: List<RatkoPoint>) {
        logger.serviceCall(
            "createRouteNumberPoints",
            "routeNumberOid" to routeNumberOid,
            "points" to points
        )

        points.chunked(1000).map { chunk ->
            client
                .post()
                .uri("/api/infra/v1.0/routenumber/points/${routeNumberOid}")
                .bodyValue(chunk)
                .retrieve()
                .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.CREATE)
                .toBodilessEntity()
                .block(defaultRequestTimeout)
        }
    }

    fun updateLocationTrackPoints(locationTrackOid: RatkoOid<RatkoLocationTrack>, points: List<RatkoPoint>) {
        logger.serviceCall(
            "updateLocationTrackPoints",
            "locationTrackOid" to locationTrackOid,
            "points" to points
        )

        points.chunked(1000).map { chunk ->
            client
                .patch()
                .uri("/api/infra/v1.0/points/${locationTrackOid}")
                .bodyValue(chunk)
                .retrieve()
                .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.UPDATE)
                .toBodilessEntity()
                .block(defaultRequestTimeout)
        }
    }

    fun createLocationTrackPoints(locationTrackOid: RatkoOid<RatkoLocationTrack>, points: List<RatkoPoint>) {
        logger.serviceCall(
            "createLocationTrackPoints",
            "locationTrackOid" to locationTrackOid,
            "points" to points
        )

        points.chunked(1000).map { chunk ->
            client
                .post()
                .uri("/api/infra/v1.0/points/${locationTrackOid}")
                .bodyValue(chunk)
                .retrieve()
                .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.CREATE)
                .toBodilessEntity()
                .block(defaultRequestTimeout)
        }
    }

    fun forceRatkoToRedrawLocationTrack(locationTrackOids: List<RatkoOid<RatkoLocationTrack>>) {
        logger.serviceCall(
            "updateLocationTrackGeometryMValues",
            "locationTrackOids" to locationTrackOids
        )

        client
            .patch()
            .uri("/api/infra/v1.0/locationtracks/geom")
            .httpRequest { httpRequest ->
                val reactorRequest: HttpClientRequest = httpRequest.getNativeRequest()
                reactorRequest.responseTimeout(metersCalculationTimeout)
            }
            .bodyValue(locationTrackOids.map { it.id })
            .retrieve()
            .toBodilessEntity()
            .block(metersCalculationTimeout)
    }

    fun newLocationTrack(locationTrack: RatkoLocationTrack): RatkoOid<RatkoLocationTrack>? {
        logger.serviceCall("newLocationTrack", "locationTrack" to locationTrack)

        return client
            .post()
            .uri("/api/infra/v1.0/locationtracks")
            .bodyValue(locationTrack)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<RatkoOid<RatkoLocationTrack>>()
            .block(defaultRequestTimeout)
    }

    fun <T : RatkoAsset> newAsset(asset: RatkoAsset): RatkoOid<T>? {
        logger.serviceCall(
            "newAsset",
            "asset" to asset
        )
        data class NewRatkoAssetResponse(val id: String)

        return client
            .post()
            .uri("/api/assets/v1.2/")
            .bodyValue(asset.withoutGeometries())
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<List<NewRatkoAssetResponse>>()
            .block(defaultRequestTimeout)
            ?.firstOrNull()
            ?.let { RatkoOid(it.id) }
    }

    fun <T : RatkoAsset> replaceAssetLocations(assetOid: RatkoOid<T>, locations: List<RatkoAssetLocation>) {
        logger.serviceCall(
            "replaceAssetLocations",
            "assetOid" to assetOid,
            "locations" to locations
        )

        client
            .put()
            .uri("/api/assets/v1.2/${assetOid}/locations")
            .bodyValue(locations.map { it.withoutGeometries() })
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.LOCATION, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultRequestTimeout)
    }

    fun <T : RatkoAsset> replaceAssetGeoms(assetOid: RatkoOid<T>, geoms: List<RatkoAssetGeometry>) {
        logger.serviceCall(
            "replaceAssetGeoms",
            "assetOid" to assetOid,
            "geoms" to geoms
        )
        client
            .put()
            .uri("/api/assets/v1.2/${assetOid}/geoms")
            .bodyValue(geoms)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultRequestTimeout)
    }

    fun <T : RatkoAsset> getSwitchAsset(assetOid: RatkoOid<T>): RatkoSwitchAsset? {
        logger.serviceCall("getSwitchAsset", "assetOid" to assetOid)

        return client
            .get()
            .uri("/api/assets/v1.2/${assetOid}")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono<String>()
            .onErrorResume(WebClientResponseException::class.java) {
                if (HttpStatus.NOT_FOUND == it.statusCode) Mono.empty() else Mono.error(it)
            }
            .block(defaultRequestTimeout)
            ?.let { response ->
                ratkoJsonMapper.readTree(response).let { jsonNode ->
                    jsonNode.get("assetGeoms")?.map { asset ->
                        (asset as ObjectNode).replace("geometry", asset.get("geometryOriginal"))
                    }

                    jsonNode.get("locations")?.forEach { location ->
                        replaceKmM(location.get("nodecollection"))
                    }

                    ratkoJsonMapper.treeToValue(jsonNode, RatkoSwitchAsset::class.java)
                }
            }
    }

    private fun replaceKmM(nodeCollection: JsonNode) {
        nodeCollection.get("nodes")?.forEach { node ->
            val point = node.get("point") as ObjectNode
            val km = point.get("km").textValue().toInt()
            val m = point.get("m").textValue().toBigDecimal().setScale(3, RoundingMode.DOWN)
            point.put("kmM", RatkoTrackMeter(KmNumber(km), m).toString())
        }
    }

    fun <T : RatkoAsset> updateAssetState(assetOid: RatkoOid<T>, state: RatkoAssetState) {
        logger.serviceCall(
            "updateAssetState",
            "assetOid" to assetOid,
            "state" to state
        )

        val responseJson = client
            .get()
            .uri("/api/assets/v1.2/${assetOid}")
            .retrieve()
            .bodyToMono<String>()
            .block(defaultRequestTimeout)

        val switchJsonObject = ObjectMapper().readTree(responseJson) as ObjectNode
        switchJsonObject.put("state", state.value)
        switchJsonObject.remove("temporalStartTime")
        switchJsonObject.get("properties")?.forEach { property ->
            (property as ObjectNode).remove("validityStart")
        }

        switchJsonObject.get("assetGeoms")?.forEach { geom ->
            (geom as ObjectNode).let {
                it.replace("geometry", geom.get("geometryOriginal"))
                it.remove("validityStart")
            }
        }

        switchJsonObject.get("relationAssets")?.forEach { relation ->
            (relation as ObjectNode).remove("validityStart")
        }

        val updatedLocations = mutableListOf<JsonNode>()

        switchJsonObject.get("locations")?.forEach { location ->
            (location as ObjectNode).remove("validityStartTime")
            val nodeCollection = location.get("nodecollection") as ObjectNode
            replaceKmM(nodeCollection)

            val validJoints = nodeCollection.get("nodes").filter { node ->
                val point = node.get("point") as ObjectNode
                (point.get("state") as ObjectNode).get("name").textValue() == RatkoPointStates.VALID.state
            }

            if (validJoints.isNotEmpty()) {
                val nodes = nodeCollection.putArray("nodes")
                validJoints.forEach { node -> nodes.add(node) }

                updatedLocations.add(location)
            }
        }

        val locations = switchJsonObject.putArray("locations")
        updatedLocations.forEachIndexed { index, location ->
            (location as ObjectNode).put("priority", index + 1)
            locations.add(location)
        }

        (switchJsonObject.get("rowMetadata") as ObjectNode).put("sourceName", GEOVIITE_NAME)

        switchJsonObject.remove("childAssets")

        client
            .put()
            .uri("/api/assets/v1.2/${assetOid}")
            .bodyValue(switchJsonObject.toString())
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.STATE, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultRequestTimeout)
    }

    fun <T : RatkoAsset> updateAssetProperties(assetOid: RatkoOid<T>, properties: List<RatkoAssetProperty>) {
        logger.serviceCall(
            "updateAssetProperties",
            "assetOid" to assetOid,
            "properties" to properties
        )

        client
            .put()
            .uri("/api/assets/v1.2/${assetOid}/properties")
            .bodyValue(properties)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultRequestTimeout)
    }

    fun getNewLocationTrackOid(): RatkoOid<RatkoLocationTrack>? {
        logger.serviceCall("getNewLocationTrackOid")

        return client
            .post()
            .uri("/api/infra/v1.0/locationtracks")
            .body(BodyInserters.fromValue("{}"))
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<RatkoOid<RatkoLocationTrack>>()
            .block(defaultRequestTimeout)
    }

    fun getNewRouteNumberOid(): RatkoOid<RatkoRouteNumber>? {
        logger.serviceCall("getNewRouteNumberOid")

        return client
            .post()
            .uri("/api/infra/v1.0/routenumbers")
            .body(BodyInserters.fromValue("{}"))
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<RatkoOid<RatkoRouteNumber>>()
            .block(defaultRequestTimeout)
    }

    fun getNewSwitchOid(): RatkoOid<RatkoSwitchAsset>? {
        logger.serviceCall("getNewSwitchOid")

        val body = "{\"type\":\"turnout\"}"

        return client
            .post()
            .uri("/api/assets/v1.2")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(body))
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<List<RatkoOid<RatkoSwitchAsset>>>()
            .block(defaultRequestTimeout)
            ?.firstOrNull()
    }

    fun getRouteNumber(routeNumberOid: RatkoOid<RatkoRouteNumber>): RatkoRouteNumber? {
        logger.serviceCall("getRouteNumber", "routeNumberOid" to routeNumberOid)

        return client
            .get()
            .uri("/api/locations/v1.1/routenumber/${routeNumberOid}")
            .retrieve()
            .bodyToMono<String>()
            .onErrorResume(WebClientResponseException::class.java) {
                if (HttpStatus.NOT_FOUND == it.statusCode) Mono.empty() else Mono.error(it)
            }
            .block(defaultRequestTimeout)
            ?.let { response ->
                ratkoJsonMapper.readTree(response).let { jsonNode ->
                    replaceKmM(jsonNode.get("nodecollection"))

                    ratkoJsonMapper.treeToValue(jsonNode, RatkoRouteNumber::class.java)
                }
            }
    }

    fun newRouteNumber(routeNumber: RatkoRouteNumber): RatkoOid<RatkoRouteNumber>? {
        logger.serviceCall(
            "newRouteNumber",
            "routeNumber" to routeNumber
        )

        return client
            .post()
            .uri("/api/infra/v1.0/routenumbers")
            .bodyValue(routeNumber)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<RatkoOid<RatkoRouteNumber>>()
            .block(defaultRequestTimeout)
    }

    fun forceRatkoToRedrawRouteNumber(routeNumberOids: List<RatkoOid<RatkoRouteNumber>>) {
        logger.serviceCall(
            "updateRouteNumberGeometryMValues",
            "routeNumberOids" to routeNumberOids
        )

        client
            .patch()
            .uri("/api/infra/v1.0/routenumbers/geom")
            .httpRequest { httpRequest ->
                val reactorRequest: HttpClientRequest = httpRequest.getNativeRequest()
                reactorRequest.responseTimeout(metersCalculationTimeout)
            }
            .bodyValue(routeNumberOids.map { it.id })
            .retrieve()
            .toBodilessEntity()
            .block(metersCalculationTimeout)
    }

    fun updateRouteNumber(routeNumber: RatkoRouteNumber) {
        logger.serviceCall(
            "updateRouteNumber",
            "routeNumber" to routeNumber
        )

        client
            .put()
            .uri("/api/infra/v1.0/routenumbers")
            .bodyValue(routeNumber)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultRequestTimeout)
    }

    private fun WebClient.ResponseSpec.defaultErrorHandler(
        errorType: RatkoPushErrorType,
        operation: RatkoOperation
    ) = onStatus(
        { !it.is2xxSuccessful },
        { response ->
            response.bodyToMono<String>().switchIfEmpty(Mono.just("")).flatMap { body ->
                logger.error("Error during Ratko push! HTTP Status code: ${response.rawStatusCode()}, body: $body")
                Mono.error(RatkoPushException(errorType, operation, body))
            }
        }
    )
}
