package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.util.produceIf
import java.time.Instant

@GeoviiteService
class StationLinkService(
    private val operationalPointDao: OperationalPointDao,
    private val layoutSwitchDao: LayoutSwitchDao,
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
        val operationalPoints =
            operationalPointDao.listOfficialAtMoment(branch, moment).associateBy(OperationalPoint::id)
        val switchIdToOpId =
            layoutSwitchDao
                .listOfficialAtMoment(branch, moment)
                .mapNotNull { s -> s.operationalPointId?.let { s.id as IntId to it } }
                .associate { it }
        return locationTrackService
            .listOfficialWithGeometryAtMoment(branch, moment)
            .flatMap { (track, geom) -> resolveTrackConnections(track, geom, switchIdToOpId) }
            .groupBy { connection -> connection.stationLinkKey }
            .map { (key, connections) ->
                val op1 = requireNotNull(operationalPoints[key.op1Id]) { "Operational point ${key.op1Id} not found" }
                val op2 = requireNotNull(operationalPoints[key.op2Id]) { "Operational point ${key.op2Id} not found" }
                val link = connections.minBy { it.length }
                StationLink(
                    trackNumberId = key.trackNumberId,
                    startOperationalPointId = key.op1Id,
                    endOperationalPointId = key.op2Id,
                    locationTrackIds = connections.map { it.track.id as IntId },
                    length =
                        link.length.distance +
                            calculateDistance(op1, link.op1Point) +
                            calculateDistance(op2, link.op2Point),
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
                produceIf(prev.opId != next.opId) { TrackStationConnection(track, prev, next) }
            }
}
