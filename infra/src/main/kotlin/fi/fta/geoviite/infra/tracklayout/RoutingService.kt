package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import org.jgrapht.Graph
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.DefaultDirectedWeightedGraph

data class RoutingGraph(val graph: LayoutGraph) {
    private val jgraph: Graph<IntId<LayoutNode>, LayoutGraphEdge> = buildJGraph(graph)
    private val dijkstra = DijkstraShortestPath(jgraph)

    fun findPath(startNode: IntId<LayoutNode>, endNode: IntId<LayoutNode>): List<LayoutGraphEdge> =
        dijkstra.getPath(startNode, endNode)?.edgeList?.filterNotNull() ?: emptyList()
}

private fun buildJGraph(graph: LayoutGraph): Graph<IntId<LayoutNode>, LayoutGraphEdge> =
    DefaultDirectedWeightedGraph<IntId<LayoutNode>, LayoutGraphEdge>(LayoutGraphEdge::class.java).also { jgraph ->
        graph.nodes.keys.forEach { nodeId -> jgraph.addVertex(nodeId) }
        graph.edges.values
            .asSequence()
            .flatMap { edge -> sequenceOf(edge, edge.flip()) }
            .forEach { edge ->
                jgraph.addEdge(edge.startNode, edge.endNode, edge)
                jgraph.setEdgeWeight(edge, edge.length)
            }
    }

@GeoviiteService
class RoutingService {

    private val routingCache: Cache<Int, RoutingGraph> =
        Caffeine.newBuilder().maximumSize(100).expireAfterAccess(layoutCacheDuration).build()

    private fun getRouting(graph: LayoutGraph) = routingCache.get(graph.key) { RoutingGraph(graph) }

    fun findPath(graph: LayoutGraph, startNode: IntId<LayoutNode>, endNode: IntId<LayoutNode>): List<LayoutGraphEdge> {
        return getRouting(graph).findPath(startNode, endNode)
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
