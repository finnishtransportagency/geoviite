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

    private data class TrackStationPoint(
        val op: OperationalPoint,
        val location: AlignmentPoint<LocationTrackM>,
        val distance: Double,
    ) {
        val opId: IntId<OperationalPoint>
            get() = op.id as IntId
    }

    private data class StationLinkKey(
        val trackNumberId: IntId<LayoutTrackNumber>,
        val op1Id: IntId<OperationalPoint>,
        val op2Id: IntId<OperationalPoint>,
    ) {
        init {
            require(op1Id.intValue < op2Id.intValue)
        }
    }

    private data class TrackStationConnection(
        val trackVersion: LayoutRowVersion<LocationTrack>,
        val track: LocationTrack,
        val op1Point: TrackStationPoint,
        val op2Point: TrackStationPoint,
    ) {
        val length: LineM<LocationTrackM> =
            abs(op2Point.location.m - op1Point.location.m) + op1Point.distance + op2Point.distance

        val stationLinkKey: StationLinkKey =
            StationLinkKey(
                track.trackNumberId,
                if (op1Point.opId.intValue < op2Point.opId.intValue) op1Point.opId else op2Point.opId,
                if (op1Point.opId.intValue < op2Point.opId.intValue) op2Point.opId else op1Point.opId,
            )
    }

    fun getStationLinks(branch: LayoutBranch, moment: Instant): List<StationLink> {
        val operationalPoints = operationalPointDao.listOfficialAtMoment(branch, moment)
        val switches = layoutSwitchDao.listOfficialAtMoment(branch, moment)
        val tracksWithGeometry = locationTrackService.listOfficialWithGeometryAtMoment(branch, moment)
        val trackNumberIds = tracksWithGeometry.map { it.first.trackNumberId }.distinct()
        val trackNumberVersions = layoutTrackNumberDao.fetchManyOfficialVersionsAtMoment(branch, trackNumberIds, moment)
        return calculateStationLinks(operationalPoints, switches, tracksWithGeometry, trackNumberVersions)
    }

    fun getStationLinks(context: LayoutContext): List<StationLink> {
        val operationalPoints = operationalPointDao.list(context, includeDeleted = false)
        val switches = layoutSwitchDao.list(context, includeDeleted = false)
        val tracksWithGeometry = locationTrackService.listWithGeometries(context, includeDeleted = false)
        val trackNumberIds = tracksWithGeometry.map { it.first.trackNumberId }.distinct()
        val trackNumberVersions = layoutTrackNumberDao.fetchVersions(context, trackNumberIds)
        return calculateStationLinks(operationalPoints, switches, tracksWithGeometry, trackNumberVersions)
    }

    private fun calculateStationLinks(
        operationalPoints: List<OperationalPoint>,
        switches: List<LayoutSwitch>,
        tracksWithGeometry: List<Pair<LocationTrack, LocationTrackGeometry>>,
        trackNumberVersions: List<LayoutRowVersion<LayoutTrackNumber>>,
    ): List<StationLink> {
        val operationalPointsById = operationalPoints.associateBy { it.id as IntId }
        val trackNumberVersionById = trackNumberVersions.associateBy { it.id }
        val switchIdToOpId =
            switches.mapNotNull { s -> s.operationalPointId?.let { s.id as IntId to it } }.associate { it }

        return tracksWithGeometry
            .flatMap { (track, geom) ->
                val trackVersion = track.getVersionOrThrow()
                resolveTrackConnections(trackVersion, track, geom, switchIdToOpId, operationalPointsById)
            }
            .groupBy { connection -> connection.stationLinkKey }
            .map { (key, connections) ->
                val shortestLink = connections.minBy { it.length }
                val trackNumberVersion =
                    requireNotNull(trackNumberVersionById[key.trackNumberId]) {
                        "Track number version ${key.trackNumberId} not found"
                    }
                StationLink(
                    trackNumberVersion = trackNumberVersion,
                    // The points will be the same on all connections, so just pick from the shortest one
                    startOperationalPointVersion = shortestLink.op1Point.op.getVersionOrThrow(),
                    endOperationalPointVersion = shortestLink.op2Point.op.getVersionOrThrow(),
                    locationTrackVersions = connections.map { it.trackVersion },
                    // Use the shortest link length
                    length = shortestLink.length.distance,
                )
            }
    }

    private fun resolveTrackConnections(
        trackVersion: LayoutRowVersion<LocationTrack>,
        track: LocationTrack,
        geometry: LocationTrackGeometry,
        switchIdToOpId: Map<IntId<LayoutSwitch>, IntId<OperationalPoint>>,
        operationalPoints: Map<IntId<OperationalPoint>, OperationalPoint>,
    ): List<TrackStationConnection> {
        val connectedOpIds =
            track.switchIds.mapNotNull { switchId -> switchIdToOpId[switchId] } + track.operationalPointIds
        return connectedOpIds
            .map { id -> requireNotNull(operationalPoints[id]) { "Operational point $id not found" } }
            .mapNotNull { op -> toTrackStationPoint(op, geometry) }
            .sortedBy { it.location.m }
            .zipWithNext()
            .mapNotNull { (prev, next) ->
                produceIf(prev.opId != next.opId) { TrackStationConnection(trackVersion, track, prev, next) }
            }
    }

    private fun toTrackStationPoint(op: OperationalPoint, geometry: LocationTrackGeometry): TrackStationPoint? =
        op.location?.let { opLocation ->
            geometry.getClosestPoint(opLocation)?.first?.let { trackLocation ->
                TrackStationPoint(op, trackLocation, calculateDistance(LAYOUT_SRID, opLocation, trackLocation))
            }
        }
}
