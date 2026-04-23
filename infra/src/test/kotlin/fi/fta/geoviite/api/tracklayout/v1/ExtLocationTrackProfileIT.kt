package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryProfile
import fi.fta.geoviite.infra.geometry.VICircularCurve
import fi.fta.geoviite.infra.geometry.VIPoint
import fi.fta.geoviite.infra.geometry.VerticalIntersection
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.lineFromOrigin
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
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
        val plan = insertPlan(listOf(profileAlignment()))
        val elements = plan.alignments[0].elements
        val (trackNumberId, referenceLineId) = insertTrackNumberWithReferenceLine(elements)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements))
        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        val publication2 = extTestDataService.publishInMain()

        val responseLatest = api.locationTrackProfile.get(oid)
        assertProfileResponseTopLevel(responseLatest, oid, publication2.uuid)

        val responseAtV1 = api.locationTrackProfile.getAtVersion(oid, publication1.uuid)
        assertProfileResponseTopLevel(responseAtV1, oid, publication1.uuid)

        val responseAtV2 = api.locationTrackProfile.getAtVersion(oid, publication2.uuid)
        assertProfileResponseTopLevel(responseAtV2, oid, publication2.uuid)
    }

    @Test
    fun `Response contains correctly structured break points with expected values`() {
        val curveRadius = BigDecimal(20000)
        val alignment =
            profileAlignment(
                profileElements =
                    listOf(
                        VIPoint(PlanElementName("start"), Point(0.0, 50.0)),
                        VICircularCurve(PlanElementName("curve"), Point(500.0, 50.0), curveRadius, BigDecimal(155)),
                        VIPoint(PlanElementName("end"), Point(600.0, 51.0)),
                    )
            )
        val plan = insertPlan(listOf(alignment))
        val elements = plan.alignments[0].elements
        val (trackNumberId, referenceLineId) = insertTrackNumberWithReferenceLine(elements)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements))
        val publication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        val response = api.locationTrackProfile.get(oid)
        assertProfileResponseTopLevel(response, oid, publication.uuid)
        assertEquals(1, response.osoitevalit.size, "Expected exactly one address range")

        val breakPoints = response.osoitevalit[0].taitepisteet
        assertEquals(1, breakPoints.size, "Expected exactly one break point for one curve")

        val breakPoint = breakPoints[0]
        assertEquals(
            curveRadius.toDouble(),
            breakPoint.pyoristyssade.toDouble(),
            "pyoristyssade should match curve radius",
        )
        assertEquals(
            50.0,
            breakPoint.taite.korkeus_alkuperäinen.toDouble(),
            "Intersection height should match profile point Y",
        )
        assertEquals(0, breakPoint.huomiot.size, "Single plan should have no remarks")
    }

    @Test
    fun `Start and end points include kaltevuus, intersection point does not`() {
        val alignment =
            profileAlignment(
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
                    )
            )
        val plan = insertPlan(listOf(alignment))
        val elements = plan.alignments[0].elements
        val (trackNumberId, referenceLineId) = insertTrackNumberWithReferenceLine(elements)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements))
        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        val response = api.locationTrackProfile.get(oid)
        assertEquals(1, response.osoitevalit.size)
        assertEquals(1, response.osoitevalit[0].taitepisteet.size)
        val breakPoint = response.osoitevalit[0].taitepisteet[0]

        // Curved section endpoints (pyoristyksen_alku/loppu) have kaltevuus field
        // Intersection point (taite) does not — enforced by type ExtTestProfileIntersectionPointV1 having no kaltevuus
        assertNotNull(breakPoint.pyoristyksen_alku.kaltevuus, "Curved section start should have kaltevuus")
        assertNotNull(breakPoint.pyoristyksen_loppu.kaltevuus, "Curved section end should have kaltevuus")
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

        val response = api.locationTrackProfile.get(oid)
        assertEquals(1, response.osoitevalit.size)
        assertEquals(1, response.osoitevalit[0].taitepisteet.size)
        val breakPoint = response.osoitevalit[0].taitepisteet[0]

        assertEquals(
            breakPoint.pyoristyksen_alku.korkeus_alkuperäinen.toDouble(),
            breakPoint.pyoristyksen_alku.korkeus_n2000?.toDouble(),
            "N2000 height should equal original for N2000 source data (start)",
        )
        assertEquals(
            breakPoint.taite.korkeus_alkuperäinen.toDouble(),
            breakPoint.taite.korkeus_n2000?.toDouble(),
            "N2000 height should equal original for N2000 source data (intersection)",
        )
        assertEquals(
            breakPoint.pyoristyksen_loppu.korkeus_alkuperäinen.toDouble(),
            breakPoint.pyoristyksen_loppu.korkeus_n2000?.toDouble(),
            "N2000 height should equal original for N2000 source data (end)",
        )
    }

    @Test
    fun `N43 source data returns korkeus_n2000 as null`() {
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

        val response = api.locationTrackProfile.get(oid)
        assertEquals(1, response.osoitevalit.size)
        assertEquals(1, response.osoitevalit[0].taitepisteet.size)
        val breakPoint = response.osoitevalit[0].taitepisteet[0]

        assertNull(
            breakPoint.pyoristyksen_alku.korkeus_n2000,
            "N2000 height should be null for N43 source data (start)",
        )
        assertNull(breakPoint.taite.korkeus_n2000, "N2000 height should be null for N43 source data (intersection)")
        assertNull(breakPoint.pyoristyksen_loppu.korkeus_n2000, "N2000 height should be null for N43 source data (end)")
    }

    @Test
    fun `Consecutive break points form a single address range`() {
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

        val response = api.locationTrackProfile.get(oid)
        assertEquals(1, response.osoitevalit.size, "Expected a single address range for consecutive break points")
        assertEquals(1, response.osoitevalit[0].taitepisteet.size, "Expected one break point for one curve")
    }

    @Test
    fun `Gap in break points creates separate address ranges`() {
        val plan1 = insertPlan(listOf(profileAlignment()), fileName = FileName("profile_gap_1.xml"))
        val plan2 = insertPlan(listOf(profileAlignment()), fileName = FileName("profile_gap_2.xml"))
        val sourceElement1 = plan1.alignments[0].elements[0]
        val sourceElement2 = plan2.alignments[0].elements[0]
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val referenceLineId =
            mainDraftContext
                .saveReferenceLine(referenceLineAndGeometry(trackNumberId, segment(Point(0.0, 0.0), Point(0.0, 200.0))))
                .id

        // Segments with a gap (0..80 linked, 80..120 not linked, 120..200 linked)
        val points1 = (0..80).map { Point(0.0, it.toDouble()) }
        val pointsGap = (80..120).map { Point(0.0, it.toDouble()) }
        val points2 = (120..200).map { Point(0.0, it.toDouble()) }
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

        val response = api.locationTrackProfile.get(oid)
        assertEquals(2, response.osoitevalit.size, "Expected two address ranges separated by the unlinked gap")
    }

    @Test
    fun `overlapsAnother produces kaltevuusjakso_limittain remark`() {
        val plan1 = insertPlan(listOf(profileAlignment()), fileName = FileName("overlap_1.xml"))
        val plan2 = insertPlan(listOf(profileAlignment()), fileName = FileName("overlap_2.xml"))
        val sourceElement1 = plan1.alignments[0].elements[0]
        val sourceElement2 = plan2.alignments[0].elements[0]
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val referenceLineId =
            mainDraftContext
                .saveReferenceLine(referenceLineAndGeometry(trackNumberId, segment(Point(0.0, 0.0), Point(0.0, 100.0))))
                .id

        // Two segments overlapping (0..70 and 30..100)
        val points1 = (0..70).map { Point(0.0, it.toDouble()) }
        val points2 = (30..100).map { Point(0.0, it.toDouble()) }
        val (trackId, oid) =
            mainDraftContext.saveWithOid(
                locationTrack(trackNumberId),
                trackGeometryOfSegments(
                    segment(toSegmentPoints(to3DMPoints(points1)), sourceId = sourceElement1.id, sourceStartM = 0.0),
                    segment(toSegmentPoints(to3DMPoints(points2)), sourceId = sourceElement2.id, sourceStartM = 0.0),
                ),
            )
        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        val response = api.locationTrackProfile.get(oid)
        val allRemarks = response.osoitevalit.flatMap { range -> range.taitepisteet.flatMap { it.huomiot } }
        val overlapRemarks = allRemarks.filter { it.koodi == "kaltevuusjakso_limittain" }
        assertTrue(overlapRemarks.isNotEmpty(), "Expected kaltevuusjakso_limittain remarks for overlapping profiles")
        overlapRemarks.forEach { remark -> assertTrue(remark.selite.isNotBlank(), "Remark selite should be non-blank") }
    }

    @Test
    fun `No overlap produces empty huomiot`() {
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

        val response = api.locationTrackProfile.get(oid)
        assertEquals(1, response.osoitevalit.size, "Expected a single address range with no overlap")
        val allRemarks = response.osoitevalit.flatMap { range -> range.taitepisteet.flatMap { it.huomiot } }
        assertEquals(0, allRemarks.size, "Expected no remarks when there is no overlap")
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

        val sleeperResponse = api.locationTrackProfile.get(sleeperOid)
        assertEquals(1, sleeperResponse.osoitevalit.size)
        assertEquals(1, sleeperResponse.osoitevalit[0].taitepisteet.size)
        assertEquals(
            "Korkeusviiva",
            sleeperResponse.osoitevalit[0].taitepisteet[0].suunnitelman_korkeusasema,
            "TOP_OF_SLEEPER should map to 'Korkeusviiva'",
        )

        val railResponse = api.locationTrackProfile.get(railOid)
        assertEquals(1, railResponse.osoitevalit.size)
        assertEquals(1, railResponse.osoitevalit[0].taitepisteet.size)
        assertEquals(
            "Kiskon selkä",
            railResponse.osoitevalit[0].taitepisteet[0].suunnitelman_korkeusasema,
            "TOP_OF_RAIL should map to 'Kiskon selkä'",
        )
    }

    @Test
    fun `Location track with no vertical geometry returns 204`() {
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

        api.locationTrackProfile.assertDoesntExist(oid)
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

    private fun insertTrackNumberWithReferenceLine(
        elements: List<GeometryElement>
    ): Pair<IntId<LayoutTrackNumber>, IntId<ReferenceLine>> {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val referenceLineId =
            mainDraftContext.saveReferenceLine(referenceLineAndGeometryOfElements(trackNumberId, elements)).id
        return Pair(trackNumberId, referenceLineId)
    }

    private fun profileAlignment(
        profileElements: List<VerticalIntersection> =
            listOf(
                VIPoint(PlanElementName("start"), Point(0.0, 50.0)),
                VICircularCurve(PlanElementName("curve"), Point(500.0, 50.0), BigDecimal(20000), BigDecimal(155)),
                VIPoint(PlanElementName("end"), Point(600.0, 51.0)),
            )
    ): GeometryAlignment =
        geometryAlignment(
            elements = listOf(lineFromOrigin(1.0)),
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
}
