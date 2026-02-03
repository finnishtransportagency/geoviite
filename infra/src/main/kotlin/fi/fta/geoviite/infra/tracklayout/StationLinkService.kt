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
        val opId: IntId<OperationalPoint>,
        val location: AlignmentPoint<LocationTrackM>,
    )

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
        val length: LineM<LocationTrackM> = abs(op2Point.location.m - op1Point.location.m)

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
        fun getOp(id: IntId<OperationalPoint>): OperationalPoint =
            requireNotNull(operationalPointsById[id]) { "Operational point $id not found" }
        val trackNumberVersionById = trackNumberVersions.associateBy { it.id }
        val switchIdToOpId =
            switches.mapNotNull { s -> s.operationalPointId?.let { s.id as IntId to it } }.associate { it }

        return tracksWithGeometry
            .flatMap { (track, geom) ->
                val trackVersion = track.getVersionOrThrow()
                resolveTrackConnections(trackVersion, track, geom, switchIdToOpId)
            }
            .groupBy { connection -> connection.stationLinkKey }
            .map { (key, connections) ->
                val op1 = getOp(key.op1Id)
                val op2 = getOp(key.op2Id)
                val shortestLink = connections.minBy { it.length }
                val trackNumberVersion =
                    requireNotNull(trackNumberVersionById[key.trackNumberId]) {
                        "Track number version ${key.trackNumberId} not found"
                    }
                StationLink(
                    trackNumberVersion = trackNumberVersion,
                    startOperationalPointVersion = op1.getVersionOrThrow(),
                    endOperationalPointVersion = op2.getVersionOrThrow(),
                    locationTrackVersions = connections.map { it.trackVersion },
                    length =
                        shortestLink.length.distance +
                            calculateDistance(op1, shortestLink.op1Point) +
                            calculateDistance(op2, shortestLink.op2Point),
                )
            }
    }

    private fun calculateDistance(op: OperationalPoint, point: TrackStationPoint): Double =
        calculateDistance(
            LAYOUT_SRID,
            point.location,
            requireNotNull(op.location) { "Operational point has no location defined: id=${op.id}" },
        )

    private fun resolveTrackConnections(
        trackVersion: LayoutRowVersion<LocationTrack>,
        track: LocationTrack,
        geometry: LocationTrackGeometry,
        switchIdToOpId: Map<IntId<LayoutSwitch>, IntId<OperationalPoint>>,
    ): List<TrackStationConnection> =
        geometry.trackSwitchLinks
            .mapNotNull { link ->
                switchIdToOpId[link.switchId]?.let { opId -> TrackStationPoint(opId, link.location) }
            }
            .zipWithNext()
            .mapNotNull { (prev, next) ->
                produceIf(prev.opId != next.opId) { TrackStationConnection(trackVersion, track, prev, next) }
            }
}
