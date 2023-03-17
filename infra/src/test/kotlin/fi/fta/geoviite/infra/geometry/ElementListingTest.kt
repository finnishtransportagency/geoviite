package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.RotationDirection.CW
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.geometry.TrackGeometryElementType.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.tracklayout.GeometrySource.PLAN
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

private val allElementTypes = GeometryElementType.values().toList()
private val allTrackElementTypes = TrackGeometryElementType.values().toList()
private val getTransformation = { srid: Srid -> Transformation.nonKKJToETRSTransform(srid, LAYOUT_SRID) }

class ElementListingTest {

    @Test
    fun `Basic info is filled from LocationTrack & GeometryPlanHeader`() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val alignment = geometryAlignment(
            trackNumberId = trackNumberId,
            id = IntId(1),
            elements = createElements(
                1,
                GeometryElementType.LINE,
                GeometryElementType.CURVE,
                GeometryElementType.CLOTHOID,
            ),
            name = "TSTTrack002",
        )
        val planHeader = planHeader(
            source = PlanSource.PAIKANNUSPALVELU,
            id = IntId(2),
            trackNumberId = trackNumberId,
            fileName = FileName("test-file 002.xml"),
            srid = LAYOUT_SRID,
            coordinateSystemName = CoordinateSystemName("KKJ test-name"),
        )
        val (locationTrack, layoutAlignment) = locationTrackAndAlignment(trackNumberId, createSegments(alignment))
        val listing = getListing(locationTrack, layoutAlignment, planHeader, alignment)
        listing.forEach { l ->
            assertEquals(PlanSource.PAIKANNUSPALVELU, l.planSource)
            assertEquals(IntId(2), l.planId)
            assertEquals(FileName("test-file 002.xml"), l.fileName)
            assertEquals(LAYOUT_SRID, l.coordinateSystemSrid)
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
        assertEquals(alignment.elements.map { e -> e.start.round(3) }, listing.map { l -> l.start.coordinate })
        assertEquals(alignment.elements.map { e -> e.end.round(3) }, listing.map { l -> l.end.coordinate })
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
            source = PlanSource.GEOMETRIAPALVELU,
            trackNumberId = trackNumberId,
            trackNumberDesc = PlanElementName("test track number"),
            alignments = listOf(alignment),
            fileName = FileName("test-file 001.xml"),
            srid = LAYOUT_SRID,
            coordinateSystemName = CoordinateSystemName("KKJ testname"),
        )
        val listing = toElementListing(null, getTransformation, plan, allElementTypes)
        listing.forEach { l ->
            assertEquals(PlanSource.GEOMETRIAPALVELU, l.planSource)
            assertEquals(plan.id, l.planId)
            assertEquals(FileName("test-file 001.xml"), l.fileName)
            assertEquals(LAYOUT_SRID, l.coordinateSystemSrid)
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
        assertEquals(alignment.elements.map { e -> e.start.round(3) }, listing.map { l -> l.start.coordinate })
        assertEquals(alignment.elements.map { e -> e.end.round(3) }, listing.map { l -> l.end.coordinate })
    }

    @Test
    fun `Start and end location includes calculated values`() {
        val gk27 = Srid(3881)
        val gk27CoordinateBase = Point(7059000.0, 27480000.0)
        val layoutCoordinateBase = transformNonKKJCoordinate(gk27, LAYOUT_SRID, gk27CoordinateBase)

        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val trackNumber = trackNumber(id = trackNumberId)
        val (referenceLine, alignment) = referenceLineAndAlignment(
            trackNumberId = trackNumberId,
            segments = listOf(segment(
                Point(0.0, 0.0) + layoutCoordinateBase,
                Point(50.0, 0.0) + layoutCoordinateBase,
            )),
            startAddress = TrackMeter(KmNumber(1), 100),
        )
        val geocodingContext = GeocodingContext.create(trackNumber, referenceLine.startAddress, alignment, listOf())
        val clothoid = minimalClothoid(
            start = Point(10.0, 10.0) + gk27CoordinateBase,
            end = Point(20.0, 20.0) + gk27CoordinateBase,
            pi = Point(18.0, 19.0) + gk27CoordinateBase,
            rotation = CW,
        )
        val cant = linearCant(0.0, clothoid.calculatedLength, 0.001, 0.005)
        val plan = plan(
            trackNumberId = IntId(1),
            alignments = listOf(geometryAlignment(elements = listOf(clothoid), cant = cant)),
            srid = gk27,
        )
        val elementListing = toElementListing(geocodingContext, getTransformation, plan, GeometryElementType.values().toList())
        assertEquals(1, elementListing.size)
        val element1 = elementListing[0]

        assertEquals((Point(10.0, 10.0) + gk27CoordinateBase).round(3), element1.start.coordinate)
        // Geocoding is not 1mm accurate so round decimals
        assertEquals(TrackMeter(KmNumber(1), BigDecimal("110.0")), element1.start.address!!.round(1))
        // Direction in grads (geodetic) from start to pi
        assertEquals(BigDecimal("46.259488"), element1.start.directionGrads)
        assertEquals(clothoid.radiusStart, element1.start.radiusMeters)
        assertEquals(BigDecimal("0.001000"), element1.start.cant)

        assertEquals((Point(20.0, 20.0) + gk27CoordinateBase).round(3), element1.end.coordinate)
        // Geocoding is not 1mm accurate so round decimals
        assertEquals(TrackMeter(KmNumber(1), BigDecimal("120.0")), element1.end.address!!.round(1))
        // Direction in grads (geodetic) from pi to end
        assertEquals(BigDecimal("70.483276"), element1.end.directionGrads)
        assertEquals(clothoid.radiusEnd, element1.end.radiusMeters)
        assertEquals(BigDecimal("0.005000"), element1.end.cant)
    }

    @Test
    fun `Plan element listing is filtered by types`() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val geometryAlignment = createAlignment(
            trackNumberId,
            GeometryElementType.LINE,
            GeometryElementType.CURVE,
            GeometryElementType.CLOTHOID,
            GeometryElementType.CLOTHOID,
            GeometryElementType.CURVE,
            GeometryElementType.LINE,
        )
        val plan = plan(trackNumberId = trackNumberId, alignments = listOf(geometryAlignment))
        assertEquals(listOf(LINE, LINE), getElementListingTypes(plan, GeometryElementType.LINE))
        assertEquals(listOf(CURVE, CURVE), getElementListingTypes(plan, GeometryElementType.CURVE))
        assertEquals(listOf(CLOTHOID, CLOTHOID), getElementListingTypes(plan, GeometryElementType.CLOTHOID))
        assertEquals(
            listOf(LINE, CLOTHOID, CLOTHOID, LINE),
            getElementListingTypes(plan, GeometryElementType.LINE, GeometryElementType.CLOTHOID),
        )
        assertEquals(
            listOf(LINE, CURVE, CLOTHOID, CLOTHOID, CURVE, LINE),
            getElementListingTypes(plan, GeometryElementType.LINE, GeometryElementType.CURVE, GeometryElementType.CLOTHOID),
        )
    }


    @Test
    fun `Track element listing is filtered by types`() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val plan = planHeader(trackNumberId = trackNumberId)
        val geometryAlignment = createAlignment(
            trackNumberId,
            GeometryElementType.LINE,
            GeometryElementType.CURVE,
            GeometryElementType.CLOTHOID,
            GeometryElementType.CLOTHOID,
            GeometryElementType.CURVE,
            GeometryElementType.LINE,
        )
        val (track, layoutAlignment) = locationTrackAndAlignment(trackNumberId, createSegments(geometryAlignment))
        assertEquals(
            getElementTypes(geometryAlignment).filter { t -> t == LINE },
            getListingTypes(track, layoutAlignment, plan, geometryAlignment, listOf(LINE)),
        )
        assertEquals(
            getElementTypes(geometryAlignment).filter { t -> t == CURVE },
            getListingTypes(track, layoutAlignment, plan, geometryAlignment, listOf(CURVE)),
        )
        assertEquals(
            getElementTypes(geometryAlignment).filter { t -> t == CLOTHOID },
            getListingTypes(track, layoutAlignment, plan, geometryAlignment, listOf(CLOTHOID)),
        )
        assertEquals(
            getElementTypes(geometryAlignment).filter { t -> t == CLOTHOID || t == LINE },
            getListingTypes(track, layoutAlignment, plan, geometryAlignment, listOf(LINE, CLOTHOID)),
        )
        assertEquals(
            getElementTypes(geometryAlignment).filter { t -> t == CLOTHOID || t == LINE || t == CURVE },
            getListingTypes(track, layoutAlignment, plan, geometryAlignment, listOf(LINE, CURVE, CLOTHOID)),
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
        val planHeader = planHeader(trackNumberId = trackNumberId, srid = LAYOUT_SRID)
        val alignments = listOf(planHeader to alignment1, planHeader to alignment2)

        val context = geocodingContext(
            referenceLinePoints = listOf(Point(0.0, 0.0), Point(100.0, 0.0)),
            trackNumberId = trackNumberId,
        )
        val listing = toElementListing(
            context,
            getTransformation,
            track,
            layoutAlignment,
            allTrackElementTypes,
            TrackMeter(KmNumber.ZERO, 25),
            TrackMeter(KmNumber.ZERO, 35)) { id ->
            alignments.find { a -> a.second.id == id }!!
        }

        // Linked: alignment1 elements 1 & 2 (not 0) + alignment2 elements 0 & 1 (not 2)
        // Address range includes only alignment1 element 2 & alignment2 element 0
        val expectedIds = listOf(alignment1.elements[2].id, alignment2.elements[0].id)
        assertEquals(expectedIds, listing.map(ElementListing::elementId))
    }



    private fun createSegments(alignment: GeometryAlignment) =
        if (alignment.id !is IntId) throw IllegalStateException("Alignment must have int-id for element seeking to work")
        else if (alignment.elements.isEmpty()) throw IllegalStateException("Must have elements to generate the segments for")
        else alignment.elements.map { e -> segment(e.start, e.end, sourceId = e.id) }

    private fun getElementListingTypes(plan: GeometryPlan, vararg types: GeometryElementType) =
        toElementListing(null, getTransformation, plan, types.toList()).map { e -> e.elementType }

    private fun getListingTypes(
        locationTrack: LocationTrack,
        layoutAlignment: LayoutAlignment,
        planHeader: GeometryPlanHeader,
        geometryAlignment: GeometryAlignment,
        elementTypes: List<TrackGeometryElementType> = allTrackElementTypes,
    ) = getListing(locationTrack, layoutAlignment, planHeader, geometryAlignment, elementTypes)
        .map { l -> l.elementType }

    private fun getListing(
        locationTrack: LocationTrack,
        layoutAlignment: LayoutAlignment,
        planHeader: GeometryPlanHeader,
        geometryAlignment: GeometryAlignment,
        elementTypes: List<TrackGeometryElementType> = allTrackElementTypes,
    ) = toElementListing(
        null,
        getTransformation,
        locationTrack,
        layoutAlignment,
        elementTypes,
        null,
        null,
    ) { _ -> planHeader to geometryAlignment }

    private fun getElementTypes(alignment: GeometryAlignment) =
        alignment.elements.map { e -> TrackGeometryElementType.of(e.type) }
}
