package fi.fta.geoviite.infra.tracklayout.graph

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDetailLevel.NANO
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class TopologyServiceIT @Autowired constructor(private val topologyService: TopologyService) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun `Context topology includes only its own and inherited official tracks`() {
        val trackNumberId =
            mainDraftContext
                .save(
                    trackNumber(testDBService.getUnusedTrackNumber()),
                    referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
                )
                .id
        val mainOfficialTrackId = saveTrack(mainDraftContext, trackNumberId, x = 0.0)
        testDBService.publish(trackNumbers = listOf(trackNumberId), locationTracks = listOf(mainOfficialTrackId))

        val mainDraftTrackId = saveTrack(mainDraftContext, trackNumberId, x = 100.0)

        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)
        val designOfficialTrackId = saveTrack(designDraftContext, trackNumberId, x = 200.0)
        testDBService.publish(designBranch, locationTracks = listOf(designOfficialTrackId))

        val designDraftTrackId = saveTrack(designDraftContext, trackNumberId, x = 300.0)

        assertEquals(
            setOf(mainOfficialTrackId),
            topologyService.getTopology(mainOfficialContext.context, NANO).getTrackIds(),
        )
        assertEquals(
            setOf(mainOfficialTrackId, mainDraftTrackId),
            topologyService.getTopology(mainDraftContext.context, NANO).getTrackIds(),
        )
        assertEquals(
            setOf(mainOfficialTrackId, designOfficialTrackId),
            topologyService.getTopology(testDBService.testContext(designBranch, OFFICIAL).context, NANO).getTrackIds(),
        )
        assertEquals(
            setOf(mainOfficialTrackId, designOfficialTrackId, designDraftTrackId),
            topologyService.getTopology(designDraftContext.context, NANO).getTrackIds(),
        )
    }

    private fun saveTrack(
        context: TestLayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        x: Double,
    ): IntId<LocationTrack> =
        context
            .save(
                locationTrack(trackNumberId),
                trackGeometryOfSegments(segment(Point(x, 0.0), Point(x, 100.0))),
            )
            .id

    private fun Topology.getTrackIds(): Set<DomainId<LocationTrack>> =
        edges.flatMap { edge -> edge.trackReferences.map { trackReference -> trackReference.track.id } }.toSet()
}
