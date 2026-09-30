package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.graph.Topology
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDirection
import fi.fta.geoviite.infra.tracklayout.graph.TopologyEdge
import fi.fta.geoviite.infra.tracklayout.graph.TopologyEdgeTraversal
import fi.fta.geoviite.infra.tracklayout.graph.TopologyNode
import fi.fta.geoviite.infra.tracklayout.graph.TopologyService
import fi.fta.geoviite.infra.tracklayout.graph.TopologyTransition
import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTopologyServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val topologyService: TopologyService,
    private val locationTrackService: LocationTrackService,
    private val layoutSwitchService: LayoutSwitchService,
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
                    publication,
                    resolution,
                    coordinateSystem,
                    locationTrackService,
                    layoutSwitchService,
                ),
        )
    }
}

private fun Topology.toExtTopology(
    publication: Publication,
    resolution: ExtTopologyResolutionV1,
    coordinateSystem: Srid,
    locationTrackService: LocationTrackService,
    layoutSwitchService: LayoutSwitchService,
): ExtTopologyV1 {
    val branch = publication.layoutBranch.branch
    val trackOids =
        edges
            .flatMap(TopologyEdge::tracks)
            .distinctBy { track -> track.id }
            .associate { track ->
                val trackId = track.id as IntId<LocationTrack>
                trackId to ExtOidV1(locationTrackService.getExternalIdsByBranch(trackId).getValue(branch))
            }
    val switchOids =
        nodes
            .flatMap { node -> node.switches }
            .distinctBy { switch -> switch.id }
            .associate { switch ->
                switch.id to ExtOidV1(layoutSwitchService.getExternalIdsByBranch(switch.id).getValue(branch))
            }
    return ExtTopologyV1(
        resolution = resolution,
        edges = edges.map { edge -> edge.toExtTopologyEdge(trackOids) },
        nodes = nodes.map { node -> node.toExtTopologyNode(switchOids, coordinateSystem) },
    )
}

private fun TopologyEdge.toExtTopologyEdge(trackOids: Map<IntId<LocationTrack>, ExtOidV1<LocationTrack>>) =
    ExtTopologyEdgeV1(
        id = ExtTopologyEdgeIdV1(id),
        startNode = ExtTopologyNodeIdV1(startNode.id),
        endNode = ExtTopologyNodeIdV1(endNode.id),
        length = BigDecimal.valueOf(length),
        tracks =
            tracks.map { track ->
                ExtTopologyLocationTrackReferenceV1(trackOids.getValue(track.id as IntId<LocationTrack>))
            },
    )

private fun TopologyNode.toExtTopologyNode(
    switchOids: Map<IntId<LayoutSwitch>, ExtOidV1<LayoutSwitch>>,
    coordinateSystem: Srid,
) =
    ExtTopologyNodeV1(
        id = ExtTopologyNodeIdV1(id),
        type =
            when (type) {
                LayoutNodeType.TRACK_BOUNDARY -> ExtTopologyNodeTypeV1.TRACK_END
                LayoutNodeType.SWITCH -> ExtTopologyNodeTypeV1.SWITCH
            },
        switches =
            switches.map { switch ->
                ExtTopologySwitchReferenceV1(
                    oid = switchOids.getValue(switch.id),
                    jointNumber = switch.jointNumber.intValue,
                )
            },
        location = toExtCoordinate(location, coordinateSystem),
        transitions = transitions.map { transition -> transition.toExtTopologyTransition() },
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
