package fi.fta.geoviite.infra.ratko

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.treeToValue
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MainBranchRatkoExternalId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.integration.RatkoAssetType
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoOperation.CREATE
import fi.fta.geoviite.infra.integration.RatkoOperation.DELETE
import fi.fta.geoviite.infra.integration.RatkoOperation.FETCH_EXISTING
import fi.fta.geoviite.infra.integration.RatkoOperation.UPDATE
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.integration.RatkoPushErrorType.GEOMETRY
import fi.fta.geoviite.infra.integration.RatkoPushErrorType.LOCATION
import fi.fta.geoviite.infra.integration.RatkoPushErrorType.PROPERTIES
import fi.fta.geoviite.infra.integration.RatkoPushErrorType.STATE
import fi.fta.geoviite.infra.logging.integrationCall
import fi.fta.geoviite.infra.ratko.model.GEOVIITE_NAME
import fi.fta.geoviite.infra.ratko.model.RatkoAssetGeometry
import fi.fta.geoviite.infra.ratko.model.RatkoAssetLocation
import fi.fta.geoviite.infra.ratko.model.RatkoAssetProperty
import fi.fta.geoviite.infra.ratko.model.RatkoAssetState
import fi.fta.geoviite.infra.ratko.model.RatkoBulkTransferResponse
import fi.fta.geoviite.infra.ratko.model.RatkoErrorResponse
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPointAssetsResponse
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPointParse
import fi.fta.geoviite.infra.ratko.model.RatkoPlan
import fi.fta.geoviite.infra.ratko.model.RatkoPlanId
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItem
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItemId
import fi.fta.geoviite.infra.ratko.model.RatkoPlanResponse
import fi.fta.geoviite.infra.ratko.model.RatkoPlanState
import fi.fta.geoviite.infra.ratko.model.RatkoPoint
import fi.fta.geoviite.infra.ratko.model.RatkoPointStates
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.ratko.model.RatkoSwitchAsset
import fi.fta.geoviite.infra.ratko.model.RatkoSwitchAssetType
import fi.fta.geoviite.infra.ratko.model.RatkoTrackMeter
import fi.fta.geoviite.infra.ratko.model.parseAsset
import fi.fta.geoviite.infra.split.BulkTransfer
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Duration

val defaultBlockTimeout: Duration = defaultResponseTimeout.plusMinutes(1L)

private const val INFRA_PATH = "/api/infra/v1.0"
private const val LOCATIONS_PATH = "/api/locations/v1.1"

private const val LOCATION_TRACK_LOCATIONS_PATH = "$LOCATIONS_PATH/locationtracks"
private const val LOCATION_TRACK_POINTS_PATH_V1_0 = "/api/infra/v1.0/points"
private const val LOCATION_TRACK_POINTS_PATH_V1_1 = "/api/infra/v1.1/points"
private const val LOCATION_TRACK_PATH = "$INFRA_PATH/locationtracks"
private const val LOCATION_TRACK_PATCH_PATH = "/api/infra/v1.1/locationtracks"
private const val ROUTE_NUMBER_LOCATIONS_PATH = "$LOCATIONS_PATH/routenumber"
private const val ROUTE_NUMBER_POINTS_PATH = "$INFRA_PATH/routenumber/points"
private const val ROUTE_NUMBER_PATH = "$INFRA_PATH/routenumbers"
private const val ASSET_PATH = "/api/assets/v1.2"
private const val VERSION_PATH = "/api/versions/v1.0/version"
private const val BULK_TRANSFER_PATH = "/api/split/bulk-transfer"
private const val PLAN_PATH = "/api/plan/v1.0/plans"
private const val MAP_ASSET_PATH = "/api/map/v1.0/assets"

private const val MAX_JSON_LOG_LENGTH = 10_000

// Use Geoviite OID-space for fake OIDs but make fake OIDs clearly distinct from real OIDs
const val FAKE_OID_PREFIX = "1.2.246.578.13.0.0.0.0.0.0.0.0.0"
const val TRACK_NUMBER_FAKE_OID_CONTEXT = 10001
const val LOCATION_TRACK_FAKE_OID_CONTEXT = 10002
const val SWITCH_FAKE_OID_CONTEXT = 139

enum class RatkoConnectionStatus {
    ONLINE,
    ONLINE_ERROR,
    OFFLINE,
    NOT_CONFIGURED,
}

fun isFakeOID(oid: String): Boolean = oid.startsWith(FAKE_OID_PREFIX)

fun <T> isFakeOID(oid: RatkoOid<T>): Boolean = isFakeOID(oid.toString())

fun <T> isFakeOID(oid: Oid<T>): Boolean = isFakeOID(oid.toString())

sealed class RatkoPushTarget<T> {
    abstract val oid: Oid<T>
    abstract val assetType: RatkoAssetType
}

data class RatkoPushTargetLocationTrack(override val oid: Oid<LocationTrack>) : RatkoPushTarget<LocationTrack>() {
    constructor(oid: RatkoOid<LocationTrack>) : this(Oid(oid.id))

    constructor(track: RatkoLocationTrack) : this(requireNotNull(track.id))

    override val assetType: RatkoAssetType = RatkoAssetType.LOCATION_TRACK
}

data class RatkoPushTargetTrackNumber(override val oid: Oid<LayoutTrackNumber>) : RatkoPushTarget<LayoutTrackNumber>() {
    constructor(oid: RatkoOid<LayoutTrackNumber>) : this(Oid(oid.id))

    constructor(routeNumber: RatkoRouteNumber) : this(requireNotNull(routeNumber.id))

    override val assetType: RatkoAssetType = RatkoAssetType.TRACK_NUMBER
}

data class RatkoPushTargetSwitch(override val oid: Oid<LayoutSwitch>) : RatkoPushTarget<LayoutSwitch>() {
    constructor(oid: RatkoOid<LayoutSwitch>) : this(Oid(oid.id))

    constructor(switch: RatkoSwitchAsset) : this(requireNotNull(switch.id))

    override val assetType: RatkoAssetType = RatkoAssetType.SWITCH
}

@Component
@ConditionalOnBean(RatkoFakeOidGeneratorConfiguration::class)
class RatkoFakeOidGenerator {

    fun <T> generateFakeRatkoOID(contextId: Int, uniqueIdInContext: Int): RatkoOid<T> {
        return RatkoOid("${FAKE_OID_PREFIX}.${contextId}.${uniqueIdInContext}")
    }
}

@Component
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoClient @Autowired constructor(val client: RatkoWebClient) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val ratkoJsonMapper =
        jsonMapper { addModule(kotlinModule { configure(KotlinFeature.NullIsSameAsDefault, true) }) }
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    data class RatkoStatus(val connectionStatus: RatkoConnectionStatus, val ratkoStatusCode: Int?)

    fun getRatkoOnlineStatus(): RatkoStatus {
        return getSpec(VERSION_PATH)
            .toBodilessEntity()
            .map { response -> RatkoStatus(RatkoConnectionStatus.ONLINE, response.statusCode.value()) }
            // Handle non-2xx responses from Ratko
            .onErrorResume(WebClientResponseException::class.java) { ex ->
                Mono.just(RatkoStatus(RatkoConnectionStatus.ONLINE_ERROR, ex.statusCode.value()))
            }
            // Handle exceptions thrown by the WebClient (e.g. timeouts)
            .onErrorResume(WebClientRequestException::class.java) {
                Mono.just(RatkoStatus(RatkoConnectionStatus.OFFLINE, null))
            }
            .block(defaultBlockTimeout) ?: RatkoStatus(RatkoConnectionStatus.OFFLINE, null)
    }

    fun getLocationTrack(locationTrackOid: RatkoOid<LocationTrack>): RatkoLocationTrack? {
        logger.integrationCall("getLocationTrack", "locationTrackOid" to locationTrackOid)

        val url = combinePaths(LOCATION_TRACK_LOCATIONS_PATH, locationTrackOid)
        return getWithJsonResponseBody(url, LOCATION, RatkoPushTargetLocationTrack(locationTrackOid))?.let { response ->
            ratkoJsonMapper.readTree(response).firstOrNull()?.let { locationTrackJsonNode ->
                replaceKmM(locationTrackJsonNode.get("nodecollection"))
                parseJsonNode<RatkoLocationTrack>(locationTrackJsonNode, "getLocationTrack")
            }
        }
    }

    fun updateLocationTrackProperties(locationTrack: RatkoLocationTrack, oid: Oid<LocationTrack>) {
        logger.integrationCall("updateLocationTrackProperties", "locationTrack" to locationTrack)

        patchGeometryWithoutResponseBody(
            "$LOCATION_TRACK_PATCH_PATH/${oid}",
            locationTrack,
            RatkoPushTargetLocationTrack(oid),
        )
    }

    fun deleteLocationTrackPoints(locationTrackOid: RatkoOid<LocationTrack>, km: KmNumber?) {
        logger.integrationCall("deleteLocationTrackPoints", "locationTrackOid" to locationTrackOid, "km" to km)

        deletePoints(
            combinePaths(LOCATION_TRACK_POINTS_PATH_V1_0, locationTrackOid, km),
            RatkoPushTargetLocationTrack(locationTrackOid),
        )
    }

    fun deleteRouteNumberPoints(routeNumberOid: RatkoOid<LayoutTrackNumber>, km: KmNumber?) {
        logger.integrationCall("deleteRouteNumberPoints", "routeNumberOid" to routeNumberOid, "km" to km)

        deletePoints(
            combinePaths(ROUTE_NUMBER_POINTS_PATH, routeNumberOid, km),
            RatkoPushTargetTrackNumber(routeNumberOid),
        )
    }

    fun updateRouteNumberPoints(routeNumberOid: RatkoOid<LayoutTrackNumber>, points: List<RatkoPoint>) {
        logger.integrationCall(
            "updateRouteNumberPoints",
            "routeNumberOid" to routeNumberOid,
            "points" to "${points.first().kmM}..${points.last().kmM}",
        )

        points.chunked(1000).mapIndexed { index, chunk ->
            logger.integrationCall(
                "updateRouteNumberPoints",
                "routeNumberOid" to routeNumberOid,
                "chunk" to index,
                "points" to "${chunk.first().kmM}..${chunk.last().kmM}",
            )

            val pushTarget = RatkoPushTargetTrackNumber(routeNumberOid)
            patchGeometryWithoutResponseBody(combinePaths(ROUTE_NUMBER_POINTS_PATH, routeNumberOid), chunk, pushTarget)
        }
    }

    fun createRouteNumberPoints(routeNumberOid: RatkoOid<LayoutTrackNumber>, points: List<RatkoPoint>) {
        logger.integrationCall(
            "createRouteNumberPoints",
            "routeNumberOid" to routeNumberOid,
            "points" to "${points.first().kmM}..${points.last().kmM}",
        )

        points.chunked(1000).mapIndexed { index, chunk ->
            logger.integrationCall(
                "createRouteNumberPoints",
                "routeNumberOid" to routeNumberOid,
                "chunk" to index,
                "points" to "${chunk.first().kmM}..${chunk.last().kmM}",
            )

            val url = combinePaths(ROUTE_NUMBER_POINTS_PATH, routeNumberOid)
            val pushTarget = RatkoPushTargetTrackNumber(routeNumberOid)
            postSpec(url, chunk)
                .defaultErrorHandler(GEOMETRY, CREATE, pushTarget, url, chunk)
                .toBodilessEntity()
                .block(defaultBlockTimeout)
        }
    }

    fun updateLocationTrackPoints(locationTrackOid: RatkoOid<LocationTrack>, points: List<RatkoPoint>) {
        logger.integrationCall(
            "updateLocationTrackPoints",
            "locationTrackOid" to locationTrackOid,
            "points" to "${points.first().kmM}..${points.last().kmM}",
            "points.size" to points.size,
        )

        val url = "$LOCATION_TRACK_POINTS_PATH_V1_1/$locationTrackOid?updateKmMvalues=true"
        val pushTarget = RatkoPushTargetLocationTrack(locationTrackOid)
        patchGeometryWithoutResponseBody(url, points, pushTarget)
    }

    fun createLocationTrackPoints(locationTrackOid: RatkoOid<LocationTrack>, points: List<RatkoPoint>) {
        logger.integrationCall(
            "createLocationTrackPoints",
            "locationTrackOid" to locationTrackOid,
            "points" to "${points.first().kmM}..${points.last().kmM}",
        )

        points.chunked(1000).mapIndexed { index, chunk ->
            logger.integrationCall(
                "createLocationTrackPoints",
                "locationTrackOid" to locationTrackOid,
                "chunk" to index,
                "points" to "${chunk.first().kmM}..${chunk.last().kmM}",
            )

            val url = combinePaths(LOCATION_TRACK_POINTS_PATH_V1_0, locationTrackOid)
            val pushTarget = RatkoPushTargetLocationTrack(locationTrackOid)
            postSpec(url, chunk)
                .defaultErrorHandler(GEOMETRY, CREATE, pushTarget, url, chunk)
                .toBodilessEntity()
                .block(defaultBlockTimeout)
        }
    }

    fun patchLocationTrackPoints(
        sourceTrackExternalId: MainBranchRatkoExternalId<LocationTrack>,
        targetTrackExternalId: MainBranchRatkoExternalId<LocationTrack>,
        startAddress: TrackMeter,
        endAddress: TrackMeter,
    ) {
        logger.integrationCall(
            "patchLocationTrackPoints",
            "locationTrackOid" to targetTrackExternalId.oid,
            "locationtrackOidOfGeometry" to sourceTrackExternalId.oid,
            "startAddress" to startAddress,
            "endAddress" to endAddress,
        )

        val params =
            mapOf(
                "locationtrackOIDOfGeometry" to sourceTrackExternalId.oid.toString(),
                "locationtrackOIDOfGeometryStartKmM" to startAddress.stripTrailingZeroes(),
                "locationtrackOIDOfGeometryEndKmM" to endAddress.stripTrailingZeroes(),
            )

        val url = "$LOCATION_TRACK_PATCH_PATH/${targetTrackExternalId.oid}"
        val pushTarget = RatkoPushTargetLocationTrack(targetTrackExternalId.oid)

        client
            .patch()
            .uri { builder ->
                // Due to special handling of the plus character "+" (as it is treated as a space in a URL), the
                // following call uses a query param template.
                //
                // Plus characters are parts of track addresses, such as "0001+0123".
                builder.path(url).apply { params.forEach { (k, v) -> queryParam(k, "{$k}") } }.build(params)
            }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf<String, Any>())
            .retrieve()
            .defaultErrorHandler(GEOMETRY, UPDATE, pushTarget, "$url?$params")
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun forceRatkoToRedrawLocationTrack(locationTrackOids: Set<RatkoOid<LocationTrack>>) {
        logger.integrationCall("updateLocationTrackGeometryMValues", "locationTrackOids" to locationTrackOids)

        patchSpec(combinePaths(LOCATION_TRACK_PATH, "geom"), locationTrackOids.map { it.id })
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun newLocationTrack(
        locationTrack: RatkoLocationTrack,
        locationTrackOidOfGeometry: RatkoOid<LocationTrack>? = null,
    ): RatkoOid<LocationTrack>? {
        logger.integrationCall("newLocationTrack", "locationTrack" to locationTrack)
        assert(locationTrack.id != null && !isFakeOID(locationTrack.id)) {
            "Cannot push fake OID ${locationTrack.id} into Ratko"
        }

        val pushTarget = RatkoPushTargetLocationTrack(locationTrack)
        return locationTrackOidOfGeometry?.let { referencedGeometryOid ->
            val url = "$LOCATION_TRACK_PATH?locationtrackOidOfGeometry=${referencedGeometryOid.id}"
            postWithResponseBody(url, locationTrack, PROPERTIES, CREATE, pushTarget)
        } ?: postWithResponseBody(LOCATION_TRACK_PATH, locationTrack, PROPERTIES, CREATE, pushTarget)
    }

    fun newSwitch(switch: RatkoSwitchAsset): RatkoOid<LayoutSwitch>? {
        logger.integrationCall("newSwitch", "switch" to switch)
        val pushTarget = RatkoPushTargetSwitch(switch)
        return postWithJsonResponseBody(ASSET_PATH, switch.withoutGeometries(), PROPERTIES, CREATE, pushTarget)
            ?.let { response -> ratkoJsonMapper.readTree(response).firstOrNull()?.get("id")?.textValue() }
            ?.let(::RatkoOid)
    }

    fun newMetadataAsset(asset: RatkoMetadataAsset): RatkoOid<RatkoMetadataAsset>? {
        logger.integrationCall("newMetadataAsset", "asset" to asset)
        return postWithJsonResponseBody(ASSET_PATH, asset.withoutGeometries(), PROPERTIES, CREATE, null)
            ?.let { response -> ratkoJsonMapper.readTree(response).firstOrNull()?.get("id")?.textValue() }
            ?.let(::RatkoOid)
    }

    fun replaceSwitchLocations(assetOid: RatkoOid<LayoutSwitch>, locations: List<RatkoAssetLocation>) {
        logger.integrationCall("replaceSwitchLocations", "assetOid" to assetOid, "locations" to locations)
        val url = combinePaths(ASSET_PATH, assetOid, "locations")
        val pushTarget = RatkoPushTargetSwitch(assetOid)
        putWithoutResponseBody(url, locations.map { it.withoutGeometries() }, LOCATION, pushTarget)
    }

    fun replaceSwitchGeoms(assetOid: RatkoOid<LayoutSwitch>, geoms: Collection<RatkoAssetGeometry>) {
        logger.integrationCall("replaceSwitchGeoms", "assetOid" to assetOid, "geoms" to geoms)
        val url = combinePaths(ASSET_PATH, assetOid, "geoms")
        val pushTarget = RatkoPushTargetSwitch(assetOid)
        putWithoutResponseBody(url, geoms, GEOMETRY, pushTarget)
    }

    fun getSwitchAsset(assetOid: RatkoOid<LayoutSwitch>): RatkoSwitchAsset? {
        logger.integrationCall("getSwitchAsset", "assetOid" to assetOid)

        val pushTarget = RatkoPushTargetSwitch(assetOid)
        return getWithJsonResponseBody(combinePaths(ASSET_PATH, assetOid), PROPERTIES, pushTarget)
            ?.let { response -> ratkoJsonMapper.readTree(response) }
            ?.let { switchJsonNode ->
                switchJsonNode.get("assetGeoms")?.forEach { asset ->
                    (asset as ObjectNode).replace("geometry", asset.get("geometryOriginal"))
                }
                switchJsonNode.get("locations")?.forEach { location -> replaceKmM(location.get("nodecollection")) }
                parseJsonNode<RatkoSwitchAsset>(switchJsonNode, "getSwitchAsset")
            }
    }

    fun updateSwitchState(switchOid: RatkoOid<LayoutSwitch>, state: RatkoAssetState) {
        logger.integrationCall("updateSwitchState", "switchOid" to switchOid, "state" to state)

        val pushTarget = RatkoPushTargetSwitch(switchOid)
        val responseJson =
            getSpec(combinePaths(ASSET_PATH, switchOid))
                .bodyToMono<String>()
                .onErrorResume(WebClientResponseException::class.java) {
                    val response = parseErrorResponse(it.responseBodyAsString)
                    Mono.error(RatkoAssetPushException(PROPERTIES, FETCH_EXISTING, pushTarget, response, it))
                }
                .block(defaultBlockTimeout)

        val switchJsonObject = ObjectMapper().readTree(responseJson) as ObjectNode
        switchJsonObject.put("state", state.value)
        switchJsonObject.remove("temporalStartTime")
        switchJsonObject.get("properties")?.forEach { property -> (property as ObjectNode).remove("validityStart") }

        switchJsonObject.get("assetGeoms")?.forEach { geom ->
            (geom as ObjectNode).let {
                it.replace("geometry", it.get("geometryOriginal"))
                it.remove("validityStart")
            }
        }

        switchJsonObject.get("relationAssets")?.forEach { relation -> (relation as ObjectNode).remove("validityStart") }

        val updatedLocations = mutableListOf<JsonNode>()

        switchJsonObject.get("locations")?.forEach { location ->
            (location as ObjectNode).remove("validityStartTime")
            (location.get("nodecollection") as ObjectNode?)?.let { nodeCollection ->
                replaceKmM(nodeCollection)

                val validJoints =
                    nodeCollection.get("nodes")?.filter { node ->
                        val point = node.get("point") as ObjectNode?
                        (point?.get("state") as ObjectNode?)?.get("name")?.textValue() == RatkoPointStates.VALID.state
                    } ?: emptyList()

                if (validJoints.isNotEmpty()) {
                    val nodes = nodeCollection.putArray("nodes")
                    validJoints.forEach { node -> nodes.add(node) }

                    updatedLocations.add(location)
                }
            }
        }

        val locations = switchJsonObject.putArray("locations")
        updatedLocations.forEachIndexed { index, location ->
            (location as ObjectNode).put("priority", index + 1)
            locations.add(location)
        }

        (switchJsonObject.get("rowMetadata") as ObjectNode?)?.put("sourceName", GEOVIITE_NAME)

        switchJsonObject.remove("childAssets")

        putWithoutResponseBody(combinePaths(ASSET_PATH, switchOid), switchJsonObject.toString(), STATE, pushTarget)
    }

    fun updateSwitchProperties(switchOid: RatkoOid<LayoutSwitch>, properties: Collection<RatkoAssetProperty>) {
        logger.integrationCall("updateAssetProperties", "switchOid" to switchOid, "properties" to properties)

        val url = combinePaths(ASSET_PATH, switchOid, "properties")
        putWithoutResponseBody(url, properties, PROPERTIES, RatkoPushTargetSwitch(switchOid))
    }

    fun createPlanItem(ratkoPlanId: RatkoPlanId, realOid: Oid<*>?): RatkoPlanItemId {
        logger.integrationCall("createPlanItem")

        val url = "/api/plan/v1.0/plans/${ratkoPlanId.intValue}/plan_items"
        val content = RatkoPlanItem(id = null, state = RatkoPlanState.OPEN, planId = ratkoPlanId, realOid = realOid)
        val rv = postWithResponseBody<RatkoPlanItem>(url, content, PROPERTIES, CREATE, null)
        return requireNotNull(rv?.id) {
            "Expected plan item ID from Ratko when creating plan item in Ratko plan " +
                "${ratkoPlanId.intValue} with real OID $realOid"
        }
    }

    fun updatePlanItem(item: RatkoPlanItem) {
        logger.integrationCall("updatePlanItem")
        val id = requireNotNull(item.id).intValue
        putWithoutResponseBody("/api/plan/v1.0/plan_items/$id", item, PROPERTIES, null)
    }

    fun getNewLocationTrackOid(): RatkoOid<LocationTrack> {
        logger.integrationCall("getNewLocationTrackOid")
        return requireNotNull(postWithResponseBody(LOCATION_TRACK_PATH, "{}", PROPERTIES, CREATE, null))
    }

    fun getNewRouteNumberOid(): RatkoOid<LayoutTrackNumber> {
        logger.integrationCall("getNewRouteNumberOid")
        return requireNotNull(postWithResponseBody(ROUTE_NUMBER_PATH, "{}", PROPERTIES, CREATE, null))
    }

    fun getNewSwitchOid(): RatkoOid<LayoutSwitch> {
        logger.integrationCall("getNewSwitchOid")
        val body = "{\"type\":\"turnout\"}"
        return requireNotNull(
            postWithResponseBody<List<RatkoOid<LayoutSwitch>>>(ASSET_PATH, body, PROPERTIES, CREATE, null)
                ?.firstOrNull()
        )
    }

    fun getRouteNumber(routeNumberOid: RatkoOid<LayoutTrackNumber>): RatkoRouteNumber? {
        logger.integrationCall("getRouteNumber", "routeNumberOid" to routeNumberOid)

        return getWithJsonResponseBody(
                combinePaths(ROUTE_NUMBER_LOCATIONS_PATH, routeNumberOid),
                PROPERTIES,
                RatkoPushTargetTrackNumber(routeNumberOid),
            )
            ?.let { response ->
                ratkoJsonMapper.readTree(response).let { routeNumberJsonNode ->
                    replaceKmM(routeNumberJsonNode.get("nodecollection"))
                    parseJsonNode<RatkoRouteNumber>(routeNumberJsonNode, "getRouteNumber")
                }
            }
    }

    fun newRouteNumber(routeNumber: RatkoRouteNumber): RatkoOid<LayoutTrackNumber>? {
        logger.integrationCall("newRouteNumber", "routeNumber" to routeNumber)
        assert(routeNumber.id != null && !isFakeOID(routeNumber.id)) {
            "Cannot push fake OID ${routeNumber.id} into Ratko"
        }
        return postWithResponseBody(
            ROUTE_NUMBER_PATH,
            routeNumber,
            PROPERTIES,
            CREATE,
            RatkoPushTargetTrackNumber(routeNumber),
        )
    }

    fun forceRatkoToRedrawRouteNumber(routeNumberOids: Set<RatkoOid<LayoutTrackNumber>>) {
        logger.integrationCall("updateRouteNumberGeometryMValues", "routeNumberOids" to routeNumberOids)

        patchSpec(combinePaths(ROUTE_NUMBER_PATH, "geom"), routeNumberOids.map { it.id })
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun updateRouteNumber(routeNumber: RatkoRouteNumber) {
        logger.integrationCall("updateRouteNumber", "routeNumber" to routeNumber)

        putWithoutResponseBody(ROUTE_NUMBER_PATH, routeNumber, PROPERTIES, RatkoPushTargetTrackNumber(routeNumber))
    }

    fun fetchOperationalPoints(): List<RatkoOperationalPointParse> {
        logger.integrationCall("fetchOperationalPoints")
        val allPoints = mutableListOf<RatkoOperationalPointParse>()
        var pageNumber = 0
        do {
            logger.info("fetching operational points for page $pageNumber")
            val allAssetsInPage =
                requireNotNull(
                    postWithResponseBody<RatkoOperationalPointAssetsResponse>(
                            "$ASSET_PATH/search?fields=summary",
                            mapOf(
                                "assetType" to RatkoSwitchAssetType.RAILWAY_TRAFFIC_OPERATIONAL_POINT.value,
                                "pageNumber" to pageNumber++,
                                "size" to 100,
                                "sortOrder" to "ASC",
                                "secondarySortOrder" to "ASC",
                            ),
                            PROPERTIES,
                            CREATE,
                            null,
                        )
                        ?.assets
                ) {
                    "Failed to get operational points from Ratko (empty response)"
                }
            val validOperationalPointsInPage = allAssetsInPage.mapNotNull { parseAsset(it, logger) }
            allPoints.addAll(validOperationalPointsInPage)
        } while (allAssetsInPage.size == 100)
        return allPoints
    }

    fun startNewBulkTransfer(split: Split): Pair<IntId<BulkTransfer>, BulkTransferState> {
        logger.integrationCall("startNewBulkTransfer", "splitId" to split.id)

        val bulkTransferId =
            postWithResponseBody<RatkoBulkTransferResponse>(
                    url = "$BULK_TRANSFER_PATH/start",
                    content = mapOf("this-is-something-that-should-be-defined" to "in-the-future"),
                    PROPERTIES,
                    CREATE,
                    null,
                )
                ?.id
        checkNotNull(bulkTransferId) { "Received bulk transfer id was null" }

        // The state may also be received from the api regarding how it is created in Ratko's end.
        return bulkTransferId to BulkTransferState.IN_PROGRESS
    }

    fun pollBulkTransferState(bulkTransferId: IntId<BulkTransfer>): BulkTransferState {
        logger.integrationCall("pollBulkTransferState", "bulkTransferId" to bulkTransferId)

        return getSpec(url = "$BULK_TRANSFER_PATH/$bulkTransferId/state") // Should be changed when the URL is known
            .bodyToMono<String>()
            .onErrorResume(WebClientResponseException::class.java) {
                // TODO Figure out bulk transfer error handling
                Mono.error(it)
            }
            .block(defaultBlockTimeout)
            .let { response ->
                val bulkTransferState = response?.let {
                    parseJsonValue<RatkoBulkTransferResponse>(it, "pollBulkTransferState").state
                }
                checkNotNull(bulkTransferState) { "Received bulk transfer state was null!" }
            }
    }

    fun createPlan(plan: RatkoPlan): RatkoPlanId? {
        logger.integrationCall("createPlan", "plan" to plan)
        val rv = postWithResponseBody<RatkoPlanResponse>(url = PLAN_PATH, plan, PROPERTIES, CREATE, null)
        return rv?.id
    }

    fun updatePlan(plan: RatkoPlan) {
        logger.integrationCall("updatePlan", "plan" to plan)
        val ratkoId = requireNotNull(plan.id).intValue
        return putWithoutResponseBody(url = "$PLAN_PATH/$ratkoId", plan, PROPERTIES, null)
    }

    private fun replaceKmM(nodeCollection: JsonNode?) {
        nodeCollection?.get("nodes")?.forEach { node ->
            (node.get("point") as ObjectNode?)?.let { point ->
                val km = point.get("km")?.textValue()
                val m = point.get("m")?.textValue()

                if (km != null && m != null) {
                    point.put("kmM", RatkoTrackMeter(TrackMeter(km, m)).toString())
                }
            }
        }
    }

    private inline fun <reified T> parseJsonValue(json: String, context: String): T {
        return try {
            ratkoJsonMapper.readValue<T>(json)
        } catch (e: Exception) {
            logger.error(
                "Failed to parse Ratko JSON response as ${T::class.simpleName} in $context: " +
                    json.take(MAX_JSON_LOG_LENGTH),
                e,
            )
            throw e
        }
    }

    private inline fun <reified T> parseJsonNode(node: JsonNode, context: String): T {
        return try {
            ratkoJsonMapper.treeToValue<T>(node)
        } catch (e: Exception) {
            logger.error(
                "Failed to parse Ratko JSON node as ${T::class.simpleName} in $context: " +
                    node.toString().take(MAX_JSON_LOG_LENGTH),
                e,
            )
            throw e
        }
    }

    private fun postWithJsonResponseBody(
        url: String,
        content: Any,
        errorType: RatkoPushErrorType,
        operation: RatkoOperation,
        target: RatkoPushTarget<*>?,
    ): String? =
        postSpec(url, content)
            .defaultErrorHandler(errorType, operation, target, url, content)
            .bodyToMono<String>()
            .block(defaultBlockTimeout)

    private inline fun <reified TOut : Any> postWithResponseBody(
        url: String,
        content: Any,
        errorType: RatkoPushErrorType,
        operation: RatkoOperation,
        target: RatkoPushTarget<*>?,
    ): TOut? =
        postWithJsonResponseBody(url, content, errorType, operation, target)?.let { parseJsonValue<TOut>(it, url) }

    private fun getWithJsonResponseBody(
        url: String,
        errorType: RatkoPushErrorType,
        target: RatkoPushTarget<*>?,
    ): String? =
        getSpec(url)
            .defaultErrorHandler(errorType, FETCH_EXISTING, target, url)
            .bodyToMono<String>()
            .onErrorResume(WebClientResponseException::class.java) { ex ->
                if (ex.statusCode == NOT_FOUND) Mono.empty() else Mono.error(ex)
            }
            .block(defaultBlockTimeout)

    private fun putWithoutResponseBody(
        url: String,
        content: Any,
        errorType: RatkoPushErrorType,
        target: RatkoPushTarget<*>?,
    ) {
        putSpec(url, content)
            .defaultErrorHandler(errorType, UPDATE, target, url, content)
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    private fun patchGeometryWithoutResponseBody(url: String, content: Any, target: RatkoPushTarget<*>) {
        patchSpec(url, content)
            .defaultErrorHandler(GEOMETRY, UPDATE, target, url, content)
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    private fun deletePoints(url: String, target: RatkoPushTarget<*>) {
        deleteSpec(url)
            .defaultErrorHandler(GEOMETRY, DELETE, target, url)
            .toBodilessEntity()
            .onErrorResume(WebClientResponseException::class.java) { ex ->
                if (ex.statusCode == NOT_FOUND) Mono.empty() else Mono.error(ex)
            }
            .block(defaultBlockTimeout)
    }

    private fun deleteSpec(url: String) = client.delete().uri(url).retrieve()

    private fun putSpec(url: String, content: Any) =
        client.put().uri(url).contentType(MediaType.APPLICATION_JSON).bodyValue(content).retrieve()

    private fun postSpec(url: String, content: Any) =
        client.post().uri(url).contentType(MediaType.APPLICATION_JSON).bodyValue(content).retrieve()

    private fun patchSpec(url: String, content: Any) =
        client.patch().uri(url).contentType(MediaType.APPLICATION_JSON).bodyValue(content).retrieve()

    private fun getSpec(url: String) = client.get().uri(url).retrieve()

    private fun WebClient.ResponseSpec.defaultErrorHandler(
        errorType: RatkoPushErrorType,
        operation: RatkoOperation,
        target: RatkoPushTarget<*>?,
        requestUrl: String,
        requestBody: Any? = null,
    ) =
        onStatus(
            { status ->
                !status.is2xxSuccessful &&
                    !(status == NOT_FOUND && (operation == FETCH_EXISTING || operation == DELETE))
            },
            { response ->
                response.bodyToMono<String>().switchIfEmpty(Mono.just("")).flatMap { responseBody ->
                    val errorResponse = parseErrorResponse(responseBody)
                    val request = getBodyJsonForLog(requestBody).take(MAX_JSON_LOG_LENGTH)
                    val truncatedResponse = responseBody.take(MAX_JSON_LOG_LENGTH)
                    logger.error(
                        "Error during Ratko push: status=${response.statusCode()} responseBody=$truncatedResponse requestUrl=\"$requestUrl\" requestBody=$request"
                    )
                    Mono.error(
                        target?.let { RatkoAssetPushException(errorType, operation, target, errorResponse) }
                            ?: RatkoException(errorType, operation, errorResponse)
                    )
                }
            },
        )

    private fun parseErrorResponse(responseBody: String): RatkoErrorResponse? =
        responseBody.takeIf(String::isNotEmpty)?.let {
            runCatching { ratkoJsonMapper.readValue<RatkoErrorResponse>(responseBody) }.getOrNull()
        }

    private fun getBodyJsonForLog(body: Any?): String =
        runCatching { body?.let { it as? String ?: ratkoJsonMapper.writeValueAsString(it) } ?: "null" }
            .getOrDefault("[JSON Serialization failed!]")

    fun getSignalAsset(x: Int, y: Int, z: Int, cluster: Boolean): ByteArray? =
        getSpec("${combinePaths(MAP_ASSET_PATH, "$x", "$y", "$z")}?assetType=signal&cluster=${cluster}&state=IN USE")
            .onStatus(
                { !it.is2xxSuccessful },
                { response ->
                    response.bodyToMono<String>().switchIfEmpty(Mono.just("")).flatMap { body ->
                        logger.error(
                            "Error proxying signal asset fetch! HTTP Status code: ${response.statusCode()}, body: ${body.take(MAX_JSON_LOG_LENGTH)}"
                        )
                        Mono.empty()
                    }
                },
            )
            .bodyToMono<ByteArray>()
            .block(defaultBlockTimeout)
}

fun combinePaths(vararg paths: Any?) =
    paths
        .mapNotNull { it?.toString() } // otherwise null will toString() to "null"
        .joinToString("/")
        .replace(Regex("/+"), "/")
