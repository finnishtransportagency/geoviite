package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.graph.Topology
import fi.fta.geoviite.infra.tracklayout.graph.TopologyService
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTopologyServiceV1
@Autowired
constructor(private val publicationService: PublicationService, private val topologyService: TopologyService) {

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
            topology = topology.toExtTopology(resolution, coordinateSystem),
        )
    }
}

// GVT-3743 adds the actual nodes and edges: the internal topology model does not contain them yet.
@Suppress("UnusedParameter") // coordinateSystem is needed as soon as node locations are mapped
private fun Topology.toExtTopology(
    resolution: ExtTopologyResolutionV1,
    coordinateSystem: Srid,
): ExtTopologyV1 = ExtTopologyV1(resolution = resolution, edges = emptyList(), nodes = emptyList())
