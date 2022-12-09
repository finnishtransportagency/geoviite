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
import fi.fta.geoviite.infra.logging.integrationCall
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
import java.math.RoundingMode
import java.time.Duration

val defaultBlockTimeout: Duration = defaultResponseTimeout.plusMinutes(1L)

@Component
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoClient @Autowired constructor(private val client: WebClient) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val ratkoJsonMapper =
        jsonMapper { addModule(kotlinModule { configure(KotlinFeature.NullIsSameAsDefault, true) }) }
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    data class RatkoStatus(val statusCode: HttpStatus) {
        val isOnline = statusCode == HttpStatus.OK
    }

    fun getRatkoOnlineStatus(): RatkoStatus {
        return client
            .get()
            .uri("/api/versions/v1.0/version")
            .retrieve()
            .toBodilessEntity()
            .thenReturn(RatkoStatus(HttpStatus.OK))
            .onErrorResume(WebClientResponseException::class.java) {
                Mono.just(RatkoStatus(it.statusCode))
            }
            .block(defaultBlockTimeout)!!
    }

    fun getLocationTrack(locationTrackOid: RatkoOid<RatkoLocationTrack>): RatkoLocationTrack? {
        logger.integrationCall("getLocationTrack", "locationTrackOid" to locationTrackOid)

        return client
            .get()
            .uri("/api/locations/v1.1/locationtracks/${locationTrackOid}")
            .retrieve()
            .bodyToMono<String>()
            .block(defaultBlockTimeout)
            ?.let { response ->
                ratkoJsonMapper.readTree(response).firstOrNull()?.let { locationTrackJsonNode ->
                    replaceKmM(locationTrackJsonNode.get("nodecollection"))
                    ratkoJsonMapper.treeToValue(locationTrackJsonNode, RatkoLocationTrack::class.java)
                }
            }
    }

    fun updateLocationTrackProperties(locationTrack: RatkoLocationTrack) {
        logger.integrationCall("updateLocationTrackProperties", "locationTrack" to locationTrack)

        client
            .put()
            .uri("/api/infra/v1.0/locationtracks")
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(locationTrack)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun deleteLocationTrackPoints(locationTrackOid: RatkoOid<RatkoLocationTrack>, km: KmNumber?) {
        logger.integrationCall(
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
                else Mono.error(
                    RatkoPushException(
                        RatkoPushErrorType.GEOMETRY,
                        RatkoOperation.DELETE,
                        it.responseBodyAsString,
                        it
                    )
                )
            }
            .block(defaultBlockTimeout)
    }

    fun deleteRouteNumberPoints(routeNumberOid: RatkoOid<RatkoRouteNumber>, km: KmNumber?) {
        logger.integrationCall(
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
                else Mono.error(
                    RatkoPushException(
                        RatkoPushErrorType.GEOMETRY,
                        RatkoOperation.DELETE,
                        it.responseBodyAsString,
                        it
                    )
                )
            }
            .block(defaultBlockTimeout)
    }

    fun updateRouteNumberPoints(routeNumberOid: RatkoOid<RatkoRouteNumber>, points: List<RatkoPoint>) {
        logger.integrationCall(
            "updateRouteNumberPoints",
            "routeNumberOid" to routeNumberOid,
            "points" to "${points.first().kmM}..${points.last().kmM}",
        )
        points.chunked(1000).mapIndexed { index, chunk ->
            logger.integrationCall("updateRouteNumberPoints",
                "routeNumberOid" to routeNumberOid,
                "chunk" to index,
                "points" to "${chunk.first().kmM}..${chunk.last().kmM}",
            )
            client
                .patch()
                .uri("/api/infra/v1.0/routenumber/points/${routeNumberOid}")
                .bodyValue(chunk)
                .retrieve()
                .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.UPDATE)
                .toBodilessEntity()
                .block(defaultBlockTimeout)
        }
    }

    fun createRouteNumberPoints(routeNumberOid: RatkoOid<RatkoRouteNumber>, points: List<RatkoPoint>) {
        logger.integrationCall(
            "createRouteNumberPoints",
            "routeNumberOid" to routeNumberOid,
            "points" to "${points.first().kmM}..${points.last().kmM}"
        )
        points.chunked(1000).mapIndexed { index, chunk ->
            logger.integrationCall("createRouteNumberPoints",
                "routeNumberOid" to routeNumberOid,
                "chunk" to index,
                "points" to "${chunk.first().kmM}..${chunk.last().kmM}",
            )
            client
                .post()
                .uri("/api/infra/v1.0/routenumber/points/${routeNumberOid}")
                .bodyValue(chunk)
                .retrieve()
                .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.CREATE)
                .toBodilessEntity()
                .block(defaultBlockTimeout)
        }
    }

    fun updateLocationTrackPoints(locationTrackOid: RatkoOid<RatkoLocationTrack>, points: List<RatkoPoint>) {
        logger.integrationCall(
            "updateLocationTrackPoints",
            "locationTrackOid" to locationTrackOid,
            "points" to "${points.first().kmM}..${points.last().kmM}"
        )
        points.chunked(1000).mapIndexed { index, chunk ->
            logger.integrationCall("updateLocationTrackPoints",
                "locationTrackOid" to locationTrackOid,
                "chunk" to index,
                "points" to "${chunk.first().kmM}..${chunk.last().kmM}",
            )
            client
                .patch()
                .uri("/api/infra/v1.0/points/${locationTrackOid}")
                .bodyValue(chunk)
                .retrieve()
                .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.UPDATE)
                .toBodilessEntity()
                .block(defaultBlockTimeout)
        }
    }

    fun createLocationTrackPoints(locationTrackOid: RatkoOid<RatkoLocationTrack>, points: List<RatkoPoint>) {
        logger.integrationCall(
            "createLocationTrackPoints",
            "locationTrackOid" to locationTrackOid,
            "points" to "${points.first().kmM}..${points.last().kmM}"
        )
        points.chunked(1000).mapIndexed { index, chunk ->
            logger.integrationCall("createLocationTrackPoints",
                "locationTrackOid" to locationTrackOid,
                "chunk" to index,
                "points" to "${chunk.first().kmM}..${chunk.last().kmM}",
            )
            client
                .post()
                .uri("/api/infra/v1.0/points/${locationTrackOid}")
                .bodyValue(chunk)
                .retrieve()
                .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.CREATE)
                .toBodilessEntity()
                .block(defaultBlockTimeout)
        }
    }

    fun forceRatkoToRedrawLocationTrack(locationTrackOids: List<RatkoOid<RatkoLocationTrack>>) {
        logger.integrationCall(
            "updateLocationTrackGeometryMValues",
            "locationTrackOids" to locationTrackOids
        )

        client
            .patch()
            .uri("/api/infra/v1.0/locationtracks/geom")
            .bodyValue(locationTrackOids.map { it.id })
            .retrieve()
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun newLocationTrack(locationTrack: RatkoLocationTrack): RatkoOid<RatkoLocationTrack>? {
        logger.integrationCall("newLocationTrack", "locationTrack" to locationTrack)

        return client
            .post()
            .uri("/api/infra/v1.0/locationtracks")
            .bodyValue(locationTrack)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<RatkoOid<RatkoLocationTrack>>()
            .block(defaultBlockTimeout)
    }

    fun <T : RatkoAsset> newAsset(asset: RatkoAsset): RatkoOid<T>? {
        logger.integrationCall("newAsset", "asset" to asset)
        data class NewRatkoAssetResponse(val id: String)

        return client
            .post()
            .uri("/api/assets/v1.2/")
            .bodyValue(asset.withoutGeometries())
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<List<NewRatkoAssetResponse>>()
            .block(defaultBlockTimeout)
            ?.firstOrNull()
            ?.let { RatkoOid(it.id) }
    }

    fun <T : RatkoAsset> replaceAssetLocations(assetOid: RatkoOid<T>, locations: List<RatkoAssetLocation>) {
        logger.integrationCall("replaceAssetLocations", "assetOid" to assetOid, "locations" to locations)

        client
            .put()
            .uri("/api/assets/v1.2/${assetOid}/locations")
            .bodyValue(locations.map { it.withoutGeometries() })
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.LOCATION, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun <T : RatkoAsset> replaceAssetGeoms(assetOid: RatkoOid<T>, geoms: List<RatkoAssetGeometry>) {
        logger.integrationCall("replaceAssetGeoms", "assetOid" to assetOid, "geoms" to geoms)
        client
            .put()
            .uri("/api/assets/v1.2/${assetOid}/geoms")
            .bodyValue(geoms)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun <T : RatkoAsset> getSwitchAsset(assetOid: RatkoOid<T>): RatkoSwitchAsset? {
        logger.integrationCall("getSwitchAsset", "assetOid" to assetOid)

        return client
            .get()
            .uri("/api/assets/v1.2/${assetOid}")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono<String>()
            .onErrorResume(WebClientResponseException::class.java) {
                if (HttpStatus.NOT_FOUND == it.statusCode) Mono.empty() else Mono.error(it)
            }
            .block(defaultBlockTimeout)
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
        logger.integrationCall("updateAssetState", "assetOid" to assetOid, "state" to state)

        val responseJson = client
            .get()
            .uri("/api/assets/v1.2/${assetOid}")
            .retrieve()
            .bodyToMono<String>()
            .block(defaultBlockTimeout)

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
            .block(defaultBlockTimeout)
    }

    fun <T : RatkoAsset> updateAssetProperties(assetOid: RatkoOid<T>, properties: List<RatkoAssetProperty>) {
        logger.integrationCall("updateAssetProperties", "assetOid" to assetOid, "properties" to properties)

        client
            .put()
            .uri("/api/assets/v1.2/${assetOid}/properties")
            .bodyValue(properties)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun getNewLocationTrackOid(): RatkoOid<RatkoLocationTrack>? {
        logger.integrationCall("getNewLocationTrackOid")

        return client
            .post()
            .uri("/api/infra/v1.0/locationtracks")
            .body(BodyInserters.fromValue("{}"))
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<RatkoOid<RatkoLocationTrack>>()
            .block(defaultBlockTimeout)
    }

    fun getNewRouteNumberOid(): RatkoOid<RatkoRouteNumber>? {
        logger.integrationCall("getNewRouteNumberOid")

        return client
            .post()
            .uri("/api/infra/v1.0/routenumbers")
            .body(BodyInserters.fromValue("{}"))
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<RatkoOid<RatkoRouteNumber>>()
            .block(defaultBlockTimeout)
    }

    fun getNewSwitchOid(): RatkoOid<RatkoSwitchAsset>? {
        logger.integrationCall("getNewSwitchOid")

        val body = "{\"type\":\"turnout\"}"

        return client
            .post()
            .uri("/api/assets/v1.2")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(body))
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<List<RatkoOid<RatkoSwitchAsset>>>()
            .block(defaultBlockTimeout)
            ?.firstOrNull()
    }

    fun getRouteNumber(routeNumberOid: RatkoOid<RatkoRouteNumber>): RatkoRouteNumber? {
        logger.integrationCall("getRouteNumber", "routeNumberOid" to routeNumberOid)

        return client
            .get()
            .uri("/api/locations/v1.1/routenumber/${routeNumberOid}")
            .retrieve()
            .bodyToMono<String>()
            .onErrorResume(WebClientResponseException::class.java) {
                if (HttpStatus.NOT_FOUND == it.statusCode) Mono.empty() else Mono.error(it)
            }
            .block(defaultBlockTimeout)
            ?.let { response ->
                ratkoJsonMapper.readTree(response).let { jsonNode ->
                    replaceKmM(jsonNode.get("nodecollection"))

                    ratkoJsonMapper.treeToValue(jsonNode, RatkoRouteNumber::class.java)
                }
            }
    }

    fun newRouteNumber(routeNumber: RatkoRouteNumber): RatkoOid<RatkoRouteNumber>? {
        logger.integrationCall("newRouteNumber", "routeNumber" to routeNumber)

        return client
            .post()
            .uri("/api/infra/v1.0/routenumbers")
            .bodyValue(routeNumber)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<RatkoOid<RatkoRouteNumber>>()
            .block(defaultBlockTimeout)
    }

    fun forceRatkoToRedrawRouteNumber(routeNumberOids: List<RatkoOid<RatkoRouteNumber>>) {
        logger.integrationCall("updateRouteNumberGeometryMValues", "routeNumberOids" to routeNumberOids)

        client
            .patch()
            .uri("/api/infra/v1.0/routenumbers/geom")
            .bodyValue(routeNumberOids.map { it.id })
            .retrieve()
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun updateRouteNumber(routeNumber: RatkoRouteNumber) {
        logger.integrationCall("updateRouteNumber", "routeNumber" to routeNumber)

        client
            .put()
            .uri("/api/infra/v1.0/routenumbers")
            .bodyValue(routeNumber)
            .retrieve()
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultBlockTimeout)
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
