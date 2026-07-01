package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geography.FIN_GK25_SRID
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.kmPosts
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LAYOUT_COORDINATE_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometryOfElements
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.tracklayout.to3DMPoints
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfElements
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
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

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtLocationTrackElementListingIT
@Autowired
constructor(mockMvc: MockMvc) : DBTestBase() {

    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @Test
    fun `Basic linked track returns correct element structure`() {
        val start = Point(0.0, 0.0)
        val end = Point(0.0, 500.0)
        val plan = insertPlan(listOf(line(start, end)))
        val elements = plan.alignments[0].elements
        val trackNumberId = insertTrackNumberWithReferenceLine(elements)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements))
        val publication = testDBService.publish(trackNumbers = listOf(trackNumberId), locationTracks = listOf(trackId))

        val response = api.locationTrackElementListing.get(oid)

        assertEquals(publication.uuid.toString(), response.rataverkon_versio)
        assertEquals(oid.toString(), response.sijaintiraide_oid)
        assertEquals(LAYOUT_SRID.toString(), response.koordinaatisto)

        val interval = response.osoitevalit.single()
        assertEquals("0000+0000.000", interval.alku)
        assertEquals("0000+0500.000", interval.loppu)

        val element = interval.geometriaelementit.single()
        assertEquals("suora", element.tyyppi)
        assertEquals("0000+0000.000", element.sijainti_alku.rataosoite)
        assertEquals("0000+0500.000", element.sijainti_loppu.rataosoite)
        assertTrue(element.pituus_m > 0.0, "Element length should be positive")
        assertNotNull(element.suunnitelma, "Linked element should have plan reference")
        assertNotNull(element.suuntakulma_gooni)
        assertTrue(element.huomiot.isEmpty(), "No notes expected for a simple linked element")
    }

    @Test
    fun `Returns 204 when track does not exist at requested version`() {
        val (trackNumberId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
            )
        // Publication 1: no track yet
        val publication1 = testDBService.publish(trackNumbers = listOf(trackNumberId))

        // Publication 2: track added
        val (trackId, oid) =
            mainDraftContext.saveWithOid(
                locationTrack(trackNumberId),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(0.0, 100.0))),
            )
        testDBService.publish(locationTracks = listOf(trackId))

        api.locationTrackElementListing.assertDoesntExistAtVersion(oid, publication1.uuid)
    }

    @Test
    fun `Plan coordinates in non-layout SRID are transformed to layout SRID in response`() {
        val gk25Start = Point(25502020.0, 6974470.0)
        val gk25End = Point(gk25Start.x, gk25Start.y + 500.0)
        val plan = insertPlan(listOf(line(gk25Start, gk25End)), srid = FIN_GK25_SRID)
        val elements = plan.alignments[0].elements
        val trackNumberId = insertTrackNumberWithReferenceLine(elements, FIN_GK25_SRID)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(
                locationTrack(trackNumberId),
                trackGeometryOfElements(elements, FIN_GK25_SRID),
            )
        testDBService.publish(trackNumbers = listOf(trackNumberId), locationTracks = listOf(trackId))

        val response = api.locationTrackElementListing.get(oid)
        assertEquals(LAYOUT_SRID.toString(), response.koordinaatisto)

        val element = response.osoitevalit.single().geometriaelementit.single()

        val expectedStart = transformNonKKJCoordinate(FIN_GK25_SRID, LAYOUT_SRID, gk25Start)
        assertEquals(expectedStart.x, element.sijainti_alku.x, LAYOUT_COORDINATE_DELTA)
        assertEquals(expectedStart.y, element.sijainti_alku.y, LAYOUT_COORDINATE_DELTA)

        val expectedEnd = transformNonKKJCoordinate(FIN_GK25_SRID, LAYOUT_SRID, gk25End)
        assertEquals(expectedEnd.x, element.sijainti_loppu.x, LAYOUT_COORDINATE_DELTA)
        assertEquals(expectedEnd.y, element.sijainti_loppu.y, LAYOUT_COORDINATE_DELTA)

        // suunnitelma carries the original plan-space coordinates untransformed
        element.suunnitelma.also { planRef ->
            assertNotNull(planRef, "Linked element should have plan reference")
            assertEquals(FIN_GK25_SRID.toString(), planRef!!.koordinaatisto)
            assertEquals(gk25Start.x, planRef.sijainti_alku.x, LAYOUT_COORDINATE_DELTA)
            assertEquals(gk25Start.y, planRef.sijainti_alku.y, LAYOUT_COORDINATE_DELTA)
            assertEquals(gk25End.x, planRef.sijainti_loppu.x, LAYOUT_COORDINATE_DELTA)
            assertEquals(gk25End.y, planRef.sijainti_loppu.y, LAYOUT_COORDINATE_DELTA)
        }
    }

    @Test
    fun `Unlinked segment produces ei_elementtia element with null suunnitelma`() {
        val start = Point(0.0, 0.0)
        val end = Point(0.0, 200.0)
        val (trackNumberId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometry(segment(start, end)),
            )
        val (trackId, oid) =
            mainDraftContext.saveWithOid(
                locationTrack(trackNumberId),
                trackGeometryOfSegments(segment(start, end)),
            )
        testDBService.publish(trackNumbers = listOf(trackNumberId), locationTracks = listOf(trackId))

        val element = api.locationTrackElementListing.get(oid).osoitevalit.single().geometriaelementit.single()

        assertEquals("ei_elementtia", element.tyyppi)
        assertNull(element.suunnitelma, "Missing section should have null suunnitelma")
        assertNull(element.kaarresade, "Missing section should have null kaarresade")
    }

    @Test
    fun `Partial element produces sisaltaa_vain_osan_elementista huomio`() {
        // Element is 200m; segment covers only 100m — more than 1m delta → isPartial = true
        val elementStart = Point(0.0, 0.0)
        val elementEnd = Point(0.0, 200.0)
        val plan = insertPlan(listOf(line(elementStart, elementEnd)))
        val element = plan.alignments[0].elements.single()

        val (trackNumberId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometry(segment(elementStart, elementEnd)),
            )
        val segmentPoints = toSegmentPoints(to3DMPoints((0..100).map { Point(0.0, it.toDouble()) }))
        val (trackId, oid) =
            mainDraftContext.saveWithOid(
                locationTrack(trackNumberId),
                trackGeometryOfSegments(
                    segment(segmentPoints, sourceId = element.id, sourceStartM = 0.0),
                ),
            )
        testDBService.publish(trackNumbers = listOf(trackNumberId), locationTracks = listOf(trackId))

        val responseElement = api.locationTrackElementListing.get(oid).osoitevalit.single().geometriaelementit.single()

        val partialNote = responseElement.huomiot.find { it.koodi == "sisaltaa_vain_osan_elementista" }
        assertNotNull(partialNote, "Expected sisaltaa_vain_osan_elementista note for partial element")
        assertEquals("Raide sisältää vain osan geometriaelementistä", partialNote!!.selite)
    }

    @Test
    fun `Versioning returns correct data at each publication`() {
        val start = Point(0.0, 0.0)
        val end = Point(0.0, 300.0)

        val plan1 = insertPlan(listOf(line(start, end)), fileName = FileName("elem_v1.xml"))
        val elements1 = plan1.alignments[0].elements
        val trackNumberId = insertTrackNumberWithReferenceLine(elements1)
        val (trackId, oid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfElements(elements1))
        val publication1 = testDBService.publish(trackNumbers = listOf(trackNumberId), locationTracks = listOf(trackId))

        // Re-link track to a different plan (different element id → different element listing)
        val plan2 = insertPlan(listOf(line(start, end)), fileName = FileName("elem_v2.xml"))
        val elements2 = plan2.alignments[0].elements
        val (track, _) = mainDraftContext.fetchLocationTrackWithGeometry(trackId)!!
        mainDraftContext.save(track, trackGeometryOfElements(elements2))
        val publication2 = testDBService.publish(locationTracks = listOf(trackId))

        val v1Response = api.locationTrackElementListing.getAtVersion(oid, publication1.uuid)
        val v2Response = api.locationTrackElementListing.getAtVersion(oid, publication2.uuid)

        assertEquals(publication1.uuid.toString(), v1Response.rataverkon_versio)
        assertEquals(publication2.uuid.toString(), v2Response.rataverkon_versio)

        // Both versions have a single linked element; plan reference differs between them
        val v1PlanRef = v1Response.osoitevalit.single().geometriaelementit.single().suunnitelma
        val v2PlanRef = v2Response.osoitevalit.single().geometriaelementit.single().suunnitelma
        assertNotNull(v1PlanRef)
        assertNotNull(v2PlanRef)
    }

    private fun insertTrackNumberWithReferenceLine(
        elements: List<GeometryElement>,
        planSrid: Srid = LAYOUT_SRID,
    ): IntId<LayoutTrackNumber> =
        mainDraftContext
            .saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometryOfElements(elements, planSrid),
            )
            .first

    private fun insertPlan(
        elements: List<GeometryElement>,
        srid: Srid = LAYOUT_SRID,
        fileName: FileName = FileName("test_elements.xml"),
    ): GeometryPlan =
        testDBService.savePlan(
            plan(
                srid = srid,
                alignments = listOf(geometryAlignment(elements = elements)),
                kmPosts = if (srid == LAYOUT_SRID) kmPosts(srid) else emptyList(),
                fileName = fileName,
            )
        )
}
