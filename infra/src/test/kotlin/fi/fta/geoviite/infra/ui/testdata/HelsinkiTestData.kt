package fi.fta.geoviite.infra.ui.testdata

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import java.math.BigDecimal
import java.time.Instant

class HelsinkiTestData {
    companion object {
        const val GEOMETRY_PLAN_NAME = "Helsinki test project"
        var WEST_LT_NAME = "lt-west"
        var EAST_LT_NAME = "lt-east"

        const val HKI_BASE_POINT_X = 385000.00
        const val HKI_BASE_POINT_Y = 6672000.00

        const val HKI_TRACKNUMBER_1 = "HKI1"
        const val HKI_TRACKNUMBER_2 = "HKI2"

        val HKI_BASE_POINT = Point(HKI_BASE_POINT_X, HKI_BASE_POINT_Y)

        fun geometryPlan(trackLayoutTrackNumberId: IntId<TrackLayoutTrackNumber>): GeometryPlan {
            return GeometryPlan(
                project = createProject(GEOMETRY_PLAN_NAME),
                application = application(),
                author = null,
                planTime = null,
                units = tmi35GeometryUnit(),
                trackNumberId = trackLayoutTrackNumberId,
                trackNumberDescription = PlanElementName(""),
                alignments = listOf(westGeometryAlignment(trackLayoutTrackNumberId)),
                switches = listOf(),
                kmPosts = listOf(westGeometryKmPost(trackLayoutTrackNumberId)),
                fileName = FileName("ratapiha.xml"),
                oid = null,
                planPhase = PlanPhase.RAILWAY_PLAN,
                decisionPhase = PlanDecisionPhase.APPROVED_PLAN,
                measurementMethod = MeasurementMethod.VERIFIED_DESIGNED_GEOMETRY,
                message = null,
                uploadTime = Instant.now()
            )
        }

        fun westGeometryKmPost(trackNumberId: IntId<TrackLayoutTrackNumber>): GeometryKmPost {
            val location = HKI_BASE_POINT + Point(x = 710.00, y = 550.00)
            return createGeometryKmPost(trackNumberId, location, "0001")
        }

        fun westGeometryAlignment(trackNumberId: DomainId<TrackLayoutTrackNumber>): GeometryAlignment {
            val point1 = Point(x = HKI_BASE_POINT_X + 680.00, y = HKI_BASE_POINT_Y + 410.00) //etelä
            val point2 = Point(x = HKI_BASE_POINT_X + 695.00, y = HKI_BASE_POINT_Y + 500.00)
            val point3 = Point(x = HKI_BASE_POINT_X + 700.00, y = HKI_BASE_POINT_Y + 560.00) //pohjoinen

            val staStart = BigDecimal("543.333470")
            val length1 = calculateDistance(listOf(point1, point2), LAYOUT_SRID).toBigDecimal()
            val length2 = calculateDistance(listOf(point2, point3), LAYOUT_SRID).toBigDecimal()

            return GeometryAlignment(
                name = AlignmentName("west geo alignment"),
                description = FreeText("west-geometry-alignment 001"),
                oidPart = null,
                state = PlanState.PROPOSED,
                featureTypeCode = FeatureTypeCode("111"),
                staStart = BigDecimal("0.000000"),
                elements = listOf(
                    geometryLine("south", "1", point1, point2, staStart, length1),
                    geometryLine("north", "2", point2, point3, staStart, length2)
                ),
                profile = null,
                trackNumberId = trackNumberId,
            )
        }

        fun westMainLocationTrack(trackNumber: IntId<TrackLayoutTrackNumber>): Pair<LocationTrack, LayoutAlignment> {
            return locationTrack(
                name = "west",
                trackNumber = trackNumber,
                basePoint = HKI_BASE_POINT + Point(x = 675.0, y = 410.0 ),
                incrementPoints = listOf(Point(x = 15.0, y = 90.0), Point(x = 5.0, y = 60.0)),
            )
        }

        fun westReferenceLine(trackNumber: IntId<TrackLayoutTrackNumber>): Pair<ReferenceLine, LayoutAlignment> {
            val trackLayoutPoints = toTrackLayoutPoints(
                Point(x = HKI_BASE_POINT_X + 670.00, y = HKI_BASE_POINT_Y + 410.00), //etelä
                Point(x = HKI_BASE_POINT_X + 685.00, y = HKI_BASE_POINT_Y + 500.00),
                Point(x = HKI_BASE_POINT_X + 690.00, y = HKI_BASE_POINT_Y + 560.00), //pohjoinen
            )
            val alignment = alignment(segment(trackLayoutPoints))
            return referenceLine(
                alignment = alignment,
                trackNumberId = trackNumber,
                startAddress = TrackMeter(KmNumber(2), 150),
            ) to alignment
        }

        fun eastReferenceLine(trackNumber: IntId<TrackLayoutTrackNumber>): Pair<ReferenceLine, LayoutAlignment> {
            val trackLayoutPoints = toTrackLayoutPoints(
                Point(x = HKI_BASE_POINT_X + 675.00, y = HKI_BASE_POINT_Y + 410.00), //etelä
                Point(x = HKI_BASE_POINT_X + 690.00, y = HKI_BASE_POINT_Y + 500.00),
                Point(x = HKI_BASE_POINT_X + 695.00, y = HKI_BASE_POINT_Y + 560.00), //pohjoinen
            )
            val alignment = alignment(segment(trackLayoutPoints))
            return referenceLine(
                alignment = alignment,
                trackNumberId = trackNumber,
                startAddress = TrackMeter(KmNumber(2), 150),
            ) to alignment
        }

        fun eastLocationTrack(trackNumberId: IntId<TrackLayoutTrackNumber>): Pair<LocationTrack, LayoutAlignment> {
            return locationTrack(
                name = "east",
                trackNumber = trackNumberId,
                basePoint = HKI_BASE_POINT + Point(x = 752.0, y = 410.0),
                incrementPoints = listOf(Point(x = 0.0, y = 150.0)),
            )
        }

        fun westTrackLayoutKmPosts(trackNumber: IntId<TrackLayoutTrackNumber>): List<TrackLayoutKmPost> {
            val point1 = HKI_BASE_POINT + Point(x = 690.00, y = 410.00)
            val point2 = HKI_BASE_POINT + Point(x = 690.00, y = 485.00)
            val point3 = HKI_BASE_POINT + Point(x = 690.00, y = 560.00)

            return arrayListOf(
                trackLayoutKmPost("0001we", trackNumber, point1),
                trackLayoutKmPost("0002we", trackNumber, point2),
                trackLayoutKmPost("0003we", trackNumber, point3)
            )

        }

        fun eastTrackLayoutKmPosts(trackNumber: DomainId<TrackLayoutTrackNumber>): List<TrackLayoutKmPost> {
            val point1 = HKI_BASE_POINT + Point(x = 752.00, y = 410.00)
            val point2 = HKI_BASE_POINT + Point(x = 752.00, y = 485.00)
            val point3 = HKI_BASE_POINT + Point(x = 752.00, y = 560.00)

            return arrayListOf(
                trackLayoutKmPost("0001es", trackNumber, point1),
                trackLayoutKmPost("0002es", trackNumber, point2),
                trackLayoutKmPost("0003es", trackNumber, point3)
            )
        }

        fun westTrackLayoutSwitch(): TrackLayoutSwitch {
            val location = HKI_BASE_POINT + Point(x = 690.00, y = 450.00)
            return trackLayoutSwitch("west-switch", listOf(location), switchStructureYV60_300_1_9())
        }

        fun eastTrackLayoutSwitch(): TrackLayoutSwitch {
            val location = HKI_BASE_POINT + Point(x = 752.00, y = 500.00)
            return trackLayoutSwitch("east-switch", listOf(location), switchStructureYV60_300_1_9())
        }
    }
}
