package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeometryServiceIT @Autowired constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val locationTrackService: LocationTrackService,
    private val geometryDao: GeometryDao,
    private val geometryService: GeometryService,
) : ITTestBase() {

    @Test
    fun getLocationTrackHeightsReturnsBothOrdinaryTicksAndPlanBoundaries() {
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        referenceLineService.saveDraft(
            referenceLine(trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(0.0, 100.0)))
        )
        val p1 = insertPlanWithGeometry("plan1.xml", trackNumberId)
        val p2 = insertPlanWithGeometry("plan2.xml", trackNumberId)
        val p3 = insertPlanWithGeometry("plan3.xml", trackNumberId)
        val locationTrackId = locationTrackService.saveDraft(
            locationTrack(trackNumberId),
            alignment(
                segment(yRangeToSegmentPoints(0..6), sourceId = p1.alignments[0].elements[0].id, sourceStart = 0.0),
                segment(yRangeToSegmentPoints(6..10)),
                segment(yRangeToSegmentPoints(10..12), sourceId = p2.alignments[0].elements[0].id, sourceStart = 0.0),
                segment(yRangeToSegmentPoints(12..13)),
                segment(yRangeToSegmentPoints(13..15), sourceId = p3.alignments[0].elements[0].id, sourceStart = 0.0),
                segment(yRangeToSegmentPoints(15..17), sourceId = p1.alignments[0].elements[0].id, sourceStart = 0.0),
            )
        ).id
        val kmHeights = geometryService.getLocationTrackHeights(locationTrackId, PublishType.DRAFT, 0.0, 20.0, 5)!!
        // this track is exactly straight on the reference line, so m-values and track meters coincide perfectly; also,
        // the profile is at exactly 50 meters height at every point where it's linked
        val expected = listOf(
            0 to 50,
            // 5 to 50, // should be filtered due to being less than a half-tick distance from nearest
            6 to 50,
            6 to null,
            10 to null,
            10 to 50,
            12 to 50,
            12 to null,
            13 to null,
            13 to 50,
            15 to 50,
            17 to 50
        ).map { (m, h) -> TrackMeterHeight(m.toDouble(), m.toDouble(), h?.toDouble(), Point(0.0, m.toDouble())) }
        assertEquals(expected, kmHeights[0].trackMeterHeights)
        val linkingSummary = geometryService.getLocationTrackGeometryLinkingSummary(locationTrackId, PublishType.DRAFT)!!
        assertEquals(
            listOf(
                Triple(0.0, 6.0, FileName("plan1.xml")),
                Triple(6.0, 10.0, null),
                Triple(10.0, 12.0, FileName("plan2.xml")),
                Triple(12.0, 13.0, null),
                Triple(13.0, 15.0, FileName("plan3.xml")),
                Triple(15.0, 17.0, FileName("plan1.xml")),
            ),
            linkingSummary.map { item -> Triple(item.startM, item.endM, item.filename) }
        )
    }

    fun yRangeToSegmentPoints(range: IntRange) =
        toTrackLayoutPoints(to3DMPoints(range.map { i -> Point(0.0, i.toDouble()) }))

    fun insertPlanWithGeometry(filename: String, trackNumberId: IntId<TrackLayoutTrackNumber>): GeometryPlan {
        val version = geometryDao.insertPlan(
            planWithGeometryAndHeights(trackNumberId), InfraModelFile(FileName(filename), "<a></a>"), null
        )
        return geometryDao.fetchPlan(version)
    }

    fun planWithGeometryAndHeights(trackNumberId: IntId<TrackLayoutTrackNumber>): GeometryPlan = plan(
        trackNumberId,
        LAYOUT_SRID,
        geometryAlignment(
            trackNumberId,
            elements = listOf(lineFromOrigin(1.0)),
            name = "foo",
            profile = GeometryProfile(
                PlanElementName("aoeu"),
                listOf(
                    // profile originally inspired by geometry alignment 2466 in order to make the numbers plausible;
                    // but simplified and rounded
                    VIPoint(PlanElementName("startpoint"), Point(0.0, 50.0)),
                    VICircularCurve(
                        PlanElementName("rounding"),
                        Point(500.0, 50.0),
                        BigDecimal(20000),
                        BigDecimal(155),
                    ),
                    VIPoint(PlanElementName("endpoint"), Point(600.0, 51.0)),
                )
            )
        ),
    )

}
