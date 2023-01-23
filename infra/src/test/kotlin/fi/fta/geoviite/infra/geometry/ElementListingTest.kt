import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.KKJ0
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.geometry.GeometryElementType.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class ElementListingTest {

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
        val plan = plan(
            trackNumberId = IntId(1),
            alignments = listOf(geometryAlignment(
                elements = listOf(line(Point(10.0, 10.0), Point(20.0, 20.0))),
            ))
        )
        val elementListing = toElementListing(geocodingContext, plan, GeometryElementType.values().toList())
        assertEquals(1, elementListing.size)
        val element1 = elementListing[0]

        assertEquals(Point(10.0, 10.0), element1.start.coordinate)
        assertEquals(TrackMeter(KmNumber(1), BigDecimal("110.000")), element1.start.address)
        assertEquals(Point(20.0, 20.0), element1.end.coordinate)
        assertEquals(TrackMeter(KmNumber(1), BigDecimal("120.000")), element1.end.address)
    }

    @Test
    fun `Element listing is filtered by types`() {
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

}
