package fi.fta.geoviite.infra.ratko

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MainBranchRatkoExternalId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.logging.integrationCall
import fi.fta.geoviite.infra.ratko.model.GEOVIITE_NAME
import fi.fta.geoviite.infra.ratko.model.RatkoAsset
import fi.fta.geoviite.infra.ratko.model.RatkoAssetGeometry
import fi.fta.geoviite.infra.ratko.model.RatkoAssetLocation
import fi.fta.geoviite.infra.ratko.model.RatkoAssetProperty
import fi.fta.geoviite.infra.ratko.model.RatkoAssetState
import fi.fta.geoviite.infra.ratko.model.RatkoBulkTransferResponse
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPointAssetsResponse
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
import fi.fta.geoviite.infra.ratko.model.RatkoTrackMeter
import fi.fta.geoviite.infra.ratko.model.parseAsset
import fi.fta.geoviite.infra.split.BulkTransfer
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

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

const val TRACK_NUMBER_FAKE_OID_CONTEXT = 10001
const val LOCATION_TRACK_FAKE_OID_CONTEXT = 10002
const val SWITCH_FAKE_OID_CONTEXT = 139

enum class RatkoConnectionStatus { ONLINE, ONLINE_ERROR, OFFLINE, NOT_CONFIGURED,
}

@Component
@ConditionalOnBean(RatkoFakeOidGeneratorConfiguration::class)
class RatkoFakeOidGenerator {

    fun <T> generateFakeRatkoOID(contextId: Int, uniqueIdInContext: Int): RatkoOid<T> {
        // make fake OID clearly distinct from real OIDs
        return RatkoOid("0.0.0.0.0.0.${contextId}.${uniqueIdInContext}")
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

    fun getLocationTrack(locationTrackOid: RatkoOid<RatkoLocationTrack>): RatkoLocationTrack? {
        logger.integrationCall("getLocationTrack", "locationTrackOid" to locationTrackOid)

        return getSpec(combinePaths(LOCATION_TRACK_LOCATIONS_PATH, locationTrackOid))
            .bodyToMono<String>()
            .onErrorResume(WebClientResponseException::class.java) {
                Mono.error(RatkoPushException(RatkoPushErrorType.LOCATION, RatkoOperation.FETCH_EXISTING, it))
            }
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

        putWithoutResponseBody(LOCATION_TRACK_PATH, locationTrack)
    }

    fun deleteLocationTrackPoints(locationTrackOid: RatkoOid<RatkoLocationTrack>, km: KmNumber?) {
        logger.integrationCall("deleteLocationTrackPoints", "locationTrackOid" to locationTrackOid, "km" to km)

        deletePoints(combinePaths(LOCATION_TRACK_POINTS_PATH_V1_0, locationTrackOid, km))
    }

    fun deleteRouteNumberPoints(routeNumberOid: RatkoOid<RatkoRouteNumber>, km: KmNumber?) {
        logger.integrationCall("deleteRouteNumberPoints", "routeNumberOid" to routeNumberOid, "km" to km)

        deletePoints(combinePaths(ROUTE_NUMBER_POINTS_PATH, routeNumberOid, km))
    }

    fun updateRouteNumberPoints(routeNumberOid: RatkoOid<RatkoRouteNumber>, points: List<RatkoPoint>) {
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

            patchWithoutResponseBody(combinePaths(ROUTE_NUMBER_POINTS_PATH, routeNumberOid), chunk)
        }
    }

    fun createRouteNumberPoints(routeNumberOid: RatkoOid<RatkoRouteNumber>, points: List<RatkoPoint>) {
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

            postSpec(combinePaths(ROUTE_NUMBER_POINTS_PATH, routeNumberOid), chunk)
                .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.CREATE)
                .toBodilessEntity()
                .block(defaultBlockTimeout)
        }
    }

    fun updateLocationTrackPoints(locationTrackOid: RatkoOid<RatkoLocationTrack>, points: List<RatkoPoint>) {
        logger.integrationCall(
            "updateLocationTrackPoints",
            "locationTrackOid" to locationTrackOid,
            "points" to "${points.first().kmM}..${points.last().kmM}",
            "points.size" to points.size,
        )

        val url = "$LOCATION_TRACK_POINTS_PATH_V1_1/$locationTrackOid?updateKmMvalues=true"
        patchWithoutResponseBody(url, points)
    }

    fun createLocationTrackPoints(locationTrackOid: RatkoOid<RatkoLocationTrack>, points: List<RatkoPoint>) {
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

            postSpec(combinePaths(LOCATION_TRACK_POINTS_PATH_V1_0, locationTrackOid), chunk)
                .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.CREATE)
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
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun forceRatkoToRedrawLocationTrack(locationTrackOids: Set<RatkoOid<RatkoLocationTrack>>) {
        logger.integrationCall("updateLocationTrackGeometryMValues", "locationTrackOids" to locationTrackOids)

        patchSpec(combinePaths(LOCATION_TRACK_PATH, "geom"), locationTrackOids.map { it.id })
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun newLocationTrack(
        locationTrack: RatkoLocationTrack,
        locationTrackOidOfGeometry: RatkoOid<RatkoLocationTrack>? = null,
    ): RatkoOid<RatkoLocationTrack>? {
        logger.integrationCall("newLocationTrack", "locationTrack" to locationTrack)

        return locationTrackOidOfGeometry?.let { referencedGeometryOid ->
            postWithResponseBody(
                "$LOCATION_TRACK_PATH?locationtrackOidOfGeometry=${referencedGeometryOid.id}",
                locationTrack,
            )
        } ?: postWithResponseBody(LOCATION_TRACK_PATH, locationTrack)
    }

    fun <T : RatkoAsset> newAsset(asset: RatkoAsset): RatkoOid<T>? {
        logger.integrationCall("newAsset", "asset" to asset)

        return postWithResponseBody<String>(ASSET_PATH, asset.withoutGeometries())
            ?.let { response -> ratkoJsonMapper.readTree(response).firstOrNull()?.get("id")?.textValue() }
            ?.let(::RatkoOid)
    }

    fun <T : RatkoAsset> replaceAssetLocations(assetOid: RatkoOid<T>, locations: List<RatkoAssetLocation>) {
        logger.integrationCall("replaceAssetLocations", "assetOid" to assetOid, "locations" to locations)

        putWithoutResponseBody(
            combinePaths(ASSET_PATH, assetOid, "locations"),
            locations.map { it.withoutGeometries() },
            RatkoPushErrorType.LOCATION,
        )
    }

    fun <T : RatkoAsset> replaceAssetGeoms(assetOid: RatkoOid<T>, geoms: Collection<RatkoAssetGeometry>) {
        logger.integrationCall("replaceAssetGeoms", "assetOid" to assetOid, "geoms" to geoms)

        putWithoutResponseBody(combinePaths(ASSET_PATH, assetOid, "geoms"), geoms, RatkoPushErrorType.GEOMETRY)
    }

    fun <T : RatkoAsset> getSwitchAsset(assetOid: RatkoOid<T>): RatkoSwitchAsset? {
        logger.integrationCall("getSwitchAsset", "assetOid" to assetOid)

        return getSpec(combinePaths(ASSET_PATH, assetOid))
            .bodyToMono<String>()
            .onErrorResume(WebClientResponseException::class.java) {
                if (HttpStatus.NOT_FOUND == it.statusCode) Mono.empty()
                else Mono.error(RatkoPushException(RatkoPushErrorType.PROPERTIES, RatkoOperation.FETCH_EXISTING, it))
            }
            .block(defaultBlockTimeout)
            ?.let { response ->
                ratkoJsonMapper.readTree(response).let { switchJsonNode ->
                    switchJsonNode.get("assetGeoms")?.map { asset ->
                        (asset as ObjectNode).replace("geometry", asset.get("geometryOriginal"))
                    }

                    switchJsonNode.get("locations")?.forEach { location -> replaceKmM(location.get("nodecollection")) }

                    ratkoJsonMapper.treeToValue(switchJsonNode, RatkoSwitchAsset::class.java)
                }
            }
    }

    fun <T : RatkoAsset> updateAssetState(assetOid: RatkoOid<T>, state: RatkoAssetState) {
        logger.integrationCall("updateAssetState", "assetOid" to assetOid, "state" to state)

        val responseJson =
            getSpec(combinePaths(ASSET_PATH, assetOid))
                .bodyToMono<String>()
                .onErrorResume(WebClientResponseException::class.java) {
                    Mono.error(RatkoPushException(RatkoPushErrorType.PROPERTIES, RatkoOperation.FETCH_EXISTING, it))
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

        putWithoutResponseBody(
            combinePaths(ASSET_PATH, assetOid),
            switchJsonObject.toString(),
            RatkoPushErrorType.STATE,
        )
    }

    fun <T : RatkoAsset> updateAssetProperties(assetOid: RatkoOid<T>, properties: Collection<RatkoAssetProperty>) {
        logger.integrationCall("updateAssetProperties", "assetOid" to assetOid, "properties" to properties)

        putWithoutResponseBody(combinePaths(ASSET_PATH, assetOid, "properties"), properties)
    }

    fun createPlanItem(ratkoPlanId: RatkoPlanId, realOid: Oid<*>?): RatkoPlanItemId {
        logger.integrationCall("createPlanItem")

        val rv =
            postWithResponseBody<RatkoPlanItem>(
                "/api/plan/v1.0/plans/${ratkoPlanId.intValue}/plan_items",
                RatkoPlanItem(id = null, state = RatkoPlanState.OPEN, planId = ratkoPlanId, realOid = realOid),
            )
        return requireNotNull(rv?.id) {
            "Expected plan item ID from Ratko when creating plan item in Ratko plan " +
                "${ratkoPlanId.intValue} with real OID $realOid"
        }
    }

    fun updatePlanItem(item: RatkoPlanItem) {
        logger.integrationCall("updatePlanItem")
        val id = requireNotNull(item.id).intValue
        putWithoutResponseBody("/api/plan/v1.0/plan_items/$id", item)
    }

    fun getNewLocationTrackOid(): RatkoOid<RatkoLocationTrack> {
        logger.integrationCall("getNewLocationTrackOid")

        return requireNotNull(postWithResponseBody(LOCATION_TRACK_PATH, "{}"))
    }

    fun getNewRouteNumberOid(): RatkoOid<RatkoRouteNumber> {
        logger.integrationCall("getNewRouteNumberOid")

        return requireNotNull(postWithResponseBody(ROUTE_NUMBER_PATH, "{}"))
    }

    fun getNewSwitchOid(): RatkoOid<RatkoSwitchAsset> {
        logger.integrationCall("getNewSwitchOid")

        val body = "{\"type\":\"turnout\"}"

        return requireNotNull(postWithResponseBody<List<RatkoOid<RatkoSwitchAsset>>>(ASSET_PATH, body)?.firstOrNull())
    }

    fun getRouteNumber(routeNumberOid: RatkoOid<RatkoRouteNumber>): RatkoRouteNumber? {
        logger.integrationCall("getRouteNumber", "routeNumberOid" to routeNumberOid)

        return getSpec(combinePaths(ROUTE_NUMBER_LOCATIONS_PATH, routeNumberOid))
            .bodyToMono<String>()
            .onErrorResume(WebClientResponseException::class.java) {
                if (HttpStatus.NOT_FOUND == it.statusCode) Mono.empty()
                else Mono.error(RatkoPushException(RatkoPushErrorType.PROPERTIES, RatkoOperation.FETCH_EXISTING, it))
            }
            .block(defaultBlockTimeout)
            ?.let { response ->
                ratkoJsonMapper.readTree(response).let { routeNumberJsonNode ->
                    replaceKmM(routeNumberJsonNode.get("nodecollection"))

                    ratkoJsonMapper.treeToValue(routeNumberJsonNode, RatkoRouteNumber::class.java)
                }
            }
    }

    fun newRouteNumber(routeNumber: RatkoRouteNumber): RatkoOid<RatkoRouteNumber>? {
        logger.integrationCall("newRouteNumber", "routeNumber" to routeNumber)

        return postWithResponseBody(ROUTE_NUMBER_PATH, routeNumber)
    }

    fun forceRatkoToRedrawRouteNumber(routeNumberOids: Set<RatkoOid<RatkoRouteNumber>>) {
        logger.integrationCall("updateRouteNumberGeometryMValues", "routeNumberOids" to routeNumberOids)

        patchSpec(combinePaths(ROUTE_NUMBER_PATH, "geom"), routeNumberOids.map { it.id })
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    fun updateRouteNumber(routeNumber: RatkoRouteNumber) {
        logger.integrationCall("updateRouteNumber", "routeNumber" to routeNumber)

        putWithoutResponseBody(ROUTE_NUMBER_PATH, routeNumber)
    }

    fun fetchOperationalPoints(): List<RatkoOperationalPointParse> {
        logger.integrationCall("fetchOperatingPoints")
        val allPoints = mutableListOf<RatkoOperationalPointParse>()
        var pageNumber = 0
        do {
            logger.info("fetching operating points for page $pageNumber")
            val body =
                (postWithResponseBody<String>(
                    "$ASSET_PATH/search?fields=summary",
                    mapOf(
                        "assetType" to "railway_traffic_operating_point",
                        "pageNumber" to pageNumber++,
                        "size" to 100,
                        "sortOrder" to "ASC",
                        "secondarySortOrder" to "ASC",
                    ),
                ))
            val allAssetsInPage =
                ratkoJsonMapper.readValue(body, RatkoOperatingPointAssetsResponse::class.java)?.assets ?: listOf()
            val validOperatingPointsInPage = allAssetsInPage.mapNotNull { parseAsset(it, logger) }
            allPoints.addAll(validOperatingPointsInPage)
        } while (allAssetsInPage.size == 100)
        return allPoints
    }

    fun startNewBulkTransfer(split: Split): Pair<IntId<BulkTransfer>, BulkTransferState> {
        logger.integrationCall("startNewBulkTransfer", "splitId" to split.id)

        val body =
            postWithResponseBody<String>(
                url = "$BULK_TRANSFER_PATH/start",
                content = mapOf("this-is-something-that-should-be-defined" to "in-the-future"),
            )

        val bulkTransferId = ratkoJsonMapper.readValue(body, RatkoBulkTransferResponse::class.java)?.id
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
                val bulkTransferState =
                    ratkoJsonMapper.readValue(response, RatkoBulkTransferResponse::class.java)?.state

                checkNotNull(bulkTransferState) { "Received bulk transfer id was null!" }

                bulkTransferState
            }
    }

    fun createPlan(plan: RatkoPlan): RatkoPlanId? {
        logger.integrationCall("createPlan", "plan" to plan)
        val rv = postWithResponseBody<RatkoPlanResponse>(url = PLAN_PATH, plan)
        return rv?.id
    }

    fun updatePlan(plan: RatkoPlan) {
        logger.integrationCall("updatePlan", "plan" to plan)
        val ratkoId = requireNotNull(plan.id).intValue
        return putWithoutResponseBody(url = "$PLAN_PATH/$ratkoId", plan)
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

    private inline fun <reified TOut : Any> postWithResponseBody(url: String, content: Any): TOut? =
        postSpec(url, content)
            .defaultErrorHandler(RatkoPushErrorType.PROPERTIES, RatkoOperation.CREATE)
            .bodyToMono<TOut>()
            .block(defaultBlockTimeout)

    private fun putWithoutResponseBody(
        url: String,
        content: Any,
        errorType: RatkoPushErrorType = RatkoPushErrorType.PROPERTIES,
    ) {
        putSpec(url, content)
            .defaultErrorHandler(errorType, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    private fun patchWithoutResponseBody(url: String, content: Any) {
        patchSpec(url, content)
            .defaultErrorHandler(RatkoPushErrorType.GEOMETRY, RatkoOperation.UPDATE)
            .toBodilessEntity()
            .block(defaultBlockTimeout)
    }

    private fun deletePoints(url: String) {
        deleteSpec(url)
            .toBodilessEntity()
            .onErrorResume(WebClientResponseException::class.java) { response ->
                if (HttpStatus.NOT_FOUND == response.statusCode || HttpStatus.BAD_REQUEST == response.statusCode)
                    Mono.empty()
                else {
                    logger.error(
                        "Error during Ratko push! HTTP Status code: ${response.statusCode}, body: ${response.responseBodyAsString}"
                    )
                    Mono.error(RatkoPushException(RatkoPushErrorType.GEOMETRY, RatkoOperation.DELETE, response))
                }
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

    private fun WebClient.ResponseSpec.defaultErrorHandler(errorType: RatkoPushErrorType, operation: RatkoOperation) =
        onStatus(
            { !it.is2xxSuccessful },
            { response ->
                response.bodyToMono<String>().switchIfEmpty(Mono.just("")).flatMap { body ->
                    logger.error("Error during Ratko push! HTTP Status code: ${response.statusCode()}, body: $body")
                    Mono.error(RatkoPushException(errorType, operation))
                }
            },
        )
}

fun combinePaths(vararg paths: Any?) =
    paths
        .mapNotNull { it?.toString() } // otherwise null will toString() to "null"
        .joinToString("/")
        .replace(Regex("/+"), "/")
