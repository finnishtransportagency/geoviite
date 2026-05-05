package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.map.toPolygon
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.assertEquals
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.to3DMPoints
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import kotlin.test.assertContains

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeometryServiceIT
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val locationTrackService: LocationTrackService,
    private val kmPostService: LayoutKmPostService,
    private val geometryDao: GeometryDao,
    private val geometryService: GeometryService,
    private val coordinateTransformationService: CoordinateTransformationService,
) : DBTestBase() {

    @BeforeEach
    fun setup() {
        testDBService.clearGeometryTables()
    }

    @Test
    fun `Hiding GeometryPlans works`() {
        val file = testFile()
        val plan = plan(testDBService.getUnusedTrackNumber(), fileName = file.name)
        val polygon = someBoundingPolygon()
        val planId = geometryDao.insertPlan(plan, file, polygon).id
        val searchBbox = polygon.boundingBox

        assertEquals(planId, geometryService.fetchDuplicateGeometryPlanHeader(file.hash, plan.source)?.id)
        assertEquals(listOf(planId), geometryService.getGeometryPlanAreas(searchBbox).map(GeometryPlanArea::id))

        geometryService.setPlanHidden(planId, true)
        assertFalse(geometryService.getPlanHeaders().any { p -> p.id == planId })
        assertEquals(null, geometryService.fetchDuplicateGeometryPlanHeader(file.hash, plan.source)?.id)
        assertEquals(
            listOf<DomainId<GeometryPlan>>(),
            geometryService.getGeometryPlanAreas(searchBbox).map(GeometryPlanArea::id),
        )

        geometryService.setPlanHidden(planId, false)
        assertTrue(geometryService.getPlanHeaders().any { p -> p.id == planId })
        assertEquals(planId, geometryService.fetchDuplicateGeometryPlanHeader(file.hash, plan.source)?.id)
        assertEquals(listOf(planId), geometryService.getGeometryPlanAreas(searchBbox).map(GeometryPlanArea::id))
    }

    @Test
    fun getLocationTrackHeightsCoversTrackStartsAndEnds() {
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.save(trackNumber).id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(
                trackNumberId = trackNumberId,
                startAddress = TrackMeter("0154", BigDecimal("123.4")),
                draft = true,
            ),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    trackGeometryOfSegments(segment(yRangeToSegmentPoints(1..29))),
                )
                .id

        kmPostService.saveDraft(
            LayoutBranch.main,
            kmPost(trackNumberId, KmNumber("0155"), kmPostGkLocation(0.0, 14.5), draft = true),
        )
        kmPostService.saveDraft(
            LayoutBranch.main,
            kmPost(trackNumberId, KmNumber("0156"), kmPostGkLocation(0.0, 27.6), draft = true),
        )

        // tickLength = 5 => normal ticks less than 2.5 distance apart from a neighbor get dropped
        val actual =
            geometryService.getLocationTrackHeights(
                MainLayoutContext.draft,
                locationTrackId,
                LineM(0.0),
                LineM(30.0),
                5,
            )!!

        // location track starts 1 m after reference line start; reference line start address is
        // 123.4; so first address
        // is 124.4. First km post is at m 14.5 -> 13.5 in location track. Ordinary ticks always
        // start at track meter 0
        // so we get nice addresses for all of them, we just skip the ones coming before the track
        // start.
        val expectedData =
            listOf(
                    "0154" to listOf(124.4 to 0.0, 125.0 to 0.6, 130.0 to 5.6, 135.0 to 10.6),
                    "0155" to listOf(0.0 to 13.5, 5.0 to 18.5, 10.0 to 23.5),
                    "0156" to listOf(0.0 to 26.6, 1.4 to 28.0),
                )
                .map { (kmNumber, ms) -> kmNumber to ms.map { (meter, m) -> meter to LineM<LocationTrackM>(m) } }

        actual.forEachIndexed { kmIndex, actualKm ->
            val expectedKm = expectedData[kmIndex]
            assertEquals(KmNumber(expectedKm.first), actualKm.kmNumber)
            // last lastM needs to be exactly correct, because it specifies the end of the track,
            // and the front-end code
            // depends on that being clean; the km post locations can slightly wobble due the GK
            // coordinate
            // transformation though
            if (kmIndex == expectedData.lastIndex) {
                assertEquals(expectedData.last().second.last().second, actualKm.endM, "endM at track end")
            } else {
                assertEquals(
                    expectedData[kmIndex + 1].second.first().second,
                    actualKm.endM,
                    0.00001,
                    "endM at index $kmIndex",
                )
            }
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
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.save(trackNumber).id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, draft = true),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        val p1 = insertPlanWithGeometry("plan1.xml", trackNumber.number)
        val p2 = insertPlanWithGeometry("plan2.xml", trackNumber.number)
        val p3 = insertPlanWithGeometry("plan3.xml", trackNumber.number)
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    trackGeometryOfSegments(
                        segment(
                            yRangeToSegmentPoints(0..6),
                            sourceId = p1.alignments[0].elements[0].id,
                            sourceStartM = 0.0,
                        ),
                        segment(yRangeToSegmentPoints(6..10)),
                        segment(
                            yRangeToSegmentPoints(10..12),
                            sourceId = p2.alignments[0].elements[0].id,
                            sourceStartM = 0.0,
                        ),
                        segment(yRangeToSegmentPoints(12..13)),
                        segment(
                            yRangeToSegmentPoints(13..15),
                            sourceId = p3.alignments[0].elements[0].id,
                            sourceStartM = 0.0,
                        ),
                        segment(
                            yRangeToSegmentPoints(15..17),
                            sourceId = p1.alignments[0].elements[0].id,
                            sourceStartM = 0.0,
                        ),
                    ),
                )
                .id
        val kmHeights =
            geometryService.getLocationTrackHeights(
                MainLayoutContext.draft,
                locationTrackId,
                LineM(0.0),
                LineM(20.0),
                5,
            )!!
        // this track is exactly straight on the reference line, so m-values and track meters
        // coincide perfectly; also,
        // the profile is at exactly 50 meters height at every point where it's linked
        val expected =
            listOf(
                    0 to 50,
                    // 5 to 50, // should be filtered due to being less than a half-tick distance
                    // from nearest
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
                )
                .map { (m, h) ->
                    TrackMeterHeight(
                        LineM<LocationTrackM>(m.toDouble()),
                        m.toDouble(),
                        h?.toDouble(),
                        Point(0.0, m.toDouble()),
                    )
                }
        assertEquals(expected, kmHeights[0].trackMeterHeights)
        val linkingSummary =
            geometryService.getLocationTrackGeometryLinkingSummary(MainLayoutContext.draft, locationTrackId)!!
        assertEquals(
            listOf(
                Triple(0.0, 6.0, FileName("plan1.xml")),
                Triple(6.0, 10.0, null),
                Triple(10.0, 12.0, FileName("plan2.xml")),
                Triple(12.0, 13.0, null),
                Triple(13.0, 15.0, FileName("plan3.xml")),
                Triple(15.0, 17.0, FileName("plan1.xml")),
            ),
            linkingSummary.map { item -> Triple(item.startM.distance, item.endM.distance, item.filename) },
        )
    }

    @Test
    fun getLocationTrackHeightsHandlesSegmentChangeAtRightBeforeKilometerStart() {
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.save(trackNumber).id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, startAddress = TrackMeter("0154", 400), draft = true),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        val sourceElement = insertPlanWithGeometry("plan1.xml", trackNumber.number).alignments[0].elements[0]
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    trackGeometryOfSegments(
                        segment(yRangeToSegmentPoints(0..2)),
                        segment(yRangeToSegmentPoints(2..9), sourceId = sourceElement.id, sourceStartM = 0.0),
                        segment(yRangeToSegmentPoints(9..10)),
                        segment(yRangeToSegmentPoints(10..20), sourceId = sourceElement.id, sourceStartM = 0.0),
                    ),
                )
                .id
        // geocoding rounds m-values to three decimals half-up, so placing the km post juuuuuuuust
        // here in fact rounds
        // its position back to exactly 10, causing the 9..10 connection segment's end address to
        // also be in
        // track km 0155
        val post = kmPost(trackNumberId, KmNumber("0155"), kmPostGkLocation(0.0, 10.00001), draft = true)
        kmPostService.saveDraft(LayoutBranch.main, post)

        val actual =
            geometryService.getLocationTrackHeights(
                MainLayoutContext.draft,
                locationTrackId,
                LineM(0.0),
                LineM(20.0),
                5,
            )!!

        val expected =
            listOf(
                "0154" to
                    listOf(400.0 to null, 402.0 to null, 402.0 to 50.0, 405.0 to 50.0, 409.0 to 50.0, 409.0 to null),
                "0155" to listOf(0.0 to null, 0.0 to 50.0, 5.0 to 50.0, 10.0 to 50.0),
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
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.save(trackNumber).id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, startAddress = TrackMeter("0154", 0), draft = true),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    trackGeometryOfSegments(segment(yRangeToSegmentPoints(0..10))),
                )
                .id
        kmPostService.saveDraft(
            LayoutBranch.main,
            kmPost(trackNumberId, KmNumber("0155"), kmPostGkLocation(0.0, 8.0), draft = true),
        )
        kmPostService.saveDraft(
            LayoutBranch.main,
            kmPost(trackNumberId, KmNumber("0156"), kmPostGkLocation(0.0, 9.0), draft = true),
        )
        val actual =
            geometryService.getLocationTrackHeights(
                MainLayoutContext.draft,
                locationTrackId,
                LineM(0.0),
                LineM(20.0),
                5,
            )!!
        assertEquals(3, actual.size)
        assertEquals(2, actual[0].trackMeterHeights.size)
        assertEquals(1, actual[1].trackMeterHeights.size)
        assertEquals(2, actual[2].trackMeterHeights.size)
    }

    fun yRangeToSegmentPoints(range: IntRange) =
        toSegmentPoints(to3DMPoints(range.map { i -> Point(0.0, i.toDouble()) }))

    fun insertPlanWithGeometry(
        filename: String,
        trackNumber: TrackNumber,
        alignments: List<GeometryAlignment> = listOf(defaultAlignmentWithHeights()),
    ): GeometryPlan {
        val version =
            geometryDao.insertPlan(
                plan(
                    trackNumber,
                    srid = LAYOUT_SRID,
                    verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
                    alignments = alignments,
                ),
                InfraModelFile(FileName(filename), "<a></a>"),
                null,
            )
        return geometryDao.fetchPlan(version)
    }

    fun defaultAlignmentWithHeights(): GeometryAlignment =
        geometryAlignment(
            elements = listOf(lineFromOrigin(1.0)),
            name = "foo",
            profile =
                GeometryProfile(
                    PlanElementName("aoeu"),
                    listOf(
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
        )

    fun alignmentWithProfile(elementLength: Double, profile: GeometryProfile): GeometryAlignment =
        geometryAlignment(
            elements = listOf(line(Point(0.0, 0.0), Point(0.0, elementLength))),
            name = "foo",
            profile = profile,
        )

    @Test
    fun `Vertical geometry listing returns break points with correct field values for a location track with linked geometry plans`() {
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.save(trackNumber).id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, draft = true),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        val profile =
            GeometryProfile(
                PlanElementName("profile"),
                listOf(
                    VIPoint(PlanElementName("start"), Point(0.0, 50.0)),
                    VICircularCurve(PlanElementName("curve"), Point(50.0, 50.0), BigDecimal(5000), BigDecimal(20)),
                    VIPoint(PlanElementName("end"), Point(100.0, 51.0)),
                ),
            )
        val plan =
            insertPlanWithGeometry(
                "plan_profile.xml",
                trackNumber.number,
                alignments = listOf(alignmentWithProfile(elementLength = 100.0, profile = profile)),
            )
        val sourceElement = plan.alignments[0].elements[0]
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    trackGeometryOfSegments(
                        segment(yRangeToSegmentPoints(0..100), sourceId = sourceElement.id, sourceStartM = 0.0)
                    ),
                )
                .id

        val listing = geometryService.getVerticalGeometryListing(MainLayoutContext.draft, locationTrackId)

        assertEquals(1, listing.size, "Single plan with 1 curve should produce exactly 1 listing entry")
        val entry = listing.single()
        assertEquals(plan.id, entry.planId)
        assertEquals(plan.fileName, entry.fileName)
        assertEquals(BigDecimal("5000"), entry.radius)
        assertFalse(entry.overlapsAnother, "Single entry cannot overlap anything")
        assertNotNull(entry.start.address, "Start tangent point should be geocoded")
        assertNotNull(entry.end.address, "End tangent point should be geocoded")
        assertNotNull(entry.point.address, "Intersection point should be geocoded")
        assertNotNull(entry.tangent, "Tangent length should be calculated")
    }

    @Test
    fun `Vertical geometry listing returns empty list for location track with no linked geometry plans`() {
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.save(trackNumber).id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, draft = true),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    trackGeometryOfSegments(segment(yRangeToSegmentPoints(0..50))),
                )
                .id

        val listing = geometryService.getVerticalGeometryListing(MainLayoutContext.draft, locationTrackId)

        assertEquals(emptyList<VerticalGeometryListing>(), listing)
    }

    @Test
    fun `Vertical geometry listing for location track spanning multiple geometry plans returns break points from all plans`() {
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.save(trackNumber).id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, draft = true),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        // Two plans with distinct profiles so deduplication keeps both entries
        val profile1 =
            GeometryProfile(
                PlanElementName("profile1"),
                listOf(
                    VIPoint(PlanElementName("start"), Point(0.0, 50.0)),
                    VICircularCurve(PlanElementName("curve"), Point(25.0, 55.0), BigDecimal(50), BigDecimal(10)),
                    VIPoint(PlanElementName("end"), Point(50.0, 51.0)),
                ),
            )
        val profile2 =
            GeometryProfile(
                PlanElementName("profile2"),
                listOf(
                    VIPoint(PlanElementName("start"), Point(0.0, 50.0)),
                    VICircularCurve(PlanElementName("curve"), Point(25.0, 56.0), BigDecimal(50), BigDecimal(10)),
                    VIPoint(PlanElementName("end"), Point(50.0, 51.0)),
                ),
            )
        val plan1 =
            insertPlanWithGeometry(
                "plan_profile_1.xml",
                trackNumber.number,
                alignments = listOf(alignmentWithProfile(elementLength = 50.0, profile = profile1)),
            )
        val plan2 =
            insertPlanWithGeometry(
                "plan_profile_2.xml",
                trackNumber.number,
                alignments = listOf(alignmentWithProfile(elementLength = 50.0, profile = profile2)),
            )
        val sourceElement1 = plan1.alignments[0].elements[0]
        val sourceElement2 = plan2.alignments[0].elements[0]
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    trackGeometryOfSegments(
                        segment(yRangeToSegmentPoints(0..50), sourceId = sourceElement1.id, sourceStartM = 0.0),
                        segment(yRangeToSegmentPoints(50..100), sourceId = sourceElement2.id, sourceStartM = 0.0),
                    ),
                )
                .id

        val listing = geometryService.getVerticalGeometryListing(MainLayoutContext.draft, locationTrackId)

        assertEquals(2, listing.size, "Two plans with distinct curves should produce exactly 2 entries")
        assertEquals(setOf(plan1.id, plan2.id), listing.map { it.planId }.toSet())
        assertEquals(setOf(plan1.fileName, plan2.fileName), listing.map { it.fileName }.toSet())
        listing.forEach { entry ->
            assertEquals(BigDecimal("-50"), entry.radius)
            assertNotNull(entry.start.address, "Start tangent point should be geocoded")
            assertNotNull(entry.end.address, "End tangent point should be geocoded")
            assertNotNull(entry.point.address, "Intersection point should be geocoded")
        }
    }

    @Test
    fun `Vertical geometry listing overlapsAnother is set correctly when gradient sections overlap`() {
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber(), draft = true)
        val trackNumberId = layoutTrackNumberDao.save(trackNumber).id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, draft = true),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
        )
        // Two plans with distinct profiles whose curve tangent ranges overlap in geocoded addresses.
        // Addresses are computed from plan alignment coordinates, so curves at similar stations
        // on both plans produce entries with overlapping address ranges.
        val profile1 =
            GeometryProfile(
                PlanElementName("profile1"),
                listOf(
                    VIPoint(PlanElementName("start"), Point(0.0, 50.0)),
                    VICircularCurve(PlanElementName("curve"), Point(30.0, 55.0), BigDecimal(50), BigDecimal(10)),
                    VIPoint(PlanElementName("end"), Point(60.0, 50.0)),
                ),
            )
        val profile2 =
            GeometryProfile(
                PlanElementName("profile2"),
                listOf(
                    VIPoint(PlanElementName("start"), Point(0.0, 50.0)),
                    VICircularCurve(PlanElementName("curve"), Point(35.0, 55.0), BigDecimal(50), BigDecimal(10)),
                    VIPoint(PlanElementName("end"), Point(60.0, 50.0)),
                ),
            )
        val plan1 =
            insertPlanWithGeometry(
                "plan_overlap_1.xml",
                trackNumber.number,
                alignments = listOf(alignmentWithProfile(elementLength = 60.0, profile = profile1)),
            )
        val plan2 =
            insertPlanWithGeometry(
                "plan_overlap_2.xml",
                trackNumber.number,
                alignments = listOf(alignmentWithProfile(elementLength = 60.0, profile = profile2)),
            )
        val sourceElement1 = plan1.alignments[0].elements[0]
        val sourceElement2 = plan2.alignments[0].elements[0]
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    trackGeometryOfSegments(
                        segment(yRangeToSegmentPoints(0..60), sourceId = sourceElement1.id, sourceStartM = 0.0),
                        segment(yRangeToSegmentPoints(60..100), sourceId = sourceElement2.id, sourceStartM = 0.0),
                    ),
                )
                .id

        val listing = geometryService.getVerticalGeometryListing(MainLayoutContext.draft, locationTrackId)

        assertEquals(2, listing.size, "Two plans with overlapping curves should produce exactly 2 entries")
        assertEquals(setOf(plan1.id, plan2.id), listing.map { it.planId }.toSet())
        listing.forEach { entry ->
            assertEquals(BigDecimal("-50"), entry.radius)
            assertTrue(entry.overlapsAnother, "Both entries should overlap since their geocoded address ranges intersect")
        }
    }

    @Test
    fun `Overlapping plan search finds plans that are within 10m of alignment`() {
        val tn = TrackNumber("001")
        val a1 = geometryAlignment(line(Point(0.0, 0.0), Point(10.0, 0.0)))
        val a2 = geometryAlignment(line(Point(20.0, 0.0), Point(30.0, 0.0)))
        val a3 = geometryAlignment(line(Point(40.0, 0.0), Point(50.0, 0.0)))
        val a4 = geometryAlignment(line(Point(60.0, 0.0), Point(70.0, 0.0)))
        val a5 = geometryAlignment(line(Point(80.0, 0.0), Point(90.0, 0.0)))
        val a6 = geometryAlignment(line(Point(40.0, 20.0), Point(50.0, 20.0)))
        val a7 = geometryAlignment(line(Point(40.0, -10.0), Point(50.0, -10.0)))

        val searchPolygon = toPolygon(referenceLineGeometry(segment(Point(32.0, 0.0), Point(50.0, 0.0))), 10.0)!!

        val tf = coordinateTransformationService.getLayoutTransformation(LAYOUT_SRID)

        // Ends before alignment start -> not found
        geometryDao.insertPlan(
            plan(tn, LAYOUT_SRID, a1),
            testFile(),
            getBoundingPolygonPointsFromAlignments(listOf(a1), tf),
        )
        val plan2EndsWithinAlignmentBuffer =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a2), tf),
            )
        val plan3CompletelyWithin =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a3),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a3), tf),
            )
        val plan4TouchesEndOfAlignmentBuffer =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a4),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a4), tf),
            )
        // Start after alignment -> not found
        geometryDao.insertPlan(
            plan(tn, LAYOUT_SRID, a5),
            testFile(),
            getBoundingPolygonPointsFromAlignments(listOf(a5), tf),
        )
        // Plan that is too far to the side -> not found
        geometryDao.insertPlan(
            plan(tn, LAYOUT_SRID, a6),
            testFile(),
            getBoundingPolygonPointsFromAlignments(listOf(a6), tf),
        )
        val plan7TouchesBufferFromSide =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a7),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a7), tf),
            )
        val plan8Hidden =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a4),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a4), tf),
            )
        geometryDao.setPlanHidden(plan8Hidden.id, true)

        val overlapping = geometryService.getOverlappingPlanHeaders(searchPolygon).map { it.id }

        assertEquals(4, overlapping.size)
        assertContains(overlapping, plan2EndsWithinAlignmentBuffer.id)
        assertContains(overlapping, plan3CompletelyWithin.id)
        assertContains(overlapping, plan4TouchesEndOfAlignmentBuffer.id)
        assertContains(overlapping, plan7TouchesBufferFromSide.id)
    }
}
