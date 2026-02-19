package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import org.jgrapht.Graph
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.SimpleDirectedWeightedGraph

data class RoutingGraph(val graph: LayoutGraph) {
    private val jgraph: Graph<IntId<LayoutNode>, LayoutGraphEdge> = buildJGraph(graph)
    private val dijkstra = DijkstraShortestPath(jgraph)

    fun findPath(startNode: IntId<LayoutNode>, endNode: IntId<LayoutNode>): List<LayoutGraphEdge>? =
        dijkstra.getPath(startNode, endNode)?.edgeList?.filterNotNull()
}

private fun buildJGraph(graph: LayoutGraph): Graph<IntId<LayoutNode>, LayoutGraphEdge> =
    // Graph types: https://jgrapht.org/guide/UserOverview#graph-structures
    SimpleDirectedWeightedGraph<IntId<LayoutNode>, LayoutGraphEdge>(LayoutGraphEdge::class.java).also { jgraph ->
        graph.nodes.keys.forEach { nodeId -> jgraph.addVertex(nodeId) }
        graph.edges.values
            .asSequence()
            .flatMap { edge -> sequenceOf(edge, edge.flip()) }
            .forEach { edge ->
                jgraph.addEdge(edge.startNode, edge.endNode, edge)
                jgraph.setEdgeWeight(edge, edge.length)
            }
    }

// For immutable edit patterns, see: https://jgrapht.org/guide/UserOverview#graph-wrappers

@GeoviiteService
class RoutingService(
    private val layoutGraphService: LayoutGraphService,
    private val locationTrackSpatialCache: LocationTrackSpatialCache,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val switchLibraryService: SwitchLibraryService,
) {

    private val routingCache: Cache<Int, RoutingGraph> =
        Caffeine.newBuilder().maximumSize(100).expireAfterAccess(layoutCacheDuration).build()

    private fun getRouting(graph: LayoutGraph) = routingCache.get(graph.key) { RoutingGraph(graph) }

    fun getClosestTrackPoint(context: LayoutContext, location: Point, maxDistance: Double): ClosestTrackPoint? =
        locationTrackSpatialCache.get(context).getClosest(location, maxDistance).firstOrNull()?.let { hit ->
            toClosestTrackPoint(location, hit)
        }

    fun findPath(graph: LayoutGraph, startNode: IntId<LayoutNode>, endNode: IntId<LayoutNode>): List<LayoutGraphEdge>? {
        return getRouting(graph).findPath(startNode, endNode)
    }

    fun getRoute(
        context: LayoutContext,
        startLocation: Point,
        endLocation: Point,
        trackSeekDistance: Double,
    ): RouteResult? {
        val trackCache = locationTrackSpatialCache.get(context)
        val startTrackHit = trackCache.getClosest(startLocation, trackSeekDistance).firstOrNull()
        val endTrackHit = trackCache.getClosest(endLocation, trackSeekDistance).firstOrNull()
        return if (startTrackHit != null && endTrackHit != null) {
//            val graph = layoutGraphService.getGraph(context, DetailLevel.NANO)
//            val route =
//                findPath(graph, startTrackHit.getClosestNode().id, endTrackHit.getClosestNode().id)?.let { path ->
//                    // The path is from closest node: it may or may not lead to traversing the hit-edge
//                    val startEdgeId = startTrackHit.getEdge().first.id
//                    val endEdgeId = endTrackHit.getEdge().first.id
//                    val edgeIds =
//                        listOf(startEdgeId.takeIf { it != path.firstOrNull()?.id }) +
//                            path.map { it.id } +
//                            listOfNotNull(endEdgeId.takeIf { it != path.lastOrNull()?.id })
//                    val edges = edgeIds.map { id -> requireNotNull(graph.edges[id]) { "Unknown edge ID: id=$id" } }
//                    //                    val nodeIds = edges.map { it.startNode } +
//                    // listOfNotNull(edges.lastOrNull()?.endNode)
//                    //                    val nodes = nodeIds.map { id -> requireNotNull(graph.nodes[id]) { "Unknown
//                    // node ID: id=$id" } }
//                    Route(
//                        edges.map { e ->
//                            when (e.id) {
//                                startEdgeId ->
//                                    e.tracks
//                                        .first { it.id == startTrackHit.track.id }
//                                        .cutFrom(startTrackHit.closestPoint.m)
//                                endEdgeId ->
//                                    e.tracks
//                                        .first { it.id == endTrackHit.track.id }
//                                        .cutUntil(startTrackHit.closestPoint.m)
//                                else -> e.tracks.first()
//                            }
//                        }
//                    )
//                }
            val graph =buildGraph(
                trackGeoms = locationTrackService.listWithGeometries(context, includeDeleted = false).map { (t,g) -> g },
                switches = switchService.list(context, includeDeleted = false),
                structures = switchLibraryService.getSwitchStructuresById(),
            )
            RouteResult(
                startConnection = toClosestTrackPoint(startLocation, startTrackHit),
                endConnection = toClosestTrackPoint(endLocation, endTrackHit),
                route = graph.findPath(startTrackHit, endTrackHit)?.let(::Route),
//                null,
//                route = route,
            )
        } else {
            null
        }
    }

    /**
     * IMPLEMENTING TURNOUT RULES WITH JGRAPHT:
     *
     * The cleanest approach is the Line Graph transformation where each edge becomes a vertex. This naturally encodes
     * turn restrictions in the graph structure.
     *
     * Example implementation:
     *
     * fun findPathWithTurnoutRules( graph: LayoutGraph, startNode: IntId<LayoutNode>, endNode: IntId<LayoutNode>,
     * isTransitionAllowed: (fromEdge: DomainId<LayoutEdge>?, toEdge: DomainId<LayoutEdge>) -> Boolean ):
     * List<DomainId<LayoutEdge>>? { // Build line graph where edges become vertices val lineGraph:
     * Graph<DomainId<LayoutEdge>, DefaultEdge> = DefaultDirectedWeightedGraph(DefaultEdge::class.java)
     *
     *     // Add all edges as vertices in the line graph
     *     graph.edges.keys.forEach { edgeId -> lineGraph.addVertex(edgeId) }
     *     // Connect edges that share a node AND are allowed by turnout rules
     *     graph.nodes.values.forEach { node ->
     *         val incomingEdges = graph.edges.values.filter { it.endNode == node.id }
     *         val outgoingEdges = graph.edges.values.filter { it.startNode == node.id }
     *         for (inEdge in incomingEdges) {
     *             for (outEdge in outgoingEdges) {
     *                 if (isTransitionAllowed(inEdge.id, outEdge.id)) {
     *                     val lineEdge = lineGraph.addEdge(inEdge.id, outEdge.id)
     *                     if (lineEdge != null) {
     *                         lineGraph.setEdgeWeight(lineEdge, outEdge.length)
     *                     }
     *                 }
     *             }
     *         }
     *         // Also add start transitions (from null to first edge)
     *         for (outEdge in outgoingEdges) {
     *             if (isTransitionAllowed(null, outEdge.id)) {
     *                 // Mark these as valid start edges somehow
     *             }
     *         }
     *     }
     *     // Find edges connected to start/end nodes
     *     val startEdges = graph.edges.values.filter { it.startNode == startNode }.map { it.id }
     *     val endEdges = graph.edges.values.filter { it.endNode == endNode }.map { it.id }
     *     // Find shortest path through all start->end combinations
     *     val dijkstra = DijkstraShortestPath(lineGraph)
     *     return startEdges.flatMap { start ->
     *         endEdges.mapNotNull { end ->
     *             dijkstra.getPath(start, end)?.let { path ->
     *                 listOf(start) + path.edgeList.mapNotNull { lineGraph.getEdgeTarget(it) }
     *             }
     *         }
     *     }.minByOrNull { path -> path.sumOf { edgeId -> graph.edges[edgeId]?.length ?: 0.0 } }
     *
     * }
     */
}

private fun toClosestTrackPoint(requestedPoint: Point, hit: LocationTrackCacheHit): ClosestTrackPoint =
    ClosestTrackPoint(
        locationTrackId = hit.track.id as IntId<LocationTrack>,
        requestedLocation = requestedPoint,
        trackLocation = hit.closestPoint,
        distance = hit.distance,
    )
