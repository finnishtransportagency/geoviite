package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.util.produceIf
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

@GeoviiteService
class StationLinkService(
    private val operationalPointDao: OperationalPointDao,
    private val layoutSwitchDao: LayoutSwitchDao,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val locationTrackService: LocationTrackService,
    private val switchLibraryService: SwitchLibraryService,
    private val geocodingService: GeocodingService,
) {
    fun getStationLinks(
        branch: LayoutBranch,
        moment: Instant,
        opFilter: IntId<OperationalPoint>? = null,
    ): List<StationLink> =
        getLinkData(branch, moment, opFilter).calculateTrackConnections(opFilter).let(::calculateStationLinks)

    fun getStationLinks(context: LayoutContext, opFilter: IntId<OperationalPoint>? = null): List<StationLink> =
        getLinkData(context, opFilter).calculateTrackConnections(opFilter).let(::calculateStationLinks)

    private fun getLinkData(
        branch: LayoutBranch,
        moment: Instant,
        opFilter: IntId<OperationalPoint>?,
    ): StationLinkData {
        val tracksWithGeometry = locationTrackService.listOfficialWithGeometryAtMoment(branch, moment)
        val operationalPoints = operationalPointDao.listOfficialAtMoment(branch, moment)
        val switches = layoutSwitchDao.listOfficialAtMoment(branch, moment)
        val trackNumberIds = tracksWithGeometry.map { it.first.trackNumberId }
        val trackNumberVersions = layoutTrackNumberDao.fetchManyOfficialVersionsAtMoment(branch, trackNumberIds, moment)
        return StationLinkData(
            trackNumberVersions = trackNumberVersions.associateBy { it.id },
            connectingTracks = createConnectingTracks(tracksWithGeometry, switches, opFilter),
            operationalPoints = operationalPoints.associateBy { it.id as IntId },
            routingGraph =
                buildGraph(
                    trackGeoms = tracksWithGeometry.map { it.second },
                    switches = switches,
                    structures = switchLibraryService.getSwitchStructuresById(),
                ),
            getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment),
        )
    }

    private fun getLinkData(context: LayoutContext, opFilter: IntId<OperationalPoint>?): StationLinkData {
        val tracksWithGeometry = locationTrackService.listWithGeometries(context, includeDeleted = false)
        val operationalPoints = operationalPointDao.list(context, includeDeleted = false)
        val switches = layoutSwitchDao.list(context, includeDeleted = false)
        val trackNumberIds = tracksWithGeometry.map { it.first.trackNumberId }
        val trackNumberVersions = layoutTrackNumberDao.fetchVersions(context, trackNumberIds)
        return StationLinkData(
            trackNumberVersions = trackNumberVersions.associateBy { it.id },
            connectingTracks = createConnectingTracks(tracksWithGeometry, switches, opFilter),
            operationalPoints = operationalPoints.associateBy { it.id as IntId },
            routingGraph =
                buildGraph(
                    trackGeoms = tracksWithGeometry.map { it.second },
                    switches = switches,
                    structures = switchLibraryService.getSwitchStructuresById(),
                ),
            getGeocodingContext = geocodingService.getLazyGeocodingContexts(context),
        )
    }
}

private fun createConnectingTracks(
    tracksWithGeometry: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
    switches: List<LayoutSwitch>,
    opFilter: IntId<OperationalPoint>?,
): Map<IntId<LocationTrack>, ConnectingTrack> {
    val switchIdToOpId = switches.mapNotNull { s -> s.operationalPointId?.let { s.id as IntId to it } }.associate { it }
    return tracksWithGeometry
        .mapNotNull { (track, geom) ->
            val switchConnections = track.switchIds.mapNotNull { switchId -> switchIdToOpId[switchId] }
            val operationalPoints = (track.operationalPointIds + switchConnections).distinct()
            produceIf(
                operationalPoints.isNotEmpty() && (opFilter == null || operationalPoints.any { it == opFilter })
            ) {
                ConnectingTrack(track, geom, operationalPoints)
            }
        }
        .associateBy { it.id }
}

private fun calculateStationLinks(trackConnections: List<TrackStationConnection>): List<StationLink> =
    trackConnections
        .groupBy { connection -> connection.stationLinkKey }
        .map { (_, connections) ->
            val shortestLink = connections.minBy { it.length }
            StationLink(
                // The track number + stations will be the same on all connections, so just pick from the shortest one
                trackNumberVersion = shortestLink.trackNumberVersion,
                startOperationalPointVersion = shortestLink.station1Version,
                endOperationalPointVersion = shortestLink.station2Version,
                locationTrackVersions = connections.map { it.trackVersion }.sortedBy { it.id.intValue },
                // Use the shortest link length
                startAddress = shortestLink.startAddress,
                endAddress = shortestLink.endAddress,
                length = shortestLink.length,
            )
        }
        .sortedWith(linkComparator)

private fun toConnectableStation(
    op: OperationalPoint,
    connectedTracks: List<ConnectingTrack>,
    getGeocodingContext: (IntId<LayoutTrackNumber>) -> GeocodingContext<ReferenceLineM>?,
): ConnectableStation? =
    op.location
        ?.let { getOfficialTrackPoints(it, connectedTracks, getGeocodingContext) }
        ?.let { ConnectableStation(op, it) }

private fun getOfficialTrackPoints(
    opLocation: Point,
    connectedTracks: List<ConnectingTrack>,
    getGeocodingContext: (IntId<LayoutTrackNumber>) -> GeocodingContext<ReferenceLineM>?,
): List<Pair<TrackMeter, LocationTrackCacheHit>> =
    connectedTracks.mapNotNull { track ->
        getGeocodingContext(track.trackNumberId)?.let { context ->
            context
                .getAddress(opLocation)
                ?.takeIf { (_, intersect) -> intersect == IntersectType.WITHIN }
                ?.let { (opAddress, _) -> context.getTrackLocation(track.geometry, opAddress) }
                ?.let { trackLocation ->
                    // The distance (op <-> track-point) is not a part of the route -> set distance to 0.0
                    val hit = LocationTrackCacheHit(track.track, track.geometry, trackLocation.point, 0.0)
                    trackLocation.address to hit
                }
        }
    }

private fun getClosestTrackPoint(
    track: LocationTrack,
    geometry: DbLocationTrackGeometry,
    location: IPoint,
): LocationTrackCacheHit? =
    geometry.getClosestPoint(location)?.first?.let { closest ->
        // The distance (op <-> track-point) is not a part of the route -> set distance to 0.0
        LocationTrackCacheHit(track, geometry, closest, 0.0)
    }

private data class StationLinkData(
    val trackNumberVersions: Map<IntId<LayoutTrackNumber>, LayoutRowVersion<LayoutTrackNumber>>,
    val connectingTracks: Map<IntId<LocationTrack>, ConnectingTrack>,
    val operationalPoints: Map<IntId<OperationalPoint>, OperationalPoint>,
    val routingGraph: RoutingGraph,
    val getGeocodingContext: (IntId<LayoutTrackNumber>) -> GeocodingContext<ReferenceLineM>?,
) {

    fun calculateTrackConnections(opFilter: IntId<OperationalPoint>? = null): List<TrackStationConnection> =
        connectingTracks.values
            .filter { opFilter == null || it.operationalPointIds.contains(opFilter) }
            .flatMap { track ->
                getClosestTrackStationLocations(track)
                    .sortedBy { it.second.closestPoint.m }
                    .zipWithNext()
                    .filter { (p1, p2) ->
                        p1.first != p2.first && (opFilter == null || opFilter == p1.first || opFilter == p2.first)
                    }
                    .mapNotNull { (p1, p2) -> getTrackStationConnection(p1.first, p1.second, p2.first, p2.second) }
            }

    private fun getClosestTrackStationLocations(
        track: ConnectingTrack
    ): List<Pair<IntId<OperationalPoint>, LocationTrackCacheHit>> =
        track.operationalPointIds.mapNotNull { opId -> getClosestTrackPoint(track.id, opId)?.let { opId to it } }

    fun getTrackStationConnection(
        op1Id: IntId<OperationalPoint>,
        op1Hit: LocationTrackCacheHit,
        op2Id: IntId<OperationalPoint>,
        op2Hit: LocationTrackCacheHit,
    ): TrackStationConnection? {
        val trackDistance = abs(op2Hit.closestPoint.m - op1Hit.closestPoint.m).distance
        val s1Link = getRoute(op1Hit, op1Id)
        val s2Link = getRoute(op2Hit, op2Id)
        return if (s1Link != null && s2Link != null) {
            val distance = s1Link.second + trackDistance + s2Link.second
            TrackStationConnection(
                trackVersion = op1Hit.track.getVersionOrThrow(),
                trackNumberVersion = trackNumberVersions.getValue(op1Hit.track.trackNumberId),
                station1Version = operationalPoints.getValue(op1Id).getVersionOrThrow(),
                station2Version = operationalPoints.getValue(op2Id).getVersionOrThrow(),
                length = distance,
                startAddress = s1Link.first,
                endAddress = s2Link.first,
            )
        } else null
    }

    private fun getRoute(
        fromTrackPoint: LocationTrackCacheHit,
        stationId: IntId<OperationalPoint>,
    ): Pair<TrackMeter, Double>? =
        getConnectableStation(stationId)
            ?.connectingLocations
            ?.mapNotNull { (address, location) ->
                routingGraph.findPath(fromTrackPoint, location)?.totalLength?.let { address to it }
            }
            ?.minByOrNull { it.second }

    private val closestTrackPoints =
        ConcurrentHashMap<Pair<IntId<LocationTrack>, IntId<OperationalPoint>>, Optional<LocationTrackCacheHit>>()

    fun getClosestTrackPoint(trackId: IntId<LocationTrack>, opId: IntId<OperationalPoint>): LocationTrackCacheHit? =
        closestTrackPoints
            .computeIfAbsent(trackId to opId) { (tId, oId) ->
                val track = connectingTracks.getValue(tId)
                val closest =
                    operationalPoints.getValue(oId).location?.let { location ->
                        getClosestTrackPoint(track.track, track.geometry, location)
                    }
                Optional.ofNullable(closest)
            }
            .getOrNull()

    private val connectableStations = ConcurrentHashMap<IntId<OperationalPoint>, Optional<ConnectableStation>>()

    fun getConnectableStation(opId: IntId<OperationalPoint>): ConnectableStation? =
        connectableStations
            .computeIfAbsent(opId) { id ->
                val op = operationalPoints.getValue(id)
                val tracks = connectingTracks.values.filter { t -> t.operationalPointIds.contains(id) }
                Optional.ofNullable(toConnectableStation(op, tracks, getGeocodingContext))
            }
            .getOrNull()
}

private data class ConnectableStation(
    val op: OperationalPoint,
    val connectingLocations: List<Pair<TrackMeter, LocationTrackCacheHit>>,
)

private data class ConnectingTrack(
    val track: LocationTrack,
    val geometry: DbLocationTrackGeometry,
    val operationalPointIds: List<IntId<OperationalPoint>>,
) {
    val id: IntId<LocationTrack> = track.id as IntId
    val trackNumberId: IntId<LayoutTrackNumber> = track.trackNumberId
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
    val trackVersion: LayoutRowVersion<LocationTrack>,
    val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    val station1Version: LayoutRowVersion<OperationalPoint>,
    val station2Version: LayoutRowVersion<OperationalPoint>,
    val startAddress: TrackMeter,
    val endAddress: TrackMeter,
    val length: Double,
) {
    val stationLinkKey: StationLinkKey =
        StationLinkKey(
            trackNumberVersion.id,
            if (station1Version.id.intValue < station2Version.id.intValue) station1Version.id else station2Version.id,
            if (station1Version.id.intValue < station2Version.id.intValue) station2Version.id else station1Version.id,
        )
}

private val linkComparator =
    compareBy<StationLink>(
        { it.startOperationalPointId.intValue },
        { it.endOperationalPointId.intValue },
        { it.trackNumberId.intValue },
    )
