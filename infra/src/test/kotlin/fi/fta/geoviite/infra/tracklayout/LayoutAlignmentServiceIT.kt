package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.getBoundingPolygonPointsFromAlignments
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.math.Point
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutAlignmentServiceIT
@Autowired
constructor(
    private val layoutAlignmentService: LayoutAlignmentService,
    private val coordinateTransformationService: CoordinateTransformationService,
    private val geocodingService: GeocodingService,
    private val geometryDao: GeometryDao,
) : DBTestBase() {
    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
        testDBService.clearGeometryTables()
    }

    @Test
    fun `overlapping plan search finds plans that are within 10m of alignment`() {
        val tn = TrackNumber("001")
        // TODO: The alignments with "_2" in their names exist because of a bug that causes bounding polygons for plans
        // with alignments in a straight line to be treated as linestrings and throw an SQL error when trying to
        // be saved into the database. This a workaround to make the bounding polygon a valid polygon instead of a
        // linestring. Remove them after GVT-3040 has been done
        val a1_1 = geometryAlignment(line(Point(0.0, 0.0), Point(10.0, 0.0)))
        val a1_2 = geometryAlignment(line(Point(0.0, 2.0), Point(10.0, 2.0)))
        val a2_1 = geometryAlignment(line(Point(20.0, 0.0), Point(30.0, 0.0)))
        val a2_2 = geometryAlignment(line(Point(20.0, 2.0), Point(30.0, 2.0)))
        val a3_1 = geometryAlignment(line(Point(40.0, 0.0), Point(50.0, 0.0)))
        val a3_2 = geometryAlignment(line(Point(40.0, 2.0), Point(50.0, 2.0)))
        val a4_1 = geometryAlignment(line(Point(60.0, 0.0), Point(70.0, 0.0)))
        val a4_2 = geometryAlignment(line(Point(60.0, 2.0), Point(70.0, 2.0)))
        val a5_1 = geometryAlignment(line(Point(80.0, 0.0), Point(90.0, 0.0)))
        val a5_2 = geometryAlignment(line(Point(80.0, 2.0), Point(90.0, 2.0)))
        val a6_1 = geometryAlignment(line(Point(40.0, 20.0), Point(50.0, 20.0)))
        val a6_2 = geometryAlignment(line(Point(40.0, 22.0), Point(50.0, 22.0)))
        val a7_1 = geometryAlignment(line(Point(40.0, -10.0), Point(50.0, -10.0)))
        val a7_2 = geometryAlignment(line(Point(40.0, -12.0), Point(50.0, -12.0)))

        val tf = coordinateTransformationService.getLayoutTransformation(LAYOUT_SRID)

        val plan1EndsBeforeAlignment =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a1_1, a1_2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a1_1, a1_2), tf),
            )
        val plan2EndsWithinAlignmentBuffer =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a2_1, a2_2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a2_1, a2_2), tf),
            )
        val plan3CompletelyWithin =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a3_1, a3_2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a3_1, a3_2), tf),
            )
        val plan4TouchesEndOfAlignmentBuffer =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a4_1, a4_2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a4_1, a4_2), tf),
            )
        val plan5StartsAfterAlignmentEnd =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a5_1, a5_2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a5_1, a5_2), tf),
            )
        val plan6TooFarToTheSide =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a6_1, a6_2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a6_1, a6_2), tf),
            )
        val plan7TouchesBufferFromSide =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a7_1, a7_2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a7_1, a7_2), tf),
            )
        val plan8Hidden =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a4_1, a4_2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a4_1, a4_2), tf),
            )
        geometryDao.setPlanHidden(plan8Hidden.id, true)

        val (track, _) =
            mainOfficialContext.insertAndFetch(
                locationTrackAndAlignment(
                    mainOfficialContext
                        .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0))))
                        .id,
                    segment(Point(32.0, 0.0), Point(50.0, 0.0)),
                    draft = false,
                )
            )

        val cacheKey = geocodingService.getGeocodingContextCacheKey(mainOfficialContext.context, track.trackNumberId)
        val overlapping =
            layoutAlignmentService.getOverlappingPlanHeaders(track.alignmentVersion!!, cacheKey!!, null, null).map {
                it.id
            }

        assertEquals(4, overlapping.size)
        assertContains(overlapping, plan2EndsWithinAlignmentBuffer.id)
        assertContains(overlapping, plan3CompletelyWithin.id)
        assertContains(overlapping, plan4TouchesEndOfAlignmentBuffer.id)
        assertContains(overlapping, plan7TouchesBufferFromSide.id)
    }
}
