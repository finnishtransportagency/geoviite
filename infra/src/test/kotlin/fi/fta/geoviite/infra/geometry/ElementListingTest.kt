package fi.fta.geoviite.infra.geometry

import ElementListing
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.RotationDirection.CW
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.KKJ0
import fi.fta.geoviite.infra.geometry.GeometryElementType.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.tracklayout.GeometrySource.PLAN
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Test
import toElementListing
import java.math.BigDecimal
import kotlin.test.assertEquals

private val allTypes = GeometryElementType.values().toList()
class ElementListingTest {

    @Test
    fun `Basic info is filled from LocationTrack & GeometryPlanHeader`() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val locationTrack = locationTrack(trackNumberId)
        val alignment = geometryAlignment(
            trackNumberId = trackNumberId,
            elements = listOf(minimalLine(), minimalCurve(), minimalClothoid()),
            name = "TSTTrack002",
        )
        val planHeader = planHeader(
            id = IntId(2),
            trackNumberId = trackNumberId,
            fileName = FileName("test-file 002.xml"),
            srid = KKJ0,
            coordinateSystemName = CoordinateSystemName("KKJ test-name"),
        )
        val listing = toElementListing(
            null,
            locationTrack,
            listOf(planHeader to alignment),
            alignment.elements.map(GeometryElement::id),
            GeometryElementType.values().toList(),
        )
        listing.forEach { l ->
            assertEquals(IntId(2), l.planId)
            assertEquals(FileName("test-file 002.xml"), l.fileName)
            assertEquals(KKJ0, l.coordinateSystemSrid)
            assertEquals(CoordinateSystemName("KKJ test-name"), l.coordinateSystemName)
            assertEquals(trackNumberId, l.trackNumberId)
            assertEquals(alignment.id, l.alignmentId)
            assertEquals(AlignmentName("TSTTrack002"), l.alignmentName)
        }
        assertEquals(listOf(LINE, CURVE, CLOTHOID), listing.map { l -> l.elementType })
        assertEquals(alignment.elements.map(GeometryElement::id), listing.map(ElementListing::elementId))
        assertEquals(
            alignment.elements.map { e -> roundTo3Decimals(e.calculatedLength) },
            listing.map { l -> l.lengthMeters },
        )
        assertEquals(alignment.elements.map { e -> e.start }, listing.map { l -> l.start.coordinate })
        assertEquals(alignment.elements.map { e -> e.end }, listing.map { l -> l.end.coordinate })
    }

    @Test
    fun `Basic info is filled from GeometryPlan`() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val alignment = geometryAlignment(
            trackNumberId = trackNumberId,
            elements = listOf(minimalLine(), minimalCurve(), minimalClothoid()),
            name = "TSTTrack001",
        )
        val plan = plan(
            trackNumberId = trackNumberId,
            trackNumberDesc = PlanElementName("test track number"),
            alignments = listOf(alignment),
            fileName = FileName("test-file 001.xml"),
            srid = KKJ0,
            coordinateSystemName = CoordinateSystemName("KKJ testname"),
        )
        val listing = toElementListing(null, plan, GeometryElementType.values().toList())
        listing.forEach { l ->
            assertEquals(plan.id, l.planId)
            assertEquals(FileName("test-file 001.xml"), l.fileName)
            assertEquals(KKJ0, l.coordinateSystemSrid)
            assertEquals(CoordinateSystemName("KKJ testname"), l.coordinateSystemName)
            assertEquals(trackNumberId, l.trackNumberId)
            assertEquals(PlanElementName("test track number"), l.trackNumberDescription)
            assertEquals(alignment.id, l.alignmentId)
            assertEquals(AlignmentName("TSTTrack001"), l.alignmentName)
        }
        assertEquals(listOf(LINE, CURVE, CLOTHOID), listing.map { l -> l.elementType })
        assertEquals(alignment.elements.map(GeometryElement::id), listing.map(ElementListing::elementId))
        assertEquals(
            alignment.elements.map { e -> roundTo3Decimals(e.calculatedLength) },
            listing.map { l -> l.lengthMeters },
        )
        assertEquals(alignment.elements.map { e -> e.start }, listing.map { l -> l.start.coordinate })
        assertEquals(alignment.elements.map { e -> e.end }, listing.map { l -> l.end.coordinate })
    }

    @Test
    fun `Start and end location includes calculated values`() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val trackNumber = trackNumber(id = trackNumberId)
        val (referenceLine, alignment) = referenceLineAndAlignment(
            trackNumberId = trackNumberId,
            segments = listOf(segment(Point(0.0, 0.0), Point(50.0, 0.0))),
            startAddress = TrackMeter(KmNumber(1), 100),
        )
        val geocodingContext = GeocodingContext.create(trackNumber, referenceLine, alignment, listOf())
        val clothoid = minimalClothoid(
            start = Point(10.0, 10.0),
            end = Point(20.0, 20.0),
            pi = Point(18.0, 19.0),
            rotation = CW,
        )
        val cant = linearCant(0.0, clothoid.calculatedLength, 0.001, 0.005)
        val plan = plan(
            trackNumberId = IntId(1),
            alignments = listOf(geometryAlignment(elements = listOf(clothoid), cant = cant))
        )
        val elementListing = toElementListing(geocodingContext, plan, values().toList())
        assertEquals(1, elementListing.size)
        val element1 = elementListing[0]

        assertEquals(Point(10.0, 10.0), element1.start.coordinate)
        assertEquals(TrackMeter(KmNumber(1), BigDecimal("110.000")), element1.start.address)
        assertEquals(round(radsToGrads(clothoid.startDirectionRads), 6), element1.start.directionGrads)
        assertEquals(clothoid.radiusStart, element1.start.radiusMeters)
        assertEquals(BigDecimal("0.001000"), element1.start.cant)

        assertEquals(Point(20.0, 20.0), element1.end.coordinate)
        assertEquals(TrackMeter(KmNumber(1), BigDecimal("120.000")), element1.end.address)
        assertEquals(round(radsToGrads(clothoid.endDirectionRads), 6), element1.end.directionGrads)
        assertEquals(clothoid.radiusEnd, element1.end.radiusMeters)
        assertEquals(BigDecimal("0.005000"), element1.end.cant)
    }

    @Test
    fun `Plan element listing is filtered by types`() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val plan = plan(
            trackNumberId = trackNumberId,
            alignments = listOf(
                geometryAlignment(
                    trackNumberId = trackNumberId,
                    elements = listOf(
                        minimalLine(),
                        minimalCurve(),
                        minimalClothoid(),
                        minimalCurve(),
                        minimalClothoid(),
                        minimalLine(),
                    ),
                )
            ),
        )
        assertEquals(
            listOf(LINE, LINE),
            toElementListing(null, plan, listOf(LINE)).map { e -> e.elementType },
        )
        assertEquals(
            listOf(CURVE, CURVE),
            toElementListing(null, plan, listOf(CURVE)).map { e -> e.elementType },
        )
        assertEquals(
            listOf(CLOTHOID, CLOTHOID),
            toElementListing(null, plan, listOf(CLOTHOID)).map { e -> e.elementType },
        )
        assertEquals(
            listOf(LINE, CLOTHOID, CLOTHOID, LINE),
            toElementListing(null, plan, listOf(LINE, CLOTHOID)).map { e -> e.elementType },
        )
        assertEquals(
            listOf(LINE, CURVE, CLOTHOID, CURVE, CLOTHOID, LINE),
            toElementListing(null, plan, listOf(LINE, CURVE, CLOTHOID)).map { e -> e.elementType },
        )
    }

    @Test
    fun `Track element listing is filtered by types`() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val track = locationTrack(trackNumberId)
        val plan = planHeader(trackNumberId = trackNumberId)
        val geometryAlignment = geometryAlignment(
            trackNumberId = trackNumberId,
            elements = listOf(
                minimalLine(),
                minimalCurve(),
                minimalClothoid(),
                minimalCurve(),
                minimalClothoid(),
                minimalLine(),
            ),
        )
        val alignments = listOf(plan to geometryAlignment)
        val allIds = geometryAlignment.elements.map(GeometryElement::id)
        assertEquals(
            listOf(LINE, LINE),
            toElementListing(null, track, alignments, allIds, listOf(LINE)).map { e -> e.elementType },
        )
        assertEquals(
            listOf(CURVE, CURVE),
            toElementListing(null, track, alignments, allIds, listOf(CURVE)).map { e -> e.elementType },
        )
        assertEquals(
            listOf(CLOTHOID, CLOTHOID),
            toElementListing(null, track, alignments, allIds, listOf(CLOTHOID)).map { e -> e.elementType },
        )
        assertEquals(
            listOf(LINE, CLOTHOID, CLOTHOID, LINE),
            toElementListing(null, track, alignments, allIds, listOf(LINE, CLOTHOID)).map { e -> e.elementType },
        )
        assertEquals(
            listOf(LINE, CURVE, CLOTHOID, CURVE, CLOTHOID, LINE),
            toElementListing(null, track, alignments, allIds, listOf(LINE, CURVE, CLOTHOID)).map { e -> e.elementType },
        )
    }

    @Test
    fun `Track element listing is filtered by ids`() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val track = locationTrack(trackNumberId)
        val plan = planHeader(trackNumberId = trackNumberId)
        val alignment = geometryAlignment(
            trackNumberId = trackNumberId,
            elements = listOf(
                minimalLine(),
                minimalCurve(),
                minimalClothoid(),
                minimalCurve(),
                minimalClothoid(),
                minimalLine(),
            ),
        )
        val alignments = listOf(plan to alignment)
        val ids = listOf(alignment.elements[1].id, alignment.elements[4].id)
        assertEquals(
            ids,
            toElementListing(null, track, alignments, ids, allTypes).map(ElementListing::elementId),
        )
    }

    @Test
    fun `Track element listing is filtered by linking and track meters`() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val alignment1 = geometryAlignment(
            id = IntId(1),
            trackNumberId = trackNumberId,
            elements = listOf(
                minimalLine(id = IndexedId(1,1)),
                minimalCurve(id = IndexedId(1,2)),
                minimalClothoid(id = IndexedId(1,3)),
            ),
        )
        val alignment2 = geometryAlignment(
            id = IntId(2),
            trackNumberId = trackNumberId,
            elements = listOf(
                minimalCurve(id = IndexedId(2,1)),
                minimalClothoid(id = IndexedId(2,2)),
                minimalLine(id = IndexedId(2,3)),
            ),
        )
        val (track, layoutAlignment) = locationTrackAndAlignment(trackNumberId,
            segment(Point(10.0, 1.0), Point(20.0, 2.0), source = PLAN, sourceId = alignment1.elements[1].id),
            segment(Point(20.0, 2.0), Point(30.0, 3.0), source = PLAN, sourceId = alignment1.elements[2].id),
            segment(Point(30.0, 3.0), Point(40.0, 4.0), source = PLAN, sourceId = alignment2.elements[0].id),
            segment(Point(40.0, 4.0), Point(50.0, 5.0), source = PLAN, sourceId = alignment2.elements[1].id),
        )
        val planHeader = planHeader(trackNumberId = trackNumberId)
        val alignments = listOf(planHeader to alignment1, planHeader to alignment2)

        val context = geocodingContext(
            referenceLinePoints = listOf(Point(0.0, 0.0), Point(100.0, 0.0)),
            trackNumberId = trackNumberId,
        )
        val addressRange = Range(TrackMeter(KmNumber.ZERO, 25), TrackMeter(KmNumber.ZERO, 35))
        val listing = toElementListing(context, track, layoutAlignment, allTypes, addressRange) { id ->
            alignments.find { a -> a.second.id == id }!!
        }

        // Linked: alignment1 elements 1 & 2 (not 0) + alignment2 elements 0 & 1 (not 2)
        // Address range includes only alignment1 element 2 & alignment2 element 0
        val expectedIds = listOf(alignment1.elements[2].id, alignment2.elements[0].id)
        assertEquals(expectedIds, listing.map(ElementListing::elementId))
    }
}
