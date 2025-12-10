package fi.fta.geoviite.infra.ui.testdata

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.PlanApplicability
import fi.fta.geoviite.infra.geometry.PlanDecisionPhase
import fi.fta.geoviite.infra.geometry.PlanName
import fi.fta.geoviite.infra.geometry.PlanPhase
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.geometry.PlanState
import fi.fta.geoviite.infra.geometry.application
import fi.fta.geoviite.infra.geometry.geometryLine
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import java.math.BigDecimal
import java.time.Instant

class HelsinkiTestData private constructor() {
    companion object {
        const val GEOMETRY_PLAN_NAME = "Helsinki test project"
        val WEST_LT_NAME = AlignmentName("lt-west")
        val EAST_LT_NAME = AlignmentName("lt-east")

        const val HKI_BASE_POINT_X = 385000.00
        const val HKI_BASE_POINT_Y = 6672000.00

        val HKI_TRACK_NUMBER_1 = TrackNumber("HKI1")
        val HKI_TRACK_NUMBER_2 = TrackNumber("HKI2")

        val HKI_BASE_POINT = Point(HKI_BASE_POINT_X, HKI_BASE_POINT_Y)

        fun geometryPlan(trackNumber: TrackNumber): GeometryPlan {
            return GeometryPlan(
                source = PlanSource.GEOMETRIAPALVELU,
                project = createProject(GEOMETRY_PLAN_NAME),
                application = application(),
                author = null,
                planTime = null,
                units = tmi35GeometryUnit(),
                trackNumber = trackNumber,
                trackNumberDescription = PlanElementName(""),
                alignments = listOf(westGeometryAlignment()),
                switches = listOf(),
                kmPosts = listOf(westGeometryKmPost()),
                fileName = FileName("ratapiha.xml"),
                pvDocumentId = null,
                planPhase = PlanPhase.RAILWAY_PLAN,
                decisionPhase = PlanDecisionPhase.APPROVED_PLAN,
                measurementMethod = MeasurementMethod.VERIFIED_DESIGNED_GEOMETRY,
                elevationMeasurementMethod = ElevationMeasurementMethod.TOP_OF_SLEEPER,
                message = null,
                uploadTime = Instant.now(),
                name = PlanName("ratapiha"),
                planApplicability = PlanApplicability.PLANNING,
            )
        }

        fun westGeometryKmPost(): GeometryKmPost {
            val location = HKI_BASE_POINT + Point(x = 710.00, y = 550.00)
            return createGeometryKmPost(location, "0001")
        }

        fun westGeometryAlignment(): GeometryAlignment {
            val point1 = Point(x = HKI_BASE_POINT_X + 680.00, y = HKI_BASE_POINT_Y + 410.00) // etelä
            val point2 = Point(x = HKI_BASE_POINT_X + 695.00, y = HKI_BASE_POINT_Y + 500.00)
            val point3 = Point(x = HKI_BASE_POINT_X + 700.00, y = HKI_BASE_POINT_Y + 560.00) // pohjoinen

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
                elements =
                    listOf(
                        geometryLine("south", "1", point1, point2, staStart, length1),
                        geometryLine("north", "2", point2, point3, staStart, length2),
                    ),
                profile = null,
            )
        }

        fun westMainLocationTrack(
            trackNumber: IntId<LayoutTrackNumber>,
            draft: Boolean = false,
        ): Pair<LocationTrack, LocationTrackGeometry> {
            return locationTrack(
                name = "west",
                trackNumber = trackNumber,
                basePoint = HKI_BASE_POINT + Point(x = 675.0, y = 410.0),
                incrementPoints = listOf(Point(x = 15.0, y = 90.0), Point(x = 5.0, y = 60.0)),
                draft = draft,
            )
        }

        fun westReferenceLine(
            trackNumber: IntId<LayoutTrackNumber>,
            draft: Boolean = false,
        ): Pair<ReferenceLine, ReferenceLineGeometry> {
            val points =
                toSegmentPoints(
                    Point(x = HKI_BASE_POINT_X + 670.00, y = HKI_BASE_POINT_Y + 410.00), // etelä
                    Point(x = HKI_BASE_POINT_X + 685.00, y = HKI_BASE_POINT_Y + 500.00),
                    Point(x = HKI_BASE_POINT_X + 690.00, y = HKI_BASE_POINT_Y + 560.00), // pohjoinen
                )
            val geometry = referenceLineGeometry(segment(points))
            return referenceLine(
                geometry = geometry,
                trackNumberId = trackNumber,
                startAddress = TrackMeter(KmNumber(2), 150),
                draft = draft,
            ) to geometry
        }

        fun eastReferenceLine(trackNumber: IntId<LayoutTrackNumber>): Pair<ReferenceLine, ReferenceLineGeometry> {
            val points =
                toSegmentPoints(
                    Point(x = HKI_BASE_POINT_X + 675.00, y = HKI_BASE_POINT_Y + 410.00), // etelä
                    Point(x = HKI_BASE_POINT_X + 690.00, y = HKI_BASE_POINT_Y + 500.00),
                    Point(x = HKI_BASE_POINT_X + 695.00, y = HKI_BASE_POINT_Y + 560.00), // pohjoinen
                )
            val geometry = referenceLineGeometry(segment(points))
            return referenceLine(
                geometry = geometry,
                trackNumberId = trackNumber,
                startAddress = TrackMeter(KmNumber(2), 150),
                draft = false,
            ) to geometry
        }

        fun eastLocationTrack(trackNumberId: IntId<LayoutTrackNumber>): Pair<LocationTrack, LocationTrackGeometry> {
            return locationTrack(
                name = "east",
                trackNumber = trackNumberId,
                basePoint = HKI_BASE_POINT + Point(x = 752.0, y = 410.0),
                incrementPoints = listOf(Point(x = 0.0, y = 150.0)),
                draft = false,
            )
        }

        fun westLayoutKmPosts(trackNumberId: IntId<LayoutTrackNumber>): List<LayoutKmPost> {
            val point1 = HKI_BASE_POINT + Point(x = 690.00, y = 410.00)
            val point2 = HKI_BASE_POINT + Point(x = 690.00, y = 485.00)
            val point3 = HKI_BASE_POINT + Point(x = 690.00, y = 560.00)

            return arrayListOf(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber("0001we"),
                    gkLocation = kmPostGkLocation(point1),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber("0002we"),
                    gkLocation = kmPostGkLocation(point2),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber("0003we"),
                    gkLocation = kmPostGkLocation(point3),
                    draft = false,
                ),
            )
        }

        fun eastLayoutKmPosts(trackNumberId: IntId<LayoutTrackNumber>): List<LayoutKmPost> {
            val point1 = HKI_BASE_POINT + Point(x = 752.00, y = 410.00)
            val point2 = HKI_BASE_POINT + Point(x = 752.00, y = 485.00)
            val point3 = HKI_BASE_POINT + Point(x = 752.00, y = 560.00)

            return arrayListOf(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber("0001es"),
                    gkLocation = kmPostGkLocation(point1),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber("0002es"),
                    gkLocation = kmPostGkLocation(point2),
                    draft = false,
                ),
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber("0003es"),
                    gkLocation = kmPostGkLocation(point3),
                    draft = false,
                ),
            )
        }

        fun westLayoutSwitch(): LayoutSwitch {
            val location = HKI_BASE_POINT + Point(x = 690.00, y = 450.00)
            return layoutSwitch("west-switch", listOf(location), switchStructureYV60_300_1_9())
        }

        fun eastLayoutSwitch(): LayoutSwitch {
            val location = HKI_BASE_POINT + Point(x = 752.00, y = 500.00)
            return layoutSwitch("east-switch", listOf(location), switchStructureYV60_300_1_9())
        }
    }
}
