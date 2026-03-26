package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.model.OperationalPointRatoType
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
    private val geocodingService: GeocodingService,
    private val routingService: RoutingService,
) {
    fun getStationLinks(
        branch: LayoutBranch,
        moment: Instant,
        opFilter: IntId<OperationalPoint>? = null,
    ): List<StationLink> =
        getLinkData(branch, moment, opFilter)
            .let { (data, routeCalculator) ->
                calculateTrackConnections(data, routeCalculator::getPathToStation, opFilter)
            }
            .let(::combineToStationLinks)

    fun getStationLinks(context: LayoutContext, opFilter: IntId<OperationalPoint>? = null): List<StationLink> =
        getLinkData(context, opFilter)
            .let { (data, routeCalculator) ->
                calculateTrackConnections(data, routeCalculator::getPathToStation, opFilter)
            }
            .let(::combineToStationLinks)

    private fun getLinkData(
        branch: LayoutBranch,
        moment: Instant,
        opFilter: IntId<OperationalPoint>?,
    ): Pair<StationLinkData, RouteCalculator> {
        val tracksWithGeometry = locationTrackService.listOfficialWithGeometryAtMoment(branch, moment)
        val operationalPoints =
            operationalPointDao
                .listOfficialAtMoment(branch, moment)
                .filter { it.ratoType != OperationalPointRatoType.OLP }
                .associateBy { it.id as IntId }
        val switches = layoutSwitchDao.listOfficialAtMoment(branch, moment)
        val connectingTracks = createConnectingTracks(tracksWithGeometry, switches, opFilter, operationalPoints.keys)
        val trackNumberIds = tracksWithGeometry.map { it.first.trackNumberId }
        val trackNumberVersions =
            layoutTrackNumberDao.fetchManyOfficialVersionsAtMoment(branch, trackNumberIds, moment).associateBy { it.id }
        val linkData = StationLinkData(trackNumberVersions, connectingTracks, operationalPoints)
        val routeCalculator =
            RouteCalculator(
                operationalPoints,
                connectingTracks,
                routingGraph = routingService.getGraph(branch, moment),
                getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment),
            )
        return linkData to routeCalculator
    }

    private fun getLinkData(
        context: LayoutContext,
        opFilter: IntId<OperationalPoint>?,
    ): Pair<StationLinkData, RouteCalculator> {
        val tracksWithGeometry = locationTrackService.listWithGeometries(context, includeDeleted = false)
        val operationalPoints =
            operationalPointDao
                .list(context, includeDeleted = false)
                .filter { it.ratoType != OperationalPointRatoType.OLP }
                .associateBy { it.id as IntId }
        val switches = layoutSwitchDao.list(context, includeDeleted = false)
        val connectingTracks = createConnectingTracks(tracksWithGeometry, switches, opFilter, operationalPoints.keys)
        val trackNumberIds = tracksWithGeometry.map { it.first.trackNumberId }
        val trackNumberVersions = layoutTrackNumberDao.fetchVersions(context, trackNumberIds).associateBy { it.id }
        val linkData = StationLinkData(trackNumberVersions, connectingTracks, operationalPoints)
        val routeCalculator =
            RouteCalculator(
                operationalPoints,
                connectingTracks,
                routingGraph = routingService.getGraph(context),
                getGeocodingContext = geocodingService.getLazyGeocodingContexts(context),
            )
        return linkData to routeCalculator
    }
}

private fun createConnectingTracks(
    tracksWithGeometry: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
    switches: List<LayoutSwitch>,
    opFilter: IntId<OperationalPoint>?,
    existingOps: Set<IntId<OperationalPoint>>,
): Map<IntId<LocationTrack>, ConnectingTrack> {
    val switchIdToOpId = switches.mapNotNull { s -> s.operationalPointId?.let { s.id as IntId to it } }.associate { it }
    return tracksWithGeometry
        .mapNotNull { (track, geom) ->
            val switchConnections = track.switchIds.mapNotNull { switchId -> switchIdToOpId[switchId] }
            val operationalPoints =
                (track.operationalPointIds + switchConnections).filter(existingOps::contains).distinct()
            produceIf(
                operationalPoints.isNotEmpty() && (opFilter == null || operationalPoints.any { it == opFilter })
            ) {
                ConnectingTrack(track, geom, operationalPoints)
            }
        }
        .associateBy { it.id }
}

private fun calculateTrackConnections(
    stationLinkData: StationLinkData,
    getPathToStation: (LocationTrackCacheHit, IntId<OperationalPoint>) -> Pair<TrackMeter, Double>?,
    opFilter: IntId<OperationalPoint>? = null,
): List<TrackStationConnection> {
    val stationConnectionPairs =
        stationLinkData.connectingTracks.values
            .filter { opFilter == null || it.operationalPointIds.contains(opFilter) }
            .flatMap { track ->
                stationLinkData
                    .getClosestTrackStationLocations(track)
                    .sortedBy { it.second.closestPoint.m }
                    .zipWithNext()
                    .filter { (p1, p2) ->
                        p1.first != p2.first && (opFilter == null || opFilter == p1.first || opFilter == p2.first)
                    }
            }

    return stationConnectionPairs
        .parallelStream()
        .map { (connection1, connection2) ->
            val (op1Id, op1ClosestPoint) = connection1
            val (op2Id, op2ClosestPoint) = connection2
            val trackDistance = abs(op2ClosestPoint.closestPoint.m - op1ClosestPoint.closestPoint.m).distance
            val s1Link = getPathToStation(op1ClosestPoint, op1Id)
            val s2Link = getPathToStation(op2ClosestPoint, op2Id)
            if (s1Link != null && s2Link != null) {
                val distance = s1Link.second + trackDistance + s2Link.second
                TrackStationConnection(
                    trackVersion = op1ClosestPoint.track.getVersionOrThrow(),
                    trackNumberVersion =
                        stationLinkData.trackNumberVersions.getValue(op1ClosestPoint.track.trackNumberId),
                    station1Version = stationLinkData.operationalPoints.getValue(op1Id).getVersionOrThrow(),
                    station2Version = stationLinkData.operationalPoints.getValue(op2Id).getVersionOrThrow(),
                    length = distance,
                    startAddress = s1Link.first,
                    endAddress = s2Link.first,
                )
            } else null
        }
        .toList()
        .filterNotNull()
}

private fun combineToStationLinks(trackConnections: List<TrackStationConnection>): List<StationLink> =
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

private data class RouteCalculator(
    val operationalPoints: Map<IntId<OperationalPoint>, OperationalPoint>,
    val connectingTracks: Map<IntId<LocationTrack>, ConnectingTrack>,
    val routingGraph: RoutingGraph,
    val getGeocodingContext: (IntId<LayoutTrackNumber>) -> GeocodingContext<ReferenceLineM>?,
) {
    private val connectableStations = ConcurrentHashMap<IntId<OperationalPoint>, Optional<ConnectableStation>>()

    fun getPathToStation(
        fromTrackPoint: LocationTrackCacheHit,
        stationId: IntId<OperationalPoint>,
    ): Pair<TrackMeter, Double>? =
        getConnectableStation(stationId)?.connectingLocations?.let { stationLocations ->
            stationLocations
                // If any station point is on the same track, we can just skip the routing
                .firstOrNull { (_, location) -> location.track.id == fromTrackPoint.track.id }
                ?.let { (address, location) ->
                    address to abs(location.closestPoint.m - fromTrackPoint.closestPoint.m).distance
                }
                // If not, we need to route all points to find the shortest path
                ?: stationLocations
                    .mapNotNull { (address, location) ->
                        routingGraph.findPath(fromTrackPoint, location)?.totalLength?.let { address to it }
                    }
                    .minByOrNull { it.second }
        }

    private fun getConnectableStation(opId: IntId<OperationalPoint>): ConnectableStation? =
        connectableStations
            .computeIfAbsent(opId) { id ->
                val op = operationalPoints.getValue(id)
                op.location
                    ?.let { opLocation -> getOpConnectingLocations(id, opLocation, getGeocodingContext) }
                    ?.let { ConnectableStation(op, it) }
                    .let { Optional.ofNullable(it) }
            }
            .getOrNull()

    private fun getOpConnectingLocations(
        opId: IntId<OperationalPoint>,
        opLocation: Point,
        getGeocodingContext: (IntId<LayoutTrackNumber>) -> GeocodingContext<ReferenceLineM>?,
    ): List<Pair<TrackMeter, LocationTrackCacheHit>> =
        connectingTracks.values
            .filter { t -> t.operationalPointIds.contains(opId) }
            .mapNotNull { track ->
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
}

private data class StationLinkData(
    val trackNumberVersions: Map<IntId<LayoutTrackNumber>, LayoutRowVersion<LayoutTrackNumber>>,
    val connectingTracks: Map<IntId<LocationTrack>, ConnectingTrack>,
    val operationalPoints: Map<IntId<OperationalPoint>, OperationalPoint>,
) {
    fun getClosestTrackStationLocations(
        track: ConnectingTrack
    ): List<Pair<IntId<OperationalPoint>, LocationTrackCacheHit>> =
        track.operationalPointIds.mapNotNull { opId -> getClosestTrackPoint(track.id, opId)?.let { opId to it } }

    private val closestTrackPoints =
        ConcurrentHashMap<Pair<IntId<LocationTrack>, IntId<OperationalPoint>>, Optional<LocationTrackCacheHit>>()

    fun getClosestTrackPoint(trackId: IntId<LocationTrack>, opId: IntId<OperationalPoint>): LocationTrackCacheHit? =
        closestTrackPoints
            .computeIfAbsent(trackId to opId) { (tId, oId) ->
                val track = connectingTracks.getValue(tId)
                val op = operationalPoints.getValue(oId)
                val closestPoint =
                    op.location?.let { location ->
                        track.geometry.getClosestPoint(location)?.first?.let { closest ->
                            // The distance (op <-> track-point) is not a part of the route -> set distance to 0.0
                            LocationTrackCacheHit(track.track, track.geometry, closest, 0.0)
                        }
                    }
                Optional.ofNullable(closestPoint)
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
