package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.ratko.model.*

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
    val isPlanContext: Boolean,
    val planItemIds: List<Int>?,
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
    val isPlanContext: Boolean,
    val planItemIds: List<Int>?,
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
    planItemIds: List<Int>? = null,
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
        planItemIds = planItemIds,
        isPlanContext = !planItemIds.isNullOrEmpty(),
    )

fun ratkoSwitch(
    oid: String,
    state: RatkoAssetState? = RatkoAssetState.IN_USE,
    properties: List<RatkoAssetProperty>? = listOf(),
    locations: List<RatkoAssetLocation>? = listOf(),
    assetGeoms: List<InterfaceRatkoAssetGeometry>? = listOf(),
    planItemIds: List<Int>? = null,
): InterfaceRatkoSwitch =
    InterfaceRatkoSwitch(
        id = oid,
        state = state,
        properties = properties,
        locations = locations,
        assetGeoms = assetGeoms,
        planItemIds = planItemIds,
        isPlanContext = !planItemIds.isNullOrEmpty(),
    )
