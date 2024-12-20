package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.split.BulkTransfer
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.someOid
import java.time.Instant

/*
Shadow clones of the parts of Ratko's API that we actually use, in the specific form that Ratko sends them, which can
be fairly unlike our own Ratko-prefixed classes.
*/
data class InterfaceRatkoGeometry(val type: RatkoGeometryType, val coordinates: List<Double>, val crs: RatkoCrs)

data class InterfaceRatkoAssetGeometry(
    val geometryOriginal: InterfaceRatkoGeometry,
    val geomType: RatkoAssetGeometryType,
    val assetGeomAccuracyType: RatkoAssetGeomAccuracyType,
)

data class InterfaceRatkoPoint(
    val km: String,
    val m: String,
    val geometry: InterfaceRatkoGeometry?,
    val state: RatkoPointState?,
    val rowMetadata: RatkoMetadata? = null,
    val locationtrack: RatkoOid<RatkoLocationTrack>? = null,
    val routenumber: RatkoOid<RatkoRouteNumber>? = null,
)

data class InterfaceRatkoNode(val nodeType: RatkoNodeType, val point: InterfaceRatkoPoint)

data class InterfaceRatkoNodes(val nodes: List<InterfaceRatkoNode> = listOf(), val type: RatkoNodesType)

data class InterfaceRatkoSwitch(
    val id: String?,
    val assetGeoms: List<InterfaceRatkoAssetGeometry>?,
    val state: RatkoAssetState?,
    val properties: List<RatkoAssetProperty>?,
    val locations: List<RatkoAssetLocation>?,
)

data class InterfaceRatkoLocationTrack(
    val id: String,
    val name: String,
    val description: String,
    val routenumber: RatkoOid<RatkoRouteNumber>?,
    val nodecollection: InterfaceRatkoNodes,
    val type: RatkoLocationTrackType,
    val state: RatkoLocationTrackState,
    val rowMetadata: RatkoMetadata,
    val duplicateOf: String?,
    val topologicalConnectivity: RatkoTopologicalConnectivityType,
)

data class InterfaceRatkoRouteNumber(
    val id: String?,
    val name: String,
    val description: String,
    val state: RatkoRouteNumberState,
    val rowMetadata: RatkoMetadata = RatkoMetadata(),
    val nodecollection: InterfaceRatkoNodes?,
)

fun ratkoRouteNumber(
    id: String,
    name: String = "routenumber-$id",
    description: String = "description $id",
    state: RatkoRouteNumberState = RatkoRouteNumberState(RatkoRouteNumberStateType.VALID),
    rowMetadata: RatkoMetadata = RatkoMetadata(),
    nodecollection: InterfaceRatkoNodes = InterfaceRatkoNodes(listOf(), RatkoNodesType.POINT),
) = InterfaceRatkoRouteNumber(id, name, description, state, rowMetadata, nodecollection)

fun ratkoLocationTrack(
    id: String,
    name: String = "trackname-$id",
    description: String = "description",
    routenumber: RatkoOid<RatkoRouteNumber> = RatkoOid("1.2.3.4.5"),
    nodecollection: InterfaceRatkoNodes = InterfaceRatkoNodes(listOf(), RatkoNodesType.POINT),
    type: RatkoLocationTrackType = RatkoLocationTrackType.MAIN,
    state: RatkoLocationTrackState = RatkoLocationTrackState.IN_USE,
    rowMetadata: RatkoMetadata = RatkoMetadata(),
    duplicateOf: String? = null,
    topologicalConnectivityType: RatkoTopologicalConnectivityType = RatkoTopologicalConnectivityType.NONE,
) =
    InterfaceRatkoLocationTrack(
        id,
        description,
        name,
        routenumber,
        nodecollection,
        type,
        state,
        rowMetadata,
        duplicateOf,
        topologicalConnectivityType,
    )

fun ratkoSwitch(
    oid: String,
    state: RatkoAssetState? = RatkoAssetState.IN_USE,
    properties: List<RatkoAssetProperty>? = listOf(),
    locations: List<RatkoAssetLocation>? = listOf(),
    assetGeoms: List<InterfaceRatkoAssetGeometry>? = listOf(),
): InterfaceRatkoSwitch =
    InterfaceRatkoSwitch(
        id = oid,
        state = state,
        properties = properties,
        locations = locations,
        assetGeoms = assetGeoms,
    )

fun bulkTransferStartRequest(
    sourceLocationTrackOid: Oid<LocationTrack> = someOid(),
    destinationLocationTracks: List<RatkoBulkTransferDestinationTrack> = emptyList(),
): RatkoBulkTransferStartRequest {
    return RatkoBulkTransferStartRequest(
        sourceLocationTrack = sourceLocationTrackOid,
        destinationLocationTracks = destinationLocationTracks,
    )
}

fun ratkoBulkTransferDestinationTrack(
    oid: Oid<LocationTrack> = someOid(),
    startKmM: RatkoTrackMeter = RatkoTrackMeter.create("0000+0000"),
    endKmM: RatkoTrackMeter = RatkoTrackMeter.create("0001+1000"),
): RatkoBulkTransferDestinationTrack {
    return RatkoBulkTransferDestinationTrack(oid = oid, startKmM = startKmM, endKmM = endKmM)
}

fun bulkTransferPollResponse(
    bulkTransferId: IntId<BulkTransfer>,
    sourceLocationTrackOid: Oid<LocationTrack> = someOid(),
    destinationLocationTracks: List<RatkoBulkTransferDestinationTrack> =
        listOf(ratkoBulkTransferDestinationTrack(), ratkoBulkTransferDestinationTrack()),
    startKmM: TrackMeter = TrackMeter("0000+0000"),
    endKmM: TrackMeter = TrackMeter("0001+1000"),
    startTime: Instant? = null,
    endTime: Instant? = null,
    assetsToMove: Int? = null,
    locationTrackChangeAssetsAmount: Int? = null,
    trexAssets: Int? = null,
    remainingTrexAssets: Int? = null,
): RatkoBulkTransferPollResponse {
    return RatkoBulkTransferPollResponse(
        locationTrackChangeAssetsAmount = locationTrackChangeAssetsAmount,
        remainingTrexAssets = remainingTrexAssets,
        locationTrackChange =
            RatkoBulkTransferPollResponseLocationTrackChange(
                id = bulkTransferId,
                sourceLocationTrackOid = sourceLocationTrackOid,
                destinationLocationTracks = destinationLocationTracks,
                startKmM = startKmM,
                endKmM = endKmM,
                assetsToMove = assetsToMove,
                trexAssets = trexAssets,
                startTime = startTime,
                endTime = endTime,
            ),
    )
}

fun bulkTransferPollResponseInProgress(bulkTransferId: IntId<BulkTransfer>): RatkoBulkTransferPollResponse {
    return bulkTransferPollResponse(
        bulkTransferId = bulkTransferId,
        startTime = Instant.now(),
        assetsToMove = 1,
        trexAssets = 1,
        locationTrackChangeAssetsAmount = 0,
        remainingTrexAssets = 0,
    )
}

fun bulkTransferPollResponseCreated(bulkTransferId: IntId<BulkTransfer>): RatkoBulkTransferPollResponse {
    return bulkTransferPollResponse(bulkTransferId = bulkTransferId)
}

fun bulkTransferPollResponseFinished(
    bulkTransferId: IntId<BulkTransfer>,
    endTime: Instant = Instant.now(),
): RatkoBulkTransferPollResponse {
    val inProgress = bulkTransferPollResponseInProgress(bulkTransferId)
    return inProgress.copy(locationTrackChange = inProgress.locationTrackChange.copy(endTime = endTime))
}
