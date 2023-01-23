import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.KKJ0
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.geometry.GeometryElementType.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ElementListingTest {

    @Test
    fun `Start location includes calculated values`() {
    }

    @Test
    fun `End location includes calculated values`() {

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
        assertEquals(
            alignment.elements.map { e -> roundTo3Decimals(e.calculatedLength) },
            listing.map { l -> l.lengthMeters },
        )
        assertEquals(alignment.elements.map { e -> e.start }, listing.map { l -> l.start.coordinate })
        assertEquals(alignment.elements.map { e -> e.end }, listing.map { l -> l.end.coordinate })
    }

    @Test
    fun `Element listing is fitered by types`() {
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
