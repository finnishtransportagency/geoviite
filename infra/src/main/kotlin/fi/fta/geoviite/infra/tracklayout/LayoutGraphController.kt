package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/track-layout/layout-graph")
class LayoutGraphController(
    val graphService: LayoutGraphService,
    val locationTrackService: LocationTrackService,
    val switchService: LayoutSwitchService,
) {

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}")
    fun getGraph(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("detailLevel", required = false) detailLevel: DetailLevel?,
    ): LayoutGraph {
        return graphService.getGraph(
            context = LayoutContext.of(layoutBranch, publicationState),
            detailLevel = detailLevel ?: DetailLevel.NANO,
            bbox = bbox,
        )
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/2/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}")
    fun getGraph2(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("detailLevel", required = false) detailLevel: DetailLevel?,
    ): TmpGraphData {
        val graph =
            graphService.getGraph(
                context = LayoutContext.of(layoutBranch, publicationState),
                detailLevel = detailLevel ?: DetailLevel.NANO,
                bbox = bbox,
            )
        val switchOids = switchService.getExtIds().mapValues { it.value.oid }
        val trackOids = locationTrackService.getExtIds().mapValues { it.value.oid }
        return TmpGraphData(graph, switchOids, trackOids)
    }
}

data class TmpGraphData(val detailLevel: DetailLevel, val nodes: List<TmpNodeData>, val edges: List<TmpEdgeData>) {
    constructor(
        graph: LayoutGraph,
        switchOids: Map<IntId<LayoutSwitch>, Oid<LayoutSwitch>>,
        trackOids: Map<IntId<LocationTrack>, Oid<LocationTrack>>,
    ) : this(
        detailLevel = graph.detailLevel,
        nodes = graph.nodes.values.map { n -> TmpNodeData(n, switchOids) },
        edges = graph.edges.values.map { e -> TmpEdgeData(e, trackOids) },
    )
}

data class TmpEdgeData(
    val id: DomainId<LayoutEdge>,
    val startNode: IntId<LayoutNode>,
    val endNode: IntId<LayoutNode>,
    val length: Double,
    val tracks: Set<Oid<LocationTrack>>,
) {
    constructor(
        edge: LayoutGraphEdge,
        trackOids: Map<IntId<LocationTrack>, Oid<LocationTrack>>,
    ) : this(
        id = edge.id,
        startNode = edge.startNode,
        endNode = edge.endNode,
        length = edge.length,
        tracks = edge.tracks.map { trackOids[it]!! }.toSet(),
    )
}

data class TmpNodeData(
    val id: IntId<LayoutNode>,
    val type: LayoutNodeType,
    val detailLevel: DetailLevel,
    val switches: List<TmpSwitchLinkData>,
    val location: Point,
) {
    constructor(
        node: LayoutGraphNode,
        switchOids: Map<IntId<LayoutSwitch>, Oid<LayoutSwitch>>,
    ) : this(
        id = node.id,
        type = node.type,
        detailLevel = node.detailLevel,
        switches = node.switches.map { TmpSwitchLinkData(it, switchOids) },
        location = node.location,
    )
}

data class TmpSwitchLinkData(val id: Oid<LayoutSwitch>, val jointNumber: JointNumber) {
    constructor(
        link: SwitchLink,
        switchOids: Map<IntId<LayoutSwitch>, Oid<LayoutSwitch>>,
    ) : this(id = switchOids[link.id]!!, jointNumber = link.jointNumber)
}
