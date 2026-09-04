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
import fi.fta.geoviite.infra.publication.ValidationContext
import fi.fta.geoviite.infra.ratko.model.OperationalPointRatoType
import fi.fta.geoviite.infra.tracklayout.StationLinkIssueType.SUSPICIOUSLY_LONG_ROUTE
import fi.fta.geoviite.infra.tracklayout.StationLinkIssueType.UNREACHABLE_STATION_MIDPOINT
import fi.fta.geoviite.infra.util.Either
import fi.fta.geoviite.infra.util.Left
import fi.fta.geoviite.infra.util.Right
import fi.fta.geoviite.infra.util.produceIf
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.jvm.optionals.getOrNull
import kotlin.to

@GeoviiteService
class StationLinkService(
    private val operationalPointDao: OperationalPointDao,
    private val layoutSwitchDao: LayoutSwitchDao,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val locationTrackDao: LocationTrackDao,
    private val locationTrackService: LocationTrackService,
    private val geocodingService: GeocodingService,
    private val routingService: RoutingService,
) {
    fun getStationLinks(
        branch: LayoutBranch,
        moment: Instant,
        opFilter: IntId<OperationalPoint>? = null,
    ): StationLinkResult =
        getLinkData(branch, moment)
            .let { (data, routeCalculator) ->
                calculateTrackConnections(data, routeCalculator::getPathToStation, opFilter)
            }
            .let { (connections, issues) ->
                StationLinkResult(
                    links = connections.let(::combineToStationLinks),
                    issues = issues,
                )
            }

    fun getStationLinks(
        context: LayoutContext,
        opFilter: IntId<OperationalPoint>? = null,
    ): StationLinkResult =
        getLinkData(context)
            .let { (data, routeCalculator) ->
                calculateTrackConnections(data, routeCalculator::getPathToStation, opFilter)
            }
            .let { (connections, issues) ->
                StationLinkResult(
                    links = connections.let(::combineToStationLinks),
                    issues = issues,
                )
            }

    fun getStationLinks(context: ValidationContext): StationLinkResult =
        getLinkData(context)
            .let { (data, routeCalculator) -> calculateTrackConnections(data, routeCalculator::getPathToStation) }
            .let { (connections, issues) ->
                StationLinkResult(
                    links = connections.let(::combineToStationLinks),
                    issues = issues,
                )
            }

    private fun getLinkData(
        branch: LayoutBranch,
        moment: Instant,
    ): Pair<StationLinkData, RouteCalculator> {
        val tracksWithGeometry = locationTrackService.listOfficialWithGeometryAtMoment(branch, moment)
        val operationalPoints =
            operationalPointDao
                .listOfficialAtMoment(branch, moment)
                .filter { it.ratoType != OperationalPointRatoType.OLP && it.exists }
                .associateBy { it.id as IntId }
        val switches = layoutSwitchDao.listOfficialAtMoment(branch, moment).filter { it.exists }
        val connectingTracks = createConnectingTracks(tracksWithGeometry, switches, operationalPoints.keys)
        val trackNumberIds = connectingTracks.values.map { it.track.trackNumberId }
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

    private fun getLinkData(context: LayoutContext): Pair<StationLinkData, RouteCalculator> {
        val tracksWithGeometry = locationTrackService.listWithGeometries(context, includeDeleted = false)
        val operationalPoints =
            operationalPointDao
                .list(context, includeDeleted = false)
                .filter { it.ratoType != OperationalPointRatoType.OLP }
                .associateBy { it.id as IntId }
        val switches = layoutSwitchDao.list(context, includeDeleted = false)
        val connectingTracks = createConnectingTracks(tracksWithGeometry, switches, operationalPoints.keys)
        val trackNumberIds = connectingTracks.values.map { it.track.trackNumberId }
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

    private fun getLinkData(context: ValidationContext): Pair<StationLinkData, RouteCalculator> {
        val tracksWithGeometry =
            getMergedData(
                context,
                locationTrackDao,
                getCandidates = { context.publicationSet.locationTracks },
                getValues = { locationTrackService.getManyWithGeometries(it) },
            )
        val operationalPoints =
            getMergedData(
                    context,
                    operationalPointDao,
                    getCandidates = { context.publicationSet.operationalPoints },
                    getValues = { operationalPointDao.fetchMany(it) },
                )
                .filter { it.ratoType != OperationalPointRatoType.OLP }
                .associateBy { it.id as IntId }
        val switches =
            getMergedData(
                context,
                layoutSwitchDao,
                getCandidates = { context.publicationSet.switches },
                getValues = { layoutSwitchDao.fetchMany(it) },
            )
        val connectingTracks = createConnectingTracks(tracksWithGeometry, switches, operationalPoints.keys)
        val trackNumberIds = connectingTracks.values.map { it.track.trackNumberId }
        val trackNumberVersions = run {
            val baseById =
                layoutTrackNumberDao.fetchVersions(context.target.baseContext, trackNumberIds).associateBy { it.id }
            val candidateById = context.publicationSet.trackNumbers.associateBy { it.id }
            (baseById + candidateById)
        }
        val linkData = StationLinkData(trackNumberVersions, connectingTracks, operationalPoints)
        val routeCalculator =
            RouteCalculator(
                operationalPoints,
                connectingTracks,
                routingGraph =
                    routingService.getGraph(
                        context,
                        tracksWithGeometry.map { (_, g) -> g.trackRowVersion },
                        switches.mapNotNull { it.version },
                    ),
                getGeocodingContext = context::getGeocodingContext,
            )
        return linkData to routeCalculator
    }
}

private fun <T : LayoutAsset<T>, V, SaveParams> getMergedData(
    context: ValidationContext,
    dao: LayoutAssetDao<T, SaveParams>,
    getCandidates: () -> List<LayoutRowVersion<T>>,
    getValues: (List<LayoutRowVersion<T>>) -> List<V>,
): List<V> {
    val baseById = dao.fetchVersions(context.target.baseContext, includeDeleted = false).associateBy { it.id }
    val candidateById = getCandidates().associateBy { it.id }
    val merged = (baseById + candidateById).values.toList() // candidates overwrite when ids match
    return getValues(merged)
}

private fun createConnectingTracks(
    tracksWithGeometry: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
    switches: List<LayoutSwitch>,
    existingOps: Set<IntId<OperationalPoint>>,
): Map<IntId<LocationTrack>, ConnectingTrack> {
    val switchIdToOpId = switches.mapNotNull { s -> s.operationalPointId?.let { s.id as IntId to it } }.associate { it }
    return tracksWithGeometry
        .mapNotNull { (track, geom) ->
            val switchConnections = track.switchIds.mapNotNull { switchId -> switchIdToOpId[switchId] }
            val opIds = (track.operationalPointIds + switchConnections).filter(existingOps::contains).distinct()
            produceIf(opIds.isNotEmpty()) { ConnectingTrack(track, geom, opIds) }
        }
        .associateBy { it.id }
}

private fun calculateTrackConnections(
    stationLinkData: StationLinkData,
    getPathToStation: (PointNearTrack, IntId<OperationalPoint>) -> StationPath?,
    opFilter: IntId<OperationalPoint>? = null,
): Pair<List<TrackStationConnection>, List<StationLinkIssue>> {
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
        .flatMap { (connection1, connection2) ->
            val (op1Id, op1ClosestPoint) = connection1
            val (op2Id, op2ClosestPoint) = connection2
            val trackDistance = abs(op2ClosestPoint.closestPoint.m - op1ClosestPoint.closestPoint.m).distance
            val s1Link = getPathToStation(op1ClosestPoint, op1Id)
            val s2Link = getPathToStation(op2ClosestPoint, op2Id)

            buildList {
                addIssues(s1Link, s2Link, op1Id, op2Id, op1ClosestPoint, op2ClosestPoint)

                if (s1Link != null && s2Link != null) {
                    val distance = s1Link.routeDistance + trackDistance + s2Link.routeDistance
                    add(
                        Left(
                            TrackStationConnection(
                                trackVersion = op1ClosestPoint.track.getVersionOrThrow(),
                                trackNumberVersion =
                                    stationLinkData.trackNumberVersions.getValue(op1ClosestPoint.track.trackNumberId),
                                station1Version = stationLinkData.operationalPoints.getValue(op1Id).getVersionOrThrow(),
                                station2Version = stationLinkData.operationalPoints.getValue(op2Id).getVersionOrThrow(),
                                length = distance,
                                startAddress = s1Link.address,
                                endAddress = s2Link.address,
                            )
                        )
                    )
                }
            }
                .stream()
        }
        .toList()
        .partition { it is Left<TrackStationConnection> }
        .let { (lefts, rights) ->
            lefts.map { (it as Left<TrackStationConnection>).value } to
                rights.map { (it as Right<StationLinkIssue>).value }
        }
}

private fun MutableList<Either<TrackStationConnection, StationLinkIssue>>.addIssues(
    s1Link: StationPath?,
    s2Link: StationPath?,
    op1Id: IntId<OperationalPoint>,
    op2Id: IntId<OperationalPoint>,
    op1ClosestPoint: PointNearTrack,
    op2ClosestPoint: PointNearTrack,
) {
    if (s1Link == null) {
        add(
            UNREACHABLE_STATION_MIDPOINT.buildStationLinkIssue(
                mainOp = op1Id,
                secondaryOp = op2Id,
                closestTrackPoint = op1ClosestPoint,
                details = emptyMap(),
            )
        )
    }
    if (s2Link == null) {
        add(
            UNREACHABLE_STATION_MIDPOINT.buildStationLinkIssue(
                mainOp = op2Id,
                secondaryOp = op1Id,
                closestTrackPoint = op2ClosestPoint,
                details = emptyMap(),
            )
        )
    }
    if (s1Link != null && s1Link.routeDistance > 2 * s1Link.straightLineDistance) {
        add(
            SUSPICIOUSLY_LONG_ROUTE.buildStationLinkIssue(
                mainOp = op1Id,
                secondaryOp = op2Id,
                closestTrackPoint = op1ClosestPoint,
                details =
                    mapOf(
                        "stationRouteDistance" to s1Link.routeDistance.toString(),
                        "stationStraightLineDistance" to s1Link.straightLineDistance.toString(),
                    ),
            )
        )
    }
    if (s2Link != null && s2Link.routeDistance > 2 * s2Link.straightLineDistance) {
        add(
            SUSPICIOUSLY_LONG_ROUTE.buildStationLinkIssue(
                mainOp = op2Id,
                secondaryOp = op1Id,
                closestTrackPoint = op2ClosestPoint,
                details =
                    mapOf(
                        "stationRouteDistance" to s2Link.routeDistance.toString(),
                        "stationStraightLineDistance" to s2Link.straightLineDistance.toString(),
                    ),
            )
        )
    }
}

private fun StationLinkIssueType.buildStationLinkIssue(
    mainOp: IntId<OperationalPoint>,
    secondaryOp: IntId<OperationalPoint>,
    closestTrackPoint: PointNearTrack,
    details: Map<String, String>,
) =
    Right(
        StationLinkIssue(
            type = this,
            severity =
                when (this) {
                    UNREACHABLE_STATION_MIDPOINT -> StationLinkIssueSeverity.ERROR
                    SUSPICIOUSLY_LONG_ROUTE -> StationLinkIssueSeverity.WARNING
                },
            operationalPointId = mainOp,
            otherOperationalPointId = secondaryOp,
            locationTrackId = closestTrackPoint.track.id as IntId,
            trackNumberId = closestTrackPoint.track.trackNumberId,
            details = details,
        )
    )

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
        fromTrackPoint: PointNearTrack,
        stationId: IntId<OperationalPoint>,
    ): StationPath? =
        getConnectableStation(stationId)?.connectingLocations?.let { stationLocations ->
            stationLocations
                // If any station point is on the same track, we can just skip the routing
                .firstOrNull { (_, location) -> location.track.id == fromTrackPoint.track.id }
                ?.let { (address, location) ->
                    StationPath(
                        address = address,
                        routeDistance = abs(location.closestPoint.m - fromTrackPoint.closestPoint.m).distance,
                        straightLineDistance = (location.closestPoint - fromTrackPoint.closestPoint).magnitude(),
                    )
                }
                // If not, we need to route all points to find the shortest path
                ?: stationLocations
                    .mapNotNull { (address, location) ->
                        routingGraph.findPath(fromTrackPoint, location)?.totalLength?.let { routeDistance ->
                            StationPath(
                                address = address,
                                routeDistance = routeDistance,
                                straightLineDistance =
                                    (location.closestPoint - fromTrackPoint.closestPoint).magnitude(),
                            )
                        }
                    }
                    .minByOrNull { it.routeDistance }
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
    ): List<Pair<TrackMeter, PointNearTrack>> =
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
                            val hit = PointNearTrack(track.track, track.geometry, trackLocation.point, 0.0)
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
    fun getClosestTrackStationLocations(track: ConnectingTrack): List<Pair<IntId<OperationalPoint>, PointNearTrack>> =
        track.operationalPointIds.mapNotNull { opId -> getClosestTrackPoint(track.id, opId)?.let { opId to it } }

    private val closestTrackPoints =
        ConcurrentHashMap<Pair<IntId<LocationTrack>, IntId<OperationalPoint>>, Optional<PointNearTrack>>()

    fun getClosestTrackPoint(trackId: IntId<LocationTrack>, opId: IntId<OperationalPoint>): PointNearTrack? =
        closestTrackPoints
            .computeIfAbsent(trackId to opId) { (tId, opId) ->
                val track = connectingTracks.getValue(tId)
                val op = operationalPoints.getValue(opId)
                val closestPoint =
                    op.location?.let { location ->
                        track.geometry.getClosestPoint(location)?.first?.let { closest ->
                            // The distance (op <-> track-point) is not a part of the route -> set distance to 0.0
                            PointNearTrack(track.track, track.geometry, closest, 0.0)
                        }
                    }
                Optional.ofNullable(closestPoint)
            }
            .getOrNull()
}

private data class ConnectableStation(
    val op: OperationalPoint,
    val connectingLocations: List<Pair<TrackMeter, PointNearTrack>>,
)

private data class StationPath(
    val address: TrackMeter,
    val routeDistance: Double,
    val straightLineDistance: Double,
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
