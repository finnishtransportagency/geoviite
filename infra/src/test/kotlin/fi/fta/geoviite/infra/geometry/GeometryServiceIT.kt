package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.to3DMPoints
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeometryServiceIT @Autowired constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val locationTrackService: LocationTrackService,
    private val kmPostService: LayoutKmPostService,
    private val geometryDao: GeometryDao,
    private val geometryService: GeometryService,
) : DBTestBase() {

    @Test
    fun `Hiding GeometryPlans works`() {
        deletePlans()

        val file = testFile()
        val plan = plan(getUnusedTrackNumber(), fileName = file.name)
        val polygon = someBoundingPolygon()
        val planId = geometryDao.insertPlan(plan, file, polygon).id
        val searchBbox = boundingBoxAroundPoints(polygon)

        assertEquals(planId, geometryService.fetchDuplicateGeometryPlanHeader(file, plan.source)?.id)
        assertEquals(listOf(planId), geometryService.getGeometryPlanAreas(searchBbox).map(GeometryPlanArea::id))

        geometryService.setPlanHidden(planId, true)
        assertFalse(geometryService.getPlanHeaders().any { p -> p.id == planId })
        assertEquals(null, geometryService.fetchDuplicateGeometryPlanHeader(file, plan.source)?.id)
        assertEquals(listOf(), geometryService.getGeometryPlanAreas(searchBbox).map(GeometryPlanArea::id))

        geometryService.setPlanHidden(planId, false)
        assertTrue(geometryService.getPlanHeaders().any { p -> p.id == planId })
        assertEquals(planId, geometryService.fetchDuplicateGeometryPlanHeader(file, plan.source)?.id)
        assertEquals(listOf(planId), geometryService.getGeometryPlanAreas(searchBbox).map(GeometryPlanArea::id))
    }

    @Test
    fun getLocationTrackHeightsCoversTrackStartsAndEnds() {
        val trackNumber = trackNumber(getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        referenceLineService.saveDraft(
            referenceLine(
                trackNumberId = trackNumberId,
                startAddress = TrackMeter("0154", BigDecimal("123.4")),
                draft = true,
            ),
            alignment(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        val locationTrackId = locationTrackService.saveDraft(
            locationTrack(trackNumberId, draft = true),
            alignment(segment(yRangeToSegmentPoints(1..29))),
        ).id

        kmPostService.saveDraft(
            LayoutBranch.main,
            kmPost(trackNumberId, KmNumber("0155"), Point(0.0, 14.5), draft = true),
        )
        kmPostService.saveDraft(
            LayoutBranch.main,
            kmPost(trackNumberId, KmNumber("0156"), Point(0.0, 27.6), draft = true),
        )

        // tickLength = 5 => normal ticks less than 2.5 distance apart from a neighbor get dropped
        val actual = geometryService.getLocationTrackHeights(locationTrackId, PublicationState.DRAFT, 0.0, 30.0, 5)!!

        // location track starts 1 m after reference line start; reference line start address is 123.4; so first address
        // is 124.4. First km post is at m 14.5 -> 13.5 in location track. Ordinary ticks always start at track meter 0
        // so we get nice addresses for all of them, we just skip the ones coming before the track start.
        val expectedData = listOf(
            "0154" to listOf(
                124.4 to 0.0,
                125.0 to 0.6,
                130.0 to 5.6,
                135.0 to 10.6,
            ),
            "0155" to listOf(
                0.0 to 13.5,
                5.0 to 18.5,
                10.0 to 23.5,
            ),
            "0156" to listOf(
                0.0 to 26.6,
                1.4 to 28.0,
            ),
        )

        actual.forEachIndexed { kmIndex, actualKm ->
            val expectedKm = expectedData[kmIndex]
            assertEquals(KmNumber(expectedKm.first), actualKm.kmNumber)
            val expectedLastM = if (kmIndex == expectedData.lastIndex) {
                expectedData.last().second.last().second
            } else {
                expectedData[kmIndex + 1].second.first().second
            }
            assertEquals(expectedLastM, actualKm.endM, "endM at index $kmIndex")
            assertEquals(expectedKm.second.size, actualKm.trackMeterHeights.size)

            actualKm.trackMeterHeights.forEachIndexed { mIndex, actualM ->
                val expectedM = expectedKm.second[mIndex]
                assertEquals(expectedM.first, actualM.meter)
                assertEquals(expectedM.second, actualM.m, 0.001)
            }
        }
    }

    @Test
    fun getLocationTrackHeightsReturnsBothOrdinaryTicksAndPlanBoundaries() {
        val trackNumber = trackNumber(getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        referenceLineService.saveDraft(
            referenceLine(trackNumberId, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        val p1 = insertPlanWithGeometry("plan1.xml", trackNumber.number)
        val p2 = insertPlanWithGeometry("plan2.xml", trackNumber.number)
        val p3 = insertPlanWithGeometry("plan3.xml", trackNumber.number)
        val locationTrackId = locationTrackService.saveDraft(
            locationTrack(trackNumberId, draft = true),
            alignment(
                segment(yRangeToSegmentPoints(0..6), sourceId = p1.alignments[0].elements[0].id, sourceStart = 0.0),
                segment(yRangeToSegmentPoints(6..10)),
                segment(yRangeToSegmentPoints(10..12), sourceId = p2.alignments[0].elements[0].id, sourceStart = 0.0),
                segment(yRangeToSegmentPoints(12..13)),
                segment(yRangeToSegmentPoints(13..15), sourceId = p3.alignments[0].elements[0].id, sourceStart = 0.0),
                segment(yRangeToSegmentPoints(15..17), sourceId = p1.alignments[0].elements[0].id, sourceStart = 0.0),
            ),
        ).id
        val kmHeights = geometryService.getLocationTrackHeights(locationTrackId, PublicationState.DRAFT, 0.0, 20.0, 5)!!
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
            17 to 50,
        ).map { (m, h) -> TrackMeterHeight(m.toDouble(), m.toDouble(), h?.toDouble(), Point(0.0, m.toDouble())) }
        assertEquals(expected, kmHeights[0].trackMeterHeights)
        val linkingSummary =
            geometryService.getLocationTrackGeometryLinkingSummary(locationTrackId, PublicationState.DRAFT)!!
        assertEquals(
            listOf(
                Triple(0.0, 6.0, FileName("plan1.xml")),
                Triple(6.0, 10.0, null),
                Triple(10.0, 12.0, FileName("plan2.xml")),
                Triple(12.0, 13.0, null),
                Triple(13.0, 15.0, FileName("plan3.xml")),
                Triple(15.0, 17.0, FileName("plan1.xml")),
            ),
            linkingSummary.map { item -> Triple(item.startM, item.endM, item.filename) },
        )
    }

    @Test
    fun getLocationTrackHeightsHandlesSegmentChangeAtRightBeforeKilometerStart() {
        val trackNumber = trackNumber(getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        referenceLineService.saveDraft(
            referenceLine(trackNumberId, startAddress = TrackMeter("0154", 400), draft = true),
            alignment(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        val sourceId = insertPlanWithGeometry("plan1.xml", trackNumber.number).alignments[0].elements[0].id
        val locationTrackId = locationTrackService.saveDraft(
            locationTrack(trackNumberId, draft = true),
            alignment(
                segment(yRangeToSegmentPoints(0..2)),
                segment(yRangeToSegmentPoints(2..9), sourceId = sourceId, sourceStart = 0.0),
                segment(yRangeToSegmentPoints(9..10)),
                segment(yRangeToSegmentPoints(10..20), sourceId = sourceId, sourceStart = 0.0),
            ),
        ).id
        // geocoding rounds m-values to three decimals half-up, so placing the km post juuuuuuuust here in fact rounds
        // its position back to exactly 10, causing the 9..10 connection segment's end address to also be in
        // track km 0155
        val post = kmPost(trackNumberId, KmNumber("0155"), Point(0.0, 10.00001), draft = true)
        kmPostService.saveDraft(LayoutBranch.main, post)

        val actual = geometryService.getLocationTrackHeights(
            locationTrackId,
            PublicationState.DRAFT,
            0.0,
            20.0,
            5,
        )!!

        val expected = listOf(
            "0154" to listOf(
                400.0 to null,
                402.0 to null,
                402.0 to 50.0,
                405.0 to 50.0,
                409.0 to 50.0,
                409.0 to null,
            ),
            "0155" to listOf(
                0.0 to null,
                0.0 to 50.0,
                5.0 to 50.0,
                10.0 to 50.0,
            ),
        )
        assertEquals(expected.size, actual.size)
        actual.forEachIndexed { kmIndex, actualKm ->
            val expectedKm = expected[kmIndex]
            assertEquals(expectedKm.second.size, actualKm.trackMeterHeights.size)
            actualKm.trackMeterHeights.forEachIndexed { mIndex, actualM ->
                val expectedM = expectedKm.second[mIndex]
                assertEquals(expectedM.first, actualM.meter)
                assertEquals(expectedM.second, actualM.height)
            }
        }
    }

    @Test
    fun getLocationTrackHeightsHandlesKmShorterThanTickLength() {
        val trackNumber = trackNumber(getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        referenceLineService.saveDraft(
            referenceLine(trackNumberId, startAddress = TrackMeter("0154", 0), draft = true),
            alignment(segment(Point(0.0, 0.0), Point(0.0, 100.0)))
        )
        val locationTrackId = locationTrackService.saveDraft(
            locationTrack(trackNumberId, draft = true),
            alignment(
                segment(yRangeToSegmentPoints(0..10)),
            ),
        ).id
        kmPostService.saveDraft(
            LayoutBranch.main,
            kmPost(trackNumberId, KmNumber("0155"), Point(0.0, 8.0), draft = true),
        )
        kmPostService.saveDraft(
            LayoutBranch.main,
            kmPost(trackNumberId, KmNumber("0156"), Point(0.0, 9.0), draft = true),
        )
        val actual = geometryService.getLocationTrackHeights(locationTrackId, PublicationState.DRAFT, 0.0, 20.0, 5)!!
        assertEquals(3, actual.size)
        assertEquals(2, actual[0].trackMeterHeights.size)
        assertEquals(1, actual[1].trackMeterHeights.size)
        assertEquals(2, actual[2].trackMeterHeights.size)
    }

    fun yRangeToSegmentPoints(range: IntRange) =
        toSegmentPoints(to3DMPoints(range.map { i -> Point(0.0, i.toDouble()) }))

    fun insertPlanWithGeometry(filename: String, trackNumber: TrackNumber): GeometryPlan {
        val version = geometryDao.insertPlan(
            planWithGeometryAndHeights(trackNumber), InfraModelFile(FileName(filename), "<a></a>"), null
        )
        return geometryDao.fetchPlan(version)
    }

    fun planWithGeometryAndHeights(trackNumber: TrackNumber): GeometryPlan = plan(
        trackNumber,
        srid = LAYOUT_SRID,
        verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
        alignments = listOf(
            geometryAlignment(
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
                    ),
                ),
            ),
        ),
    )

    private fun deletePlans() {
        jdbc.update("truncate geometry.plan cascade;", mapOf<String, Any>())
    }
}
