package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.util.produceIf
import java.time.Instant

@GeoviiteService
class StationLinkService(
    private val operationalPointDao: OperationalPointDao,
    private val layoutSwitchDao: LayoutSwitchDao,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val locationTrackService: LocationTrackService,
) {

    fun getStationLinks(
        branch: LayoutBranch,
        moment: Instant,
        opFilter: IntId<OperationalPoint>? = null,
    ): List<StationLink> =
        getConnectingTracks(branch, moment, opFilter)
            .flatMap { connectingTrack -> calculateTrackConnections(connectingTrack, opFilter) }
            .let(::calculateStationLinks)

    fun getStationLinks(context: LayoutContext, opFilter: IntId<OperationalPoint>? = null): List<StationLink> =
        getConnectingTracks(context, opFilter)
            .flatMap { connectingTrack -> calculateTrackConnections(connectingTrack, opFilter) }
            .let(::calculateStationLinks)

    fun getStationLinks2(context: LayoutContext, opFilter: IntId<OperationalPoint>? = null): List<StationLink> =
        getConnectingTracks2(context, opFilter)
            .flatMap { connectingTrack -> calculateTrackConnections(connectingTrack, opFilter) }
            .let(::calculateStationLinks)

    private fun getConnectingTracks(
        branch: LayoutBranch,
        moment: Instant,
        opFilter: IntId<OperationalPoint>?,
    ): List<ConnectingTrack> {
        val tracksWithGeometry = locationTrackService.listOfficialWithGeometryAtMoment(branch, moment)
        val operationalPoints = operationalPointDao.listOfficialAtMoment(branch, moment)
        val switches = layoutSwitchDao.listOfficialAtMoment(branch, moment)
        val trackNumberIds = tracksWithGeometry.map { it.first.trackNumberId }
        val trackNumberVersions = layoutTrackNumberDao.fetchManyOfficialVersionsAtMoment(branch, trackNumberIds, moment)
        return createConnectionObjects(tracksWithGeometry, operationalPoints, switches, trackNumberVersions, opFilter)
    }

    private fun getConnectingTracks(context: LayoutContext, opFilter: IntId<OperationalPoint>?): List<ConnectingTrack> {
        val tracksWithGeometry = locationTrackService.listWithGeometries(context, includeDeleted = false)
        val operationalPoints = operationalPointDao.list(context, includeDeleted = false)
        val switches = layoutSwitchDao.list(context, includeDeleted = false)
        val trackNumberIds = tracksWithGeometry.map { it.first.trackNumberId }
        val trackNumberVersions = layoutTrackNumberDao.fetchVersions(context, trackNumberIds)
        return createConnectionObjects(tracksWithGeometry, operationalPoints, switches, trackNumberVersions, opFilter)
    }

    private fun getConnectingTracks2(
        context: LayoutContext,
        opFilter: IntId<OperationalPoint>?,
    ): List<ConnectingTrack> {
        val connectionVersions = operationalPointDao.getLinkingTrackVersions(context, opFilter)
        val tracksWithGeometry = locationTrackService.getManyWithGeometries(connectionVersions.keys.toList())
        val tracksById = tracksWithGeometry.associateBy { it.first.id }
        val operationalPoints = operationalPointDao.fetchManyByVersion(connectionVersions.values.flatten())
        val trackNumberIds = tracksWithGeometry.map { it.first.trackNumberId }
        val trackNumberVersions = layoutTrackNumberDao.fetchVersions(context, trackNumberIds).associateBy { it.id }
        return connectionVersions.map { (trackVersion, opVersions) ->
            val (track, geometry) = tracksById.getValue(trackVersion.id)
            val operationalPoints = opVersions.map(operationalPoints::getValue)
            val trackNumberVersion = trackNumberVersions.getValue(track.trackNumberId)
            ConnectingTrack(track, geometry, trackNumberVersion, operationalPoints)
        }
    }
}

private fun createConnectionObjects(
    tracksWithGeometry: List<Pair<LocationTrack, LocationTrackGeometry>>,
    operationalPoints: List<OperationalPoint>,
    switches: List<LayoutSwitch>,
    trackNumberVersions: List<LayoutRowVersion<LayoutTrackNumber>>,
    opFilter: IntId<OperationalPoint>?,
): List<ConnectingTrack> {
    val opsById = operationalPoints.associateBy { it.id as IntId }
    val switchIdToOpId = switches.mapNotNull { s -> s.operationalPointId?.let { s.id as IntId to it } }.associate { it }
    val tnVersionById = trackNumberVersions.associateBy { it.id }
    return tracksWithGeometry.mapNotNull { (track, geom) ->
        val switchConnections = track.switchIds.mapNotNull { switchId -> switchIdToOpId[switchId] }
        val operationalPoints = (track.operationalPointIds + switchConnections).distinct().map(opsById::getValue)
        produceIf(operationalPoints.isNotEmpty() && (opFilter == null || operationalPoints.any { it.id == opFilter })) {
            val trackNumberVersion = tnVersionById.getValue(track.trackNumberId)
            ConnectingTrack(track, geom, trackNumberVersion, operationalPoints)
        }
    }
}

private fun calculateStationLinks(trackConnections: List<TrackStationConnection>): List<StationLink> =
    trackConnections
        .groupBy { connection -> connection.stationLinkKey }
        .map { (_, connections) ->
            val shortestLink = connections.minBy { it.length }
            StationLink(
                // The track number + points will be the same on all connections, so just pick from the shortest one
                trackNumberVersion = shortestLink.trackNumberVersion,
                startOperationalPointVersion = shortestLink.stationPoint1.opVersion,
                endOperationalPointVersion = shortestLink.stationPoint2.opVersion,
                locationTrackVersions = connections.map { it.trackVersion }.sortedBy { it.id.intValue },
                // Use the shortest link length
                length = shortestLink.length.distance,
            )
        }
        .sortedWith(linkComparator)

private fun calculateTrackConnections(
    connectingTrack: ConnectingTrack,
    opFilter: IntId<OperationalPoint>? = null,
): List<TrackStationConnection> =
    connectingTrack.operationalPoints
        .mapNotNull { op -> toTrackStationPoint(op, connectingTrack.geometry) }
        .sortedBy { it.location.m }
        .zipWithNext()
        .filter { (p1, p2) -> p1.opId != p2.opId && (opFilter == null || opFilter == p1.opId || opFilter == p2.opId) }
        .map { (p1, p2) -> TrackStationConnection(connectingTrack.track, connectingTrack.trackNumberVersion, p1, p2) }

private fun toTrackStationPoint(op: OperationalPoint, geometry: LocationTrackGeometry): TrackStationPoint? =
    op.location?.let { opLocation ->
        geometry.getClosestPoint(opLocation)?.first?.let { trackLocation ->
            TrackStationPoint(op, trackLocation, calculateDistance(LAYOUT_SRID, opLocation, trackLocation))
        }
    }

private data class ConnectingTrack(
    val track: LocationTrack,
    val geometry: LocationTrackGeometry,
    val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    val operationalPoints: List<OperationalPoint>,
)

private data class TrackStationPoint(
    val op: OperationalPoint,
    val location: AlignmentPoint<LocationTrackM>,
    val distance: Double,
) {
    val opVersion: LayoutRowVersion<OperationalPoint>
        get() = op.getVersionOrThrow()

    val opId: IntId<OperationalPoint>
        get() = op.id as IntId
}

private data class StationLinkKey(
    val trackNumberId: IntId<LayoutTrackNumber>,
    val op1Id: IntId<OperationalPoint>,
    val op2Id: IntId<OperationalPoint>,
) {
    init {
        require(op1Id.intValue < op2Id.intValue) {
            "Station link key must be built in id-order to avoid duplicates: op1=$op1Id op2=$op2Id"
        }
    }
}

private data class TrackStationConnection(
    val track: LocationTrack,
    val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    val stationPoint1: TrackStationPoint,
    val stationPoint2: TrackStationPoint,
) {
    val trackVersion: LayoutRowVersion<LocationTrack>
        get() = track.getVersionOrThrow()

    val length: LineM<LocationTrackM> =
        abs(stationPoint2.location.m - stationPoint1.location.m) + stationPoint1.distance + stationPoint2.distance

    val stationLinkKey: StationLinkKey =
        StationLinkKey(
            track.trackNumberId,
            if (stationPoint1.opId.intValue < stationPoint2.opId.intValue) stationPoint1.opId else stationPoint2.opId,
            if (stationPoint1.opId.intValue < stationPoint2.opId.intValue) stationPoint2.opId else stationPoint1.opId,
        )
}

private val linkComparator =
    compareBy<StationLink>(
        { it.trackNumberId.intValue },
        { it.startOperationalPointId.intValue },
        { it.endOperationalPointId.intValue },
    )
