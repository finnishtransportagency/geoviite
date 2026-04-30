package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geometry.CurvedProfileSegment
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryProfile
import fi.fta.geoviite.infra.geometry.VICircularCurve
import fi.fta.geoviite.infra.geometry.VIPoint
import fi.fta.geoviite.infra.geometry.VerticalIntersection
import fi.fta.geoviite.infra.geometry.angleFractionBetweenPoints
import fi.fta.geoviite.infra.geometry.circCurveStationPoint
import fi.fta.geoviite.infra.geometry.circularCurveCenterPoint
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.geometry.tangentPointsOfPvi
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.math.roundTo6Decimals
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LAYOUT_COORDINATE_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLineAndGeometry
import fi.fta.geoviite.infra.tracklayout.referenceLineAndGeometryOfElements
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.to3DMPoints
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfElements
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtLocationTrackProfileIT
@Autowired
constructor(mockMvc: MockMvc, private val extTestDataService: ExtApiTestDataServiceV1) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @Test
    fun `Profile API versioning works across multiple publications`() {
        val trackStart = Point(0.0, 0.0)
        val trackEnd = Point(0.0, 700.0)
        val expectedTrackStartAddress = "0000+0000.000"
        val expectedTrackEndAddress = "0000+0700.000"
        val expectedPviAddress = "0000+0350.000"

        val plan =
            insertPlan(
                listOf(
                    profileAlignment(
                        start = trackStart,
                        end = trackEnd,
                        profileElements =
                            listOf(
                                VIPoint(PlanElementName("start"), Point(0.0, 50.0)),
                                VICircularCurve(
                                    PlanElementName("curve"),
                                    Point(350.0, 50.0),
                                    BigDecimal(20000),
                                    BigDecimal(155),
                                ),
                                VIPoint(PlanElementName("end"), Point(700.0, 51.0)),
                            ),
                    )
                )
            )
        val elements = plan.alignments[0].elements
        val (trackNumberId, referenceLineId) = insertTrackNumberWithReferenceLine(elements)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements))

        // Publication 1: track with plan-linked profile
        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        // Publication 2: empty publish — no changes to the track
        val publication2 = extTestDataService.publishInMain()

        // Publication 3: re-save the track with unlinked segments (removes profile)
        val (track, geometry) = mainDraftContext.fetchLocationTrackWithGeometry(trackId)!!
        val unlinkedGeometry =
            trackGeometryOfSegments(geometry.segments.map { it.copy(sourceId = null, sourceStartM = null) })
        mainDraftContext.save(track, unlinkedGeometry)
        val publication3 = extTestDataService.publishInMain(locationTracks = listOf(trackId))

        // --- Profile endpoint assertions ---

        // Latest (after publication 3) should have no PVI points (profile removed but track exists)
        api.locationTrackProfile.get(oid).also { response ->
            assertProfileResponseTopLevel(response, oid, publication3.uuid)
            assertProfileInterval(response.osoitevali, expectedTrackStartAddress, expectedTrackEndAddress, 0)
        }

        // Explicit v1 still has profile; v2 has identical content (no change between v1 and v2)
        api.locationTrackProfile.getAtVersion(oid, publication1.uuid).also { v1 ->
            assertProfileResponseTopLevel(v1, oid, publication1.uuid)
            assertProfileInterval(v1.osoitevali, expectedTrackStartAddress, expectedTrackEndAddress, 1)

            api.locationTrackProfile.getAtVersion(oid, publication2.uuid).also { v2 ->
                assertProfileResponseTopLevel(v2, oid, publication2.uuid)
                assertEquals(v1.osoitevali, v2.osoitevali, "V2 profile should be identical to V1")
            }
        }

        // Explicit v3 should have no PVI points (profile removed)
        api.locationTrackProfile.getAtVersion(oid, publication3.uuid).also { response ->
            assertProfileResponseTopLevel(response, oid, publication3.uuid)
            assertProfileInterval(response.osoitevali, expectedTrackStartAddress, expectedTrackEndAddress, 0)
        }

        // --- Changes endpoint assertions ---

        // No modification when comparing same version
        api.locationTrackProfile.assertNoModificationBetween(oid, publication1.uuid, publication1.uuid)

        // No modification between v1 and v2 (nothing changed)
        api.locationTrackProfile.assertNoModificationBetween(oid, publication1.uuid, publication2.uuid)

        // Modification between v2 and v3 (profile was removed)
        api.locationTrackProfile.getModifiedBetween(oid, publication2.uuid, publication3.uuid).also { response ->
            assertProfileChangesTopLevel(response, oid, publication2.uuid, publication3.uuid)
            assertProfileInterval(response.osoitevalit.single(), expectedPviAddress, expectedPviAddress, 0)
        }

        // Modification between v1 and v3 (profile was removed)
        api.locationTrackProfile.getModifiedBetween(oid, publication1.uuid, publication3.uuid).also { response ->
            assertProfileChangesTopLevel(response, oid, publication1.uuid, publication3.uuid)
            assertProfileInterval(response.osoitevalit.single(), expectedPviAddress, expectedPviAddress, 0)
        }

        // No modification since latest
        api.locationTrackProfile.assertNoModificationSince(oid, publication3.uuid)
    }

    @Test
    fun `Response contains correctly structured PVI points with expected values`() {
        val curveRadius = BigDecimal(20000)
        val leftPvi = Point(0.0, 50.0)
        val middlePvi = Point(500.0, 50.0)
        val rightPvi = Point(600.0, 51.0)
        val signedRadius = 20000.0

        // Pre-compute expected values using the same math helpers the production code uses
        val tangentPoints = tangentPointsOfPvi(leftPvi, middlePvi, rightPvi, signedRadius)
        val curveCenter = circularCurveCenterPoint(signedRadius, tangentPoints.first, middlePvi)
        val curvedSegment =
            CurvedProfileSegment(
                PlanElementName("curve"),
                tangentPoints.first,
                tangentPoints.second,
                curveCenter,
                signedRadius,
            )
        val stationPoint = circCurveStationPoint(curvedSegment)
        val startGradient = angleFractionBetweenPoints(stationPoint, curvedSegment.start)!!
        val endGradient = angleFractionBetweenPoints(stationPoint, curvedSegment.end)!!

        // Track geometry is a straight north-going line covering all PVI stations.
        // With no km-posts and default reference line start at 0km, distance along track = address meters.
        // Geographic coordinates on this line: x=0, y=station. Address: "0000+<station>".
        val trackStart = Point(0.0, 0.0)
        val trackEnd = Point(0.0, 700.0)

        val alignment =
            profileAlignment(
                start = trackStart,
                end = trackEnd,
                profileElements =
                    listOf(
                        VIPoint(PlanElementName("start"), leftPvi),
                        VICircularCurve(PlanElementName("curve"), middlePvi, curveRadius, BigDecimal(155)),
                        VIPoint(PlanElementName("end"), rightPvi),
                    ),
            )
        val plan = insertPlan(listOf(alignment), verticalCoordinateSystem = VerticalCoordinateSystem.N2000)
        val (trackNumberId, referenceLineId) = insertTrackNumberWithReferenceLine(plan.alignments[0].elements)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(
                locationTrack(trackNumberId),
                trackGeometryOfElements(plan.alignments[0].elements),
            )
        val publication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        val response = api.locationTrackProfile.get(oid)
        assertProfileResponseTopLevel(response, oid, publication.uuid)

        val pviPoint = response.osoitevali.taitepisteet.single()

        assertIntersectionPoint(
            pviPoint.taite,
            expectedHeight = stationPoint.y,
            expectedHeightN2000 = stationPoint.y,
            expectedAddress = expectedAddress(stationPoint.x),
            expectedLocation = Point(0.0, stationPoint.x),
            label = "PVI point",
        )

        assertCurvedSectionEndpoint(
            pviPoint.pyoristyksen_alku,
            expectedHeight = curvedSegment.start.y,
            expectedHeightN2000 = curvedSegment.start.y,
            expectedGradient = startGradient,
            expectedAddress = expectedAddress(curvedSegment.start.x),
            expectedLocation = Point(0.0, curvedSegment.start.x),
            label = "Curve start",
        )

        assertCurvedSectionEndpoint(
            pviPoint.pyoristyksen_loppu,
            expectedHeight = curvedSegment.end.y,
            expectedHeightN2000 = curvedSegment.end.y,
            expectedGradient = endGradient,
            expectedAddress = expectedAddress(curvedSegment.end.x),
            expectedLocation = Point(0.0, curvedSegment.end.x),
            label = "Curve end",
        )

        // Radius and tangent
        assertEquals(curveRadius.toString(), pviPoint.pyoristyssade, "Rounding radius")
        assertEquals(
            roundTo3Decimals(lineLength(tangentPoints.first, stationPoint)).toString(),
            pviPoint.tangentti,
            "Tangent length",
        )

        // Linear sections (kaltevuusjaksot)
        // Backward: from leftPvi (start of profile) to the curve's station point
        assertLinearSection(
            pviPoint.kaltevuusjakso_taaksepain,
            expectedLength = roundTo3Decimals(stationPoint.x - leftPvi.x),
            expectedLinearPart = roundTo3Decimals(tangentPoints.first.x - leftPvi.x),
            label = "Backward",
        )
        // Forward: from the curve's station point to rightPvi (end of profile)
        assertLinearSection(
            pviPoint.kaltevuusjakso_eteenpain,
            expectedLength = roundTo3Decimals(rightPvi.x - stationPoint.x),
            expectedLinearPart = roundTo3Decimals(rightPvi.x - tangentPoints.second.x),
            label = "Forward",
        )

        // Station values (paaluluku)
        assertStationValues(
            pviPoint.paaluluku,
            expectedStart = curvedSegment.start.x,
            expectedIntersection = stationPoint.x,
            expectedEnd = curvedSegment.end.x,
        )

        // Metadata
        assertEquals("N2000", pviPoint.suunnitelman_korkeusjarjestelma, "Vertical coordinate system should be N2000")
        assertEquals("Korkeusviiva", pviPoint.suunnitelman_korkeusasema, "Elevation method should be Korkeusviiva")

        // Remarks
        assertEquals(0, pviPoint.huomiot.size, "Single plan should have no remarks")
    }

    @Test
    fun `N2000 heights equal original for N2000 source data`() {
        val plan = insertPlan(listOf(profileAlignment()), verticalCoordinateSystem = VerticalCoordinateSystem.N2000)
        val elements = plan.alignments[0].elements
        val (trackNumberId, referenceLineId) = insertTrackNumberWithReferenceLine(elements)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements))
        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        val pviPoint = api.locationTrackProfile.get(oid).osoitevali.taitepisteet.single()

        assertEquals(
            pviPoint.pyoristyksen_alku.korkeus_alkuperainen,
            pviPoint.pyoristyksen_alku.korkeus_n2000,
            "N2000 height should equal original for N2000 source data (start)",
        )
        assertEquals(
            pviPoint.taite.korkeus_alkuperainen,
            pviPoint.taite.korkeus_n2000,
            "N2000 height should equal original for N2000 source data (intersection)",
        )
        assertEquals(
            pviPoint.pyoristyksen_loppu.korkeus_alkuperainen,
            pviPoint.pyoristyksen_loppu.korkeus_n2000,
            "N2000 height should equal original for N2000 source data (end)",
        )
    }

    @Test
    fun `N43 source data returns korkeus_n2000 as null but includes original height`() {
        val plan = insertPlan(listOf(profileAlignment()), verticalCoordinateSystem = VerticalCoordinateSystem.N43)
        val elements = plan.alignments[0].elements
        val (trackNumberId, referenceLineId) = insertTrackNumberWithReferenceLine(elements)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements))
        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        val pviPoint = api.locationTrackProfile.get(oid).osoitevali.taitepisteet.single()

        assertNotNull(pviPoint.pyoristyksen_alku.korkeus_alkuperainen, "Original height should be present (start)")
        assertNull(pviPoint.pyoristyksen_alku.korkeus_n2000, "N2000 height should be null for N43 source data (start)")
        assertNotNull(pviPoint.taite.korkeus_alkuperainen, "Original height should be present (intersection)")
        assertNull(pviPoint.taite.korkeus_n2000, "N2000 height should be null for N43 source data (intersection)")
        assertNotNull(pviPoint.pyoristyksen_loppu.korkeus_alkuperainen, "Original height should be present (end)")
        assertNull(pviPoint.pyoristyksen_loppu.korkeus_n2000, "N2000 height should be null for N43 source data (end)")
    }

    @Test
    fun `N60 source data returns converted korkeus_n2000`() {
        // Coordinates must be within Finland for N60→N2000 triangulation to work
        val finlandOffset = Point(332000.0, 6817000.0)
        val plan =
            insertPlan(
                listOf(
                    profileAlignment(start = Point(0.0, 0.0) + finlandOffset, end = Point(0.0, 1000.0) + finlandOffset)
                ),
                verticalCoordinateSystem = VerticalCoordinateSystem.N60,
            )
        val elements = plan.alignments[0].elements
        val (trackNumberId, referenceLineId) = insertTrackNumberWithReferenceLine(elements)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements))
        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        val pviPoint = api.locationTrackProfile.get(oid).osoitevali.taitepisteet.single()

        assertNotNull(pviPoint.pyoristyksen_alku.korkeus_alkuperainen, "Original height should be present (start)")
        assertNotNull(
            pviPoint.pyoristyksen_alku.korkeus_n2000,
            "N2000 height should be present for N60 source data (start)",
        )
        assertNotEquals(
            pviPoint.pyoristyksen_alku.korkeus_alkuperainen,
            pviPoint.pyoristyksen_alku.korkeus_n2000,
            "N2000 height should differ from original for N60 source data (start)",
        )
        assertNotNull(pviPoint.taite.korkeus_alkuperainen, "Original height should be present (intersection)")
        assertNotNull(pviPoint.taite.korkeus_n2000, "N2000 height should be present for N60 source data (intersection)")
        assertNotEquals(
            pviPoint.taite.korkeus_alkuperainen,
            pviPoint.taite.korkeus_n2000,
            "N2000 height should differ from original for N60 source data (intersection)",
        )
        assertNotNull(pviPoint.pyoristyksen_loppu.korkeus_alkuperainen, "Original height should be present (end)")
        assertNotNull(
            pviPoint.pyoristyksen_loppu.korkeus_n2000,
            "N2000 height should be present for N60 source data (end)",
        )
        assertNotEquals(
            pviPoint.pyoristyksen_loppu.korkeus_alkuperainen,
            pviPoint.pyoristyksen_loppu.korkeus_n2000,
            "N2000 height should differ from original for N60 source data (end)",
        )
    }

    @Test
    fun `Single plan produces single address range covering full track`() {
        val plan = insertPlan(listOf(profileAlignment()))
        val elements = plan.alignments[0].elements
        val (trackNumberId, referenceLineId) = insertTrackNumberWithReferenceLine(elements)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements))
        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        val addressRange = api.locationTrackProfile.get(oid).osoitevali
        assertEquals("0000+0000.000", addressRange.alku, "Address range should start at track start")
        assertEquals("0000+1000.000", addressRange.loppu, "Address range should end at track end")
        val pviAddresses = addressRange.taitepisteet.map { it.taite.sijainti?.rataosoite }
        assertEquals(listOf("0000+0500.000"), pviAddresses, "Expected one PVI point at station 500")
    }

    @Test
    fun `Gap in plan linkage still returns all PVI points in single address range`() {
        // Plan 1: alignment 0-600m, curve at station 300
        val plan1 =
            insertPlan(
                listOf(
                    profileAlignment(
                        end = Point(0.0, 600.0),
                        profileElements =
                            listOf(
                                VIPoint(PlanElementName("start"), Point(0.0, 100.0)),
                                VICircularCurve(
                                    PlanElementName("curve"),
                                    Point(300.0, 100.0),
                                    BigDecimal(20000),
                                    BigDecimal(155),
                                ),
                                VIPoint(PlanElementName("end"), Point(600.0, 101.0)),
                            ),
                    )
                ),
                fileName = FileName("profile_gap_1.xml"),
            )
        // Plan 2: alignment at track position 800-1400, curve at station 300 (different heights from plan1)
        val plan2 =
            insertPlan(
                listOf(
                    profileAlignment(
                        start = Point(0.0, 800.0),
                        end = Point(0.0, 1400.0),
                        profileElements =
                            listOf(
                                VIPoint(PlanElementName("start"), Point(0.0, 102.0)),
                                VICircularCurve(
                                    PlanElementName("curve"),
                                    Point(300.0, 102.0),
                                    BigDecimal(20000),
                                    BigDecimal(155),
                                ),
                                VIPoint(PlanElementName("end"), Point(600.0, 103.0)),
                            ),
                    )
                ),
                fileName = FileName("profile_gap_2.xml"),
            )
        val sourceElement1 = plan1.alignments[0].elements[0]
        val sourceElement2 = plan2.alignments[0].elements[0]
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val referenceLineId =
            mainDraftContext
                .saveReferenceLine(
                    referenceLineAndGeometry(trackNumberId, segment(Point(0.0, 0.0), Point(0.0, 2000.0)))
                )
                .id

        // Segment 1 (0..600) linked to plan1, gap (600..800) unlinked, segment 2 (800..1400) linked to plan2
        val points1 = (0..600).map { Point(0.0, it.toDouble()) }
        val pointsGap = (600..800).map { Point(0.0, it.toDouble()) }
        val points2 = (800..1400).map { Point(0.0, it.toDouble()) }
        val (trackId, oid) =
            mainDraftContext.saveWithOid(
                locationTrack(trackNumberId),
                trackGeometryOfSegments(
                    segment(toSegmentPoints(to3DMPoints(points1)), sourceId = sourceElement1.id, sourceStartM = 0.0),
                    segment(toSegmentPoints(to3DMPoints(pointsGap))),
                    segment(toSegmentPoints(to3DMPoints(points2)), sourceId = sourceElement2.id, sourceStartM = 0.0),
                ),
            )
        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        // Plan1 curve at station 300 → track address 0+300. Plan2 curve at station 300 → track address 800+300=1100.
        val pviAddresses =
            api.locationTrackProfile.get(oid).osoitevali.taitepisteet.map { it.taite.sijainti?.rataosoite }
        assertEquals(
            listOf(expectedAddress(300.0), expectedAddress(1100.0)),
            pviAddresses,
            "Expected PVI points from both linked sections",
        )
    }

    @Test
    fun `overlapsAnother produces kaltevuusjakso_limittain remark`() {
        // Both plans cover full 0-1000m alignment with 2 elements each.
        // Plan 1 has its curve at station 450 (in element 1's range 0-500).
        // Plan 2 has its curve at station 550 (in element 2's range 500-1000).
        // The two curves describe overlapping profile regions near the connection point.
        val plan1 =
            insertPlan(
                listOf(
                    geometryAlignment(
                        elements =
                            listOf(
                                line(Point(0.0, 0.0), Point(0.0, 500.0), staStart = 0.0, name = "elem1"),
                                line(Point(0.0, 500.0), Point(0.0, 1000.0), staStart = 500.0, name = "elem2"),
                            ),
                        profile =
                            GeometryProfile(
                                PlanElementName("profile"),
                                listOf(
                                    VIPoint(PlanElementName("start"), Point(0.0, 100.0)),
                                    VICircularCurve(
                                        PlanElementName("curve"),
                                        Point(490.0, 100.0),
                                        BigDecimal(20000),
                                        BigDecimal(155),
                                    ),
                                    VIPoint(PlanElementName("end"), Point(1000.0, 101.0)),
                                ),
                            ),
                    )
                ),
                fileName = FileName("overlap_1.xml"),
            )
        val plan2 =
            insertPlan(
                listOf(
                    geometryAlignment(
                        elements =
                            listOf(
                                line(Point(0.0, 0.0), Point(0.0, 500.0), staStart = 0.0, name = "elem1"),
                                line(Point(0.0, 500.0), Point(0.0, 1000.0), staStart = 500.0, name = "elem2"),
                            ),
                        profile =
                            GeometryProfile(
                                PlanElementName("profile"),
                                listOf(
                                    VIPoint(PlanElementName("start"), Point(0.0, 99.0)),
                                    VICircularCurve(
                                        PlanElementName("curve"),
                                        Point(510.0, 100.0),
                                        BigDecimal(20000),
                                        BigDecimal(155),
                                    ),
                                    VIPoint(PlanElementName("end"), Point(1000.0, 101.0)),
                                ),
                            ),
                    )
                ),
                fileName = FileName("overlap_2.xml"),
            )
        // Segment 1 linked to plan1's first element (stations 0-500, contains curve at 450)
        // Segment 2 linked to plan2's second element (stations 500-1000, contains curve at 550)
        val sourceElement1 = plan1.alignments[0].elements[0]
        val sourceElement2 = plan2.alignments[0].elements[1]
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val referenceLineId =
            mainDraftContext
                .saveReferenceLine(
                    referenceLineAndGeometry(trackNumberId, segment(Point(0.0, 0.0), Point(0.0, 1000.0)))
                )
                .id

        val points1 = (0..500).map { Point(0.0, it.toDouble()) }
        val points2 = (500..1000).map { Point(0.0, it.toDouble()) }
        val (trackId, oid) =
            mainDraftContext.saveWithOid(
                locationTrack(trackNumberId),
                trackGeometryOfSegments(
                    segment(toSegmentPoints(to3DMPoints(points1)), sourceId = sourceElement1.id, sourceStartM = 0.0),
                    segment(toSegmentPoints(to3DMPoints(points2)), sourceId = sourceElement2.id, sourceStartM = 500.0),
                ),
            )
        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        // Plan1 curve at station 490 → track address ~490. Plan2 curve at station 510 → track address ~510.
        // Both curves describe overlapping profile near the connection point.
        val pviPoints = api.locationTrackProfile.get(oid).osoitevali.taitepisteet
        val pviAddresses = pviPoints.map { it.taite.sijainti?.rataosoite }
        assertEquals(
            listOf(expectedAddress(490.0), expectedAddress(510.0)),
            pviAddresses,
            "Expected PVI points from both plans near the connection point",
        )
        val overlapRemarks = pviPoints.flatMap { it.huomiot }.filter { it.koodi == "kaltevuusjakso_limittain" }
        assertTrue(overlapRemarks.isNotEmpty(), "Expected kaltevuusjakso_limittain remarks for overlapping profiles")
        overlapRemarks.forEach { remark -> assertTrue(remark.selite.isNotBlank(), "Remark selite should be non-blank") }
    }

    @Test
    fun `suunnitelman_korkeusasema maps TOP_OF_SLEEPER to Korkeusviiva and TOP_OF_RAIL to Kiskon selkä`() {
        val sleeperPlan =
            insertPlan(
                listOf(profileAlignment()),
                elevationMeasurementMethod = ElevationMeasurementMethod.TOP_OF_SLEEPER,
            )
        val sleeperElements = sleeperPlan.alignments[0].elements
        val (sleeperTrackNumberId, sleeperRefLineId) = insertTrackNumberWithReferenceLine(sleeperElements)
        val (sleeperTrackId, sleeperOid) =
            mainDraftContext.saveWithOid(locationTrack(sleeperTrackNumberId), trackGeometryOfElements(sleeperElements))

        val railPlan =
            insertPlan(listOf(profileAlignment()), elevationMeasurementMethod = ElevationMeasurementMethod.TOP_OF_RAIL)
        val railElements = railPlan.alignments[0].elements
        val (railTrackNumberId, railRefLineId) = insertTrackNumberWithReferenceLine(railElements)
        val (railTrackId, railOid) =
            mainDraftContext.saveWithOid(locationTrack(railTrackNumberId), trackGeometryOfElements(railElements))

        extTestDataService.publishInMain(
            trackNumbers = listOf(sleeperTrackNumberId, railTrackNumberId),
            referenceLines = listOf(sleeperRefLineId, railRefLineId),
            locationTracks = listOf(sleeperTrackId, railTrackId),
        )

        val sleeperPviPoint = api.locationTrackProfile.get(sleeperOid).osoitevali.taitepisteet.single()
        assertEquals(
            "Korkeusviiva",
            sleeperPviPoint.suunnitelman_korkeusasema,
            "TOP_OF_SLEEPER should map to 'Korkeusviiva'",
        )

        val railPviPoint = api.locationTrackProfile.get(railOid).osoitevali.taitepisteet.single()
        assertEquals("Kiskon selkä", railPviPoint.suunnitelman_korkeusasema, "TOP_OF_RAIL should map to 'Kiskon selkä'")
    }

    @Test
    fun `Location track with no vertical geometry returns 200 with no PVI points`() {
        val refLineSegment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val referenceLineId =
            mainDraftContext.saveReferenceLine(referenceLineAndGeometry(trackNumberId, refLineSegment)).id
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfSegments(refLineSegment))
        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        val addressRange = api.locationTrackProfile.get(oid).osoitevali
        assertNotNull(addressRange.alku, "Address range start should be present for existing track")
        assertNotNull(addressRange.loppu, "Address range end should be present for existing track")
        assertEquals(0, addressRange.taitepisteet.size, "Expected no PVI points when no profile exists")
    }

    @Test
    fun `Changes endpoint returns modified PVI points when profile data changes between versions`() {
        val trackStart = Point(0.0, 0.0)
        val trackEnd = Point(0.0, 700.0)
        val expectedOldPviAddress = "0000+0500.000"
        val expectedNewPviAddress = "0000+0550.000"

        // Publication 1: track linked to plan with original profile
        val plan1 =
            insertPlan(
                listOf(
                    profileAlignment(
                        start = trackStart,
                        end = trackEnd,
                        profileElements =
                            listOf(
                                VIPoint(PlanElementName("start"), Point(0.0, 50.0)),
                                VICircularCurve(
                                    PlanElementName("curve"),
                                    Point(500.0, 50.0),
                                    BigDecimal(20000),
                                    BigDecimal(155),
                                ),
                                VIPoint(PlanElementName("end"), Point(600.0, 51.0)),
                            ),
                    )
                ),
                fileName = FileName("profile_v1.xml"),
            )
        val elements1 = plan1.alignments[0].elements
        val (trackNumberId, referenceLineId) = insertTrackNumberWithReferenceLine(elements1)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements1))
        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        // Publication 2: re-link the same track to a different plan with modified profile
        val plan2 =
            insertPlan(
                listOf(
                    profileAlignment(
                        start = trackStart,
                        end = trackEnd,
                        profileElements =
                            listOf(
                                VIPoint(PlanElementName("start"), Point(0.0, 55.0)),
                                VICircularCurve(
                                    PlanElementName("curve"),
                                    Point(550.0, 55.0),
                                    BigDecimal(15000),
                                    BigDecimal(155),
                                ),
                                VIPoint(PlanElementName("end"), Point(600.0, 56.0)),
                            ),
                    )
                ),
                fileName = FileName("profile_v2.xml"),
            )
        val elements2 = plan2.alignments[0].elements
        val (track, _) = mainDraftContext.fetchLocationTrackWithGeometry(trackId)!!
        mainDraftContext.save(track, trackGeometryOfElements(elements2))
        val publication2 = extTestDataService.publishInMain(locationTracks = listOf(trackId))

        // Changes endpoint should report the modification
        val response = api.locationTrackProfile.getModifiedBetween(oid, publication1.uuid, publication2.uuid)
        assertProfileChangesTopLevel(response, oid, publication1.uuid, publication2.uuid)
        assertProfileInterval(response.osoitevalit.single(), expectedOldPviAddress, expectedNewPviAddress, 1)
    }

    private fun assertProfileResponseTopLevel(
        response: ExtTestLocationTrackProfileResponseV1,
        oid: Oid<LocationTrack>,
        version: Uuid<Publication>,
    ) {
        assertEquals(oid.toString(), response.sijaintiraide_oid)
        assertEquals(LAYOUT_SRID.toString(), response.koordinaatisto)
        assertEquals(version.toString(), response.rataverkon_versio)
    }

    private fun assertProfileChangesTopLevel(
        response: ExtTestModifiedLocationTrackProfileResponseV1,
        oid: Oid<LocationTrack>,
        fromVersion: Uuid<Publication>,
        toVersion: Uuid<Publication>,
    ) {
        assertEquals(oid.toString(), response.sijaintiraide_oid)
        assertEquals(LAYOUT_SRID.toString(), response.koordinaatisto)
        assertEquals(fromVersion.toString(), response.alkuversio)
        assertEquals(toVersion.toString(), response.loppuversio)
    }

    private fun assertProfileInterval(
        interval: ExtTestProfileAddressRangeV1,
        expectedStart: String?,
        expectedEnd: String?,
        expectedPviPointCount: Int,
    ) {
        assertEquals(expectedStart, interval.alku, "Interval start address")
        assertEquals(expectedEnd, interval.loppu, "Interval end address")
        assertEquals(expectedPviPointCount, interval.taitepisteet.size, "PVI point count in interval")
    }

    private fun assertCurvedSectionEndpoint(
        endpoint: ExtTestProfileCurvedSectionEndpointV1,
        expectedHeight: Double,
        expectedHeightN2000: Double?,
        expectedGradient: Double,
        expectedAddress: String,
        expectedLocation: Point,
        label: String,
    ) {
        assertEquals(roundTo3Decimals(expectedHeight).toString(), endpoint.korkeus_alkuperainen, "$label height")
        assertEquals(
            expectedHeightN2000?.let(::roundTo3Decimals)?.toString(),
            endpoint.korkeus_n2000,
            "$label N2000 height",
        )
        assertEquals(roundTo6Decimals(expectedGradient).toString(), endpoint.kaltevuus, "$label gradient")
        assertEquals(expectedAddress, endpoint.sijainti?.rataosoite, "$label track address")
        assertEquals(expectedLocation.x, endpoint.sijainti!!.x, LAYOUT_COORDINATE_DELTA, "$label X coordinate")
        assertEquals(expectedLocation.y, endpoint.sijainti.y, LAYOUT_COORDINATE_DELTA, "$label Y coordinate")
    }

    private fun assertIntersectionPoint(
        point: ExtTestProfileIntersectionPointV1,
        expectedHeight: Double,
        expectedHeightN2000: Double?,
        expectedAddress: String,
        expectedLocation: Point,
        label: String,
    ) {
        assertEquals(roundTo3Decimals(expectedHeight).toString(), point.korkeus_alkuperainen, "$label height")
        assertEquals(
            expectedHeightN2000?.let(::roundTo3Decimals)?.toString(),
            point.korkeus_n2000,
            "$label N2000 height",
        )
        assertEquals(expectedAddress, point.sijainti?.rataosoite, "$label track address")
        assertEquals(expectedLocation.x, point.sijainti?.x!!.toDouble(), LAYOUT_COORDINATE_DELTA, "$label X coordinate")
        assertEquals(expectedLocation.y, point.sijainti?.y!!.toDouble(), LAYOUT_COORDINATE_DELTA, "$label Y coordinate")
    }

    private fun assertLinearSection(
        section: ExtTestProfileLinearSectionV1,
        expectedLength: BigDecimal?,
        expectedLinearPart: BigDecimal?,
        label: String,
    ) {
        assertEquals(expectedLength?.toString(), section.pituus, "$label length")
        assertEquals(expectedLinearPart?.toString(), section.suora_osa, "$label linear part")
    }

    private fun assertStationValues(
        stationValues: ExtTestProfileStationValuesV1,
        expectedStart: Double?,
        expectedIntersection: Double?,
        expectedEnd: Double?,
    ) {
        assertEquals(expectedStart?.let(::roundTo3Decimals)?.toString(), stationValues.alku, "Station value start")
        assertEquals(
            expectedIntersection?.let(::roundTo3Decimals)?.toString(),
            stationValues.taite,
            "Station value intersection",
        )
        assertEquals(expectedEnd?.let(::roundTo3Decimals)?.toString(), stationValues.loppu, "Station value end")
    }

    private fun insertTrackNumberWithReferenceLine(
        elements: List<GeometryElement>
    ): Pair<IntId<LayoutTrackNumber>, IntId<ReferenceLine>> {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val referenceLineId =
            mainDraftContext.saveReferenceLine(referenceLineAndGeometryOfElements(trackNumberId, elements)).id
        return Pair(trackNumberId, referenceLineId)
    }

    private fun profileAlignment(
        start: Point = Point(0.0, 0.0),
        end: Point = Point(0.0, 1000.0),
        profileElements: List<VerticalIntersection> =
            listOf(
                VIPoint(PlanElementName("start"), Point(0.0, 100.0)),
                VICircularCurve(PlanElementName("curve"), Point(500.0, 100.0), BigDecimal(20000), BigDecimal(155)),
                VIPoint(PlanElementName("end"), Point(1000.0, 101.0)),
            ),
    ): GeometryAlignment =
        geometryAlignment(
            elements = listOf(line(start, end)),
            profile = GeometryProfile(PlanElementName("profile"), profileElements),
        )

    private fun insertPlan(
        alignments: List<GeometryAlignment>,
        verticalCoordinateSystem: VerticalCoordinateSystem = VerticalCoordinateSystem.N2000,
        elevationMeasurementMethod: ElevationMeasurementMethod = ElevationMeasurementMethod.TOP_OF_SLEEPER,
        fileName: FileName = FileName("test_profile.xml"),
    ): GeometryPlan =
        testDBService.savePlan(
            plan(
                srid = LAYOUT_SRID,
                verticalCoordinateSystem = verticalCoordinateSystem,
                elevationMeasurementMethod = elevationMeasurementMethod,
                fileName = fileName,
                alignments = alignments,
            )
        )

    private fun expectedAddress(stationMeters: Double): String =
        TrackMeter(KmNumber.ZERO, BigDecimal(roundTo3Decimals(stationMeters).toString())).formatFixedDecimals(3)
}
