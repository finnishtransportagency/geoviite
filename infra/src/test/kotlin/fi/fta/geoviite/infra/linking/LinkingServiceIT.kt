package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitTarget
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNull

@ActiveProfiles("dev", "test")
@SpringBootTest
class LinkingServiceIT @Autowired constructor(
    private val geometryService: GeometryService,
    private val planLayoutService: PlanLayoutService,
    private val geometryDao: GeometryDao,
    private val linkingService: LinkingService,
    private val locationTrackService: LocationTrackService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val kmPostDao: LayoutKmPostDao,
    private val kmPostService: LayoutKmPostService,
    private val splitDao: SplitDao,
) : DBTestBase() {

    @Test
    fun alignmentGeometryLinkingWorks() {
        val trackNumberId = getUnusedTrackNumberId()

        val geometryStart = Point(377680.0, 6676160.0)
        // 6m of geometry to replace
        val geometrySegmentChange = geometryStart + Point(3.0, 3.5)
        val geometryEnd = geometrySegmentChange + Point(3.0, 2.5)
        val plan = plan(
            trackNumberId, Srid(3067), geometryAlignment(
                line(geometryStart, geometrySegmentChange),
                line(geometrySegmentChange, geometryEnd),
            )
        )

        val geometryPlanId = geometryDao.insertPlan(plan, testFile(), null)
        val (geometryLayoutPlan, transformationError) = planLayoutService.getLayoutPlan(geometryPlanId.id)
        assertNull(transformationError)
        assertNotNull(geometryLayoutPlan)

        val geometryLayoutAlignment = geometryLayoutPlan?.alignments?.get(0)!!
        val geometryStartSegment = geometryLayoutAlignment.segments.first()
        val geometryEndSegment = geometryLayoutAlignment.segments.last()
        assertNotEquals(geometryStartSegment, geometryEndSegment)

        // Geometry is about 6m and we need 1m for transition on both sides
        // Build layout so that we keep first point, replacing the next 8: 4 from first, 4 from second segment
        val start = geometryStart - Point(1.0, 1.5)
        val segment1 = segment(
            start, start + 1.0, start + 2.0, start + 3.0, start + 4.0,
            startM = 0.0,
        )
        val segment2 = segment(
            start + 4.0, start + 5.0, start + 6.0, start + 7.0, start + 8.0, start + 9.0,
            startM = segment1.length,
        )
        val segment3 = segment(
            start + 9.0, start + 10.0, start + 11.0,
            startM = segment2.length,
        )

        val (locationTrack, alignment) = locationTrackAndAlignment(
            insertOfficialTrackNumber(), segment1, segment2, segment3, draft = true
        )
        val (locationTrackId, locationTrackVersion) = locationTrackService.saveDraft(locationTrack, alignment)
        locationTrackService.publish(ValidationVersion(locationTrackId, locationTrackVersion))

        val (officialTrack, officialAlignment) = locationTrackService.getWithAlignmentOrThrow(OFFICIAL, locationTrackId)
        assertMatches(
            officialTrack to officialAlignment, locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)
        )

        // Pick the whole geometry as interval
        val geometryInterval = GeometryInterval(
            alignmentId = geometryLayoutAlignment.id as IntId,
            mRange = Range(
                geometryStartSegment.alignmentPoints.first().m,
                geometryEndSegment.alignmentPoints.last().m,
            ),
        )

        // Pick layout interval to cut after first 2 point, skipping to 5th point of second interval
        val layoutInterval = LayoutInterval(
            alignmentId = locationTrackId,
            mRange = Range(
                officialAlignment.segments[0].alignmentPoints.first().m,
                officialAlignment.segments[1].alignmentPoints[4].m,
            ),
        )

        linkingService.saveLocationTrackLinking(LinkingParameters(geometryPlanId.id, geometryInterval, layoutInterval))
        assertEquals(
            officialTrack to officialAlignment, locationTrackService.getWithAlignment(OFFICIAL, locationTrackId)
        )

        val (_, draftAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)
        assertNotEquals(officialAlignment, draftAlignment)
        // Should be split so that we have;
        // all geometry segments
        // 1 generated segment from geometry end to 2:nd segment middle
        // second segment partially (4 last points)
        // third segment fully
        assertEquals(geometryLayoutAlignment.segments.size + 3, draftAlignment.segments.size)
        assertEquals(6, draftAlignment.segments[0].alignmentPoints.size)
        for (i in 0..geometryLayoutAlignment.segments.lastIndex) {
            assertEquals(
                geometryLayoutAlignment.segments[i].alignmentPoints.size,
                draftAlignment.segments[i].alignmentPoints.size,
            )
        }
        assertEquals(2, draftAlignment.segments[1 + geometryLayoutAlignment.segments.size].alignmentPoints.size)
        assertEquals(3, draftAlignment.segments[2 + geometryLayoutAlignment.segments.size].alignmentPoints.size)
    }

    @Test
    fun kmPostGeometryLinkingWorks() {
        val kmPost = kmPost(insertOfficialTrackNumber(), someKmNumber(), draft = false)
        val kmPostId = kmPostDao.insert(kmPost).id
        val officialKmPost = kmPostService.get(OFFICIAL, kmPostId)

        assertMatches(officialKmPost!!, kmPostService.getOrThrow(DRAFT, kmPostId), contextMatch = false)

        val trackNumberId = trackNumberDao.insert(
            trackNumber(TrackNumber(System.currentTimeMillis().toString()), draft = false)
        )
        val plan = plan(trackNumberId.id)
        val geometryPlanId = geometryDao.insertPlan(plan, testFile(), null).id

        val fetchedPlan = geometryService.getGeometryPlan(geometryPlanId)

        val geometryKmPostId = fetchedPlan.kmPosts[1].id as IntId

        val kmPostLinkingParameters = KmPostLinkingParameters(geometryPlanId, geometryKmPostId, kmPostId)

        linkingService.saveKmPostLinking(kmPostLinkingParameters)

        assertEquals(officialKmPost, kmPostService.get(OFFICIAL, kmPostId))

        val draftKmPost = kmPostService.getOrThrow(DRAFT, kmPostId)

        assertNotEquals(officialKmPost, draftKmPost)

        assertEquals(geometryKmPostId, draftKmPost.sourceId)
    }

    @Test
    fun `Linking alignments throws if alignment is deleted`() {
        val trackNumberId = getUnusedTrackNumberId()
        val plan = plan(trackNumberId, LAYOUT_SRID)

        val geometryPlanId = geometryDao.insertPlan(plan, testFile(), null)

        val (locationTrack, alignment) = locationTrackAndAlignment(
            trackNumberId = insertOfficialTrackNumber(),
            state = LayoutState.DELETED,
            draft = true,
        )
        val (locationTrackId, locationTrackVersion) = locationTrackService.saveDraft(locationTrack, alignment)
        locationTrackService.publish(ValidationVersion(locationTrackId, locationTrackVersion))

        val geometryInterval = GeometryInterval(
            alignmentId = IntId(0),
            mRange = Range(0.0, 0.0),
        )

        val layoutInterval = LayoutInterval(
            alignmentId = locationTrackId,
            mRange = Range(0.0, 0.0),
        )

        val ex = assertThrows<LinkingFailureException> {
            linkingService.saveLocationTrackLinking(
                LinkingParameters(
                    geometryPlanId.id,
                    geometryInterval,
                    layoutInterval,
                )
            )
        }
        assertEquals("Linking failed: Cannot link a location track that is deleted", ex.message)
    }

    @Test
    fun `Linking alignments throws if alignment has splits`() {
        val trackNumberId = getUnusedTrackNumberId()
        val plan = plan(trackNumberId, LAYOUT_SRID)

        val geometryPlanId = geometryDao.insertPlan(plan, testFile(), null)

        val (locationTrack, alignment) = locationTrackAndAlignment(insertOfficialTrackNumber(), draft = true)
        val (locationTrackId, locationTrackVersion) = locationTrackService.saveDraft(locationTrack, alignment)
        locationTrackService.publish(ValidationVersion(locationTrackId, locationTrackVersion))

        val geometryInterval = GeometryInterval(
            alignmentId = IntId(0),
            mRange = Range(0.0, 0.0),
        )

        val layoutInterval = LayoutInterval(
            alignmentId = locationTrackId,
            mRange = Range(0.0, 0.0),
        )

        splitDao.saveSplit(
            locationTrackId,
            listOf(SplitTarget(locationTrackId, 0..1)),
            relinkedSwitches = emptyList(),
            updatedDuplicates = emptyList(),
        )

        val ex = assertThrows<LinkingFailureException> {
            linkingService.saveLocationTrackLinking(
                LinkingParameters(geometryPlanId.id, geometryInterval, layoutInterval)
            )
        }
        assertEquals("Linking failed: Cannot link a location track that has unfinished splits", ex.message)
    }

    @Test
    fun `Linking alignments works if all alignment splits are finished`() {
        val trackNumberId = getUnusedTrackNumberId()

        val geometryStart = Point(377680.0, 6676160.0)
        // 6m of geometry to replace
        val geometrySegmentChange = geometryStart + Point(3.0, 3.5)
        val geometryEnd = geometrySegmentChange + Point(3.0, 2.5)
        val plan = plan(
            trackNumberId, LAYOUT_SRID, geometryAlignment(
                line(geometryStart, geometrySegmentChange),
                line(geometrySegmentChange, geometryEnd),
            )
        )

        val geometryPlanId = geometryDao.insertPlan(plan, testFile(), null)
        val (geometryLayoutPlan, _) = planLayoutService.getLayoutPlan(geometryPlanId.id)

        val geometryLayoutAlignment = geometryLayoutPlan?.alignments?.get(0)!!
        val geometryStartSegment = geometryLayoutAlignment.segments.first()
        val geometryEndSegment = geometryLayoutAlignment.segments.last()

        val start = geometryStart - Point(1.0, 1.5)
        val segment1 = segment(
            start, start + 1.0, start + 2.0, start + 3.0, start + 4.0,
            startM = 0.0,
        )

        val (locationTrack, alignment) = locationTrackAndAlignment(
            insertOfficialTrackNumber(), segment1, draft = true,
        )
        val (locationTrackId, locationTrackVersion) = locationTrackService.saveDraft(locationTrack, alignment)
        locationTrackService.publish(ValidationVersion(locationTrackId, locationTrackVersion))

        val (_, officialAlignment) = locationTrackService.getWithAlignmentOrThrow(OFFICIAL, locationTrackId)

        val geometryInterval = GeometryInterval(
            alignmentId = geometryLayoutAlignment.id as IntId,
            mRange = Range(
                geometryStartSegment.alignmentPoints.first().m,
                geometryEndSegment.alignmentPoints.last().m,
            ),
        )

        val layoutInterval = LayoutInterval(
            alignmentId = locationTrackId,
            mRange = Range(
                officialAlignment.segments[0].alignmentPoints.first().m,
                officialAlignment.segments[0].alignmentPoints[4].m,
            ),
        )

        val split = splitDao.getOrThrow(
            splitDao.saveSplit(
                locationTrackId,
                listOf(SplitTarget(locationTrackId, 0..1)),
                relinkedSwitches = emptyList(),
                updatedDuplicates = emptyList(),
            )
        )
        splitDao.updateSplitState(split.id, bulkTransferState = BulkTransferState.DONE)

        assertDoesNotThrow {
            linkingService.saveLocationTrackLinking(
                LinkingParameters(
                    geometryPlanId.id,
                    geometryInterval,
                    layoutInterval,
                )
            )
        }
    }

    fun assertMatches(
        trackAndAlignment1: Pair<LocationTrack, LayoutAlignment>,
        trackAndAlignment2: Pair<LocationTrack, LayoutAlignment>,
    ) {
        assertMatches(trackAndAlignment1.first, trackAndAlignment2.first, contextMatch = false)
        assertMatches(trackAndAlignment1.second, trackAndAlignment2.second)
    }
}
