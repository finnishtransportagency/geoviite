package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType
import fi.fta.geoviite.infra.tracklayout.graph.Topology
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDirection
import fi.fta.geoviite.infra.tracklayout.graph.TopologyEdge
import fi.fta.geoviite.infra.tracklayout.graph.TopologyEdgeTraversal
import fi.fta.geoviite.infra.tracklayout.graph.TopologyLocationTrackReference
import fi.fta.geoviite.infra.tracklayout.graph.TopologyNode
import fi.fta.geoviite.infra.tracklayout.graph.TopologyService
import fi.fta.geoviite.infra.tracklayout.graph.TopologySwitchReference
import fi.fta.geoviite.infra.tracklayout.graph.TopologyTransition
import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTopologyServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val topologyService: TopologyService,
) {

    fun getExtTopology(
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
        extResolution: ExtTopologyResolutionV1?,
    ): ExtTopologyResponseV1 {
        // The topology is always formed from an immutable, published main branch version: design publications are
        // rejected as unknown versions.
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        val coordinateSystem = coordinateSystem(extCoordinateSystem)
        val resolution = extResolution ?: ExtTopologyResolutionV1.NANO

        val topology = topologyService.getTopology(publication, resolution.toDetailLevel())

        return ExtTopologyResponseV1(
            layoutVersion = ExtLayoutVersionV1(publication),
            coordinateSystem = ExtSridV1(coordinateSystem),
            topology =
                topology.toExtTopology(
                    resolution,
                    coordinateSystem,
                ),
        )
    }
}

private fun Topology.toExtTopology(
    resolution: ExtTopologyResolutionV1,
    coordinateSystem: Srid,
): ExtTopologyV1 {
    return ExtTopologyV1(
        resolution = resolution,
        edges = edges.map(TopologyEdge::toExtTopologyEdge),
        nodes = nodes.map { node -> node.toExtTopologyNode(coordinateSystem) },
    )
}

private fun TopologyEdge.toExtTopologyEdge() =
    ExtTopologyEdgeV1(
        id = ExtTopologyEdgeIdV1(id),
        startNode = ExtTopologyNodeIdV1(startNode.id),
        endNode = ExtTopologyNodeIdV1(endNode.id),
        length = BigDecimal.valueOf(length),
        tracks = trackReferences.map(TopologyLocationTrackReference::toExtTopologyLocationTrackReference),
    )

private fun TopologyLocationTrackReference.toExtTopologyLocationTrackReference() =
    ExtTopologyLocationTrackReferenceV1(
        ExtOidV1(requireNotNull(oid) { "Topology location track has no OID: track=${track.id}" })
    )

private fun TopologyNode.toExtTopologyNode(coordinateSystem: Srid) =
    ExtTopologyNodeV1(
        id = ExtTopologyNodeIdV1(id),
        type =
            when (type) {
                LayoutNodeType.TRACK_BOUNDARY -> ExtTopologyNodeTypeV1.TRACK_END
                LayoutNodeType.SWITCH -> ExtTopologyNodeTypeV1.SWITCH
            },
        switches = switchReferences.map(TopologySwitchReference::toExtTopologySwitchReference),
        location = toExtCoordinate(location, coordinateSystem),
        transitions = transitions.map { transition -> transition.toExtTopologyTransition() },
    )

private fun TopologySwitchReference.toExtTopologySwitchReference() =
    ExtTopologySwitchReferenceV1(
        oid = ExtOidV1(requireNotNull(oid) { "Topology switch has no OID: switch=${switch.id}" }),
        jointNumber = switch.jointNumber.intValue,
    )

private fun TopologyTransition.toExtTopologyTransition() =
    ExtTopologyTransitionV1(
        incomingEdge = incomingEdge.toExtTopologyEdgeReference(),
        outgoingEdge = outgoingEdge.toExtTopologyEdgeReference(),
    )

private fun TopologyEdgeTraversal.toExtTopologyEdgeReference() =
    ExtTopologyEdgeReferenceV1(
        id = ExtTopologyEdgeIdV1(edge.id),
        direction =
            when (direction) {
                TopologyDirection.ASCENDING -> ExtTopologyDirectionV1.ASCENDING
                TopologyDirection.DESCENDING -> ExtTopologyDirectionV1.DESCENDING
            },
    )
