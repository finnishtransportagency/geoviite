package fi.fta.geoviite.infra.ui.testdata

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FileName
import java.time.Instant


class EspooTestData {
    companion object {

        val GEOMETRY_PLAN_NAME = "Linking geometry plan"
        val REFERENCELINE_1_NAME = "ESP1"
        val REFERENCELINE_3_NAME = "ESP3"
        val GEO_ALIGNMENT_A_NAME = "geo-alignment-a"
        val GEO_ALIGNMENT_B_NAME = "geo-alignment-b"
        val GEO_ALIGNMENT_C_NAME = "geo-alignment-c"
        val GEO_ALIGNMENT_D_NAME = "geo-alignment-d"
        val GEO_ALIGNMENT_E_NAME = "geo-alignment-e"
        val GEO_ALIGNMENT_F_NAME = "geo-alignment-f"
        val GEO_ALIGNMENT_I_NAME = "geo-alignment-i"

        lateinit var GEO_SWITCH_2_ALIGNMENT_NAMES: List<String>

        lateinit var switchStructures: List<SwitchStructure>
        lateinit var GEO_SWITCH_1_STRUCTURE: SwitchStructure
        lateinit var GEO_SWITCH_1_ALIGNMENT_NAMES: List<String>
        lateinit var GEO_SWITCH_1_NAME: String

        //X kasvaa -> liikkuu itään
        //Y kasvaa -> liikuu pohjoiseen
        val BASE_POINT_X = 369800.00
        val BASE_POINT_Y = 6676400.00
        val BASE_POINT = Point(BASE_POINT_X, BASE_POINT_Y)

        fun geometryPlan(layoutTrackNumberId: IntId<LayoutTrackNumber>): GeometryPlan {

            GEO_SWITCH_1_STRUCTURE = switchStructures.first { it.type.typeName == "YV60-300-1:9-O" }
            GEO_SWITCH_1_NAME = GEO_SWITCH_1_STRUCTURE.type.typeName.substringBefore("-")

            val switchAndAlignments1 = createSwitchAndAligments(
                switchName = GEO_SWITCH_1_NAME,
                switchStructure = GEO_SWITCH_1_STRUCTURE,
                switchAngle = 0.0,
                switchOrig = BASE_POINT + Point(x = 10.0, y = 130.0),
                trackNumberId = layoutTrackNumberId
            )
            GEO_SWITCH_1_ALIGNMENT_NAMES = switchAndAlignments1.second.map{ it.name.toString() }

            val switchAndAlignments2 = createSwitchAndAligments(
                switchName = "${GEO_SWITCH_1_STRUCTURE.type.typeName.substringBefore("-")}-2",
                switchStructure = GEO_SWITCH_1_STRUCTURE,
                switchAngle = 0.0,
                switchOrig = BASE_POINT + Point(x = 10.0, y = 150.0),
                trackNumberId = layoutTrackNumberId
            )

            GEO_SWITCH_2_ALIGNMENT_NAMES = switchAndAlignments2.second.map { it.name.toString() }

            return GeometryPlan(
                project = createProject(GEOMETRY_PLAN_NAME),
                application = application(),
                author = null,
                planTime = null,
                units = tmi35GeometryUnit(),
                trackNumberId = layoutTrackNumberId,
                switches = listOf(
                    switchAndAlignments1.first,
                ),
                alignments = listOf(
                    geometryAlignmentA(layoutTrackNumberId),
                    geometryAlignmentB(layoutTrackNumberId),
                    geometryAlignmentC(layoutTrackNumberId),
                    geometryAlignmentD(layoutTrackNumberId),
                    geometryAlignmentE(layoutTrackNumberId),
                    geometryAlignmentF(layoutTrackNumberId),
                    geometryAlignmentI(layoutTrackNumberId)
                )
                    .plus(switchAndAlignments1.second)
                    .plus(switchAndAlignments2.second),
                kmPosts = geometryKmPosts(layoutTrackNumberId),
                fileName = FileName("espoo_test_data.xml"),
                oid = null,
                planPhase = PlanPhase.RAILWAY_PLAN,
                decisionPhase = PlanDecisionPhase.APPROVED_PLAN,
                measurementMethod = MeasurementMethod.VERIFIED_DESIGNED_GEOMETRY,
                message = null,
                uploadTime = Instant.now(),
                trackNumberDescription = PlanElementName("diipa daapa")
            )
        }



        fun geometryKmPosts(trackNumberId: IntId<LayoutTrackNumber>): List<GeometryKmPost> {
            val locations = listOf(Point(x = 100.0, y = 10.0), Point(x = 50.0, y = 20.0), Point(x = 50.0, y = 20.0))
                .scan(BASE_POINT + Point(x = 0.0, y = 70.0)) { prevPoint, pointIncr -> prevPoint + pointIncr }

            return locations.mapIndexed { index, location ->
                createGeometryKmPost(
                    trackNumberId,
                    location,
                    "000${index}GM"
                )
            }
        }

        fun trackLayoutKmPosts(trackNumberId: IntId<LayoutTrackNumber>): List<TrackLayoutKmPost> {
            val locations = listOf(Point(x = 100.0, y = 20.0), Point(x = 50.0, y = 20.0))
                .scan(BASE_POINT + Point(x = 0.0, y = 80.0)) { prevPoint, pointIncr -> prevPoint + pointIncr }

            return locations.mapIndexed { index, location ->
                trackLayoutKmPost(
                    "000${index}LO",
                    trackNumberId,
                    location
                )
            }
        }

        fun geometryAlignmentA(trackNumberId: IntId<LayoutTrackNumber>) =
            createGeometryAlignment(alignmentName = GEO_ALIGNMENT_A_NAME,
                trackNumberId = trackNumberId,
                basePoint = BASE_POINT + Point(x = 0.0, y = 20.0),
                incrementPoints = listOf(Point(x = 50.0, y = 5.0), Point(x = 50.0, y = 5.0))
            )

        fun geometryAlignmentB(trackNumberId: IntId<LayoutTrackNumber>) =
            createGeometryAlignment(
                alignmentName = GEO_ALIGNMENT_B_NAME,
                trackNumberId = trackNumberId,
                basePoint = BASE_POINT + Point(x = 0.0, y = 65.0),
                incrementPoints = listOf(Point(x = 50.0, y = 5.0), Point(x = 50.0, y = 5.0))
            )


        fun locationTrackB(trackNumber: IntId<LayoutTrackNumber>) =
            locationTrack(
                name = "B",
                trackNumber = trackNumber,
                basePoint = BASE_POINT + Point(x = 0.0, y = 66.0),
                incrementPoints =  listOf(Point(x = 50.0, y = 5.0), Point(x = 49.0, y = 5.0))
            )


        fun geometryAlignmentC(trackNumberId: IntId<LayoutTrackNumber>) =
            createGeometryAlignment(
                alignmentName = GEO_ALIGNMENT_C_NAME,
                trackNumberId = trackNumberId,
                basePoint = BASE_POINT + Point(x = 0.0, y = 100.0),
                incrementPoints = listOf(Point(x = 50.0, y = 5.0), Point(x = 100.0, y = 10.0))
            )


        fun geometryAlignmentD(trackNumberId: IntId<LayoutTrackNumber>) =
             createGeometryAlignment(
                alignmentName = GEO_ALIGNMENT_D_NAME,
                trackNumberId = trackNumberId,
                basePoint = BASE_POINT + Point(x = 0.0, y = 110.0),
                incrementPoints = listOf(Point(x = 50.0, y = 5.0), Point(x = 100.0, y = 5.0))
            )


        fun locationTrackD(trackNumber: IntId<LayoutTrackNumber>): List<Pair<LocationTrack, LayoutAlignment>> {

            val part1 = locationTrack(
                name = "D1",
                trackNumber = trackNumber,
                basePoint = BASE_POINT + Point(x = 0.0, y = 115.0),
                incrementPoints = listOf(Point(x = 50.0, y = 5.0))
            )

            val part1EndPoint = part1.second.segments.last().points.last()
            val part2StartPoint = Point(x = part1EndPoint.x, y = part1EndPoint.y)

            val part2 = locationTrack(
                name = "D2",
                trackNumber = trackNumber,
                basePoint = part2StartPoint,
                incrementPoints = listOf(Point(x = 100.0, y = 15.0))
            )
            return listOf(part1, part2)
        }

        fun geometryAlignmentE(trackNumberId: IntId<LayoutTrackNumber>) =
            createGeometryAlignment(
                alignmentName = GEO_ALIGNMENT_E_NAME,
                trackNumberId = trackNumberId,
                basePoint = BASE_POINT + Point(x = 2.0, y = -21.0),
                incrementPoints = listOf(Point(x = 20.0, y = 5.0), Point(x = 20.0, y = 5.0))
            )

        fun locationTrackE(trackNumberId: IntId<LayoutTrackNumber>) =
            locationTrack(
                name = "E",
                trackNumber = trackNumberId,
                // basePoint = BASE_POINT + Point(x = 40.0, y = -13.0), <- tämä ei toimi julkaisu validoinnissa "areDirectionsContinuous -> FALSE"
                basePoint = BASE_POINT + Point(x = 44.0, y = -11.0),
                incrementPoints =  listOf(Point(x = 20.0, y = 2.0), Point(x = 20.0, y = 2.0))
            )

        fun geometryAlignmentF(trackNumberId: IntId<LayoutTrackNumber>) =
            createGeometryAlignment(
                alignmentName = GEO_ALIGNMENT_F_NAME,
                trackNumberId = trackNumberId,
                basePoint = BASE_POINT + Point(x = 0.0, y = -25.0),
                incrementPoints = listOf(Point(x = 20.0, y = 5.0), Point(x = 20.0, y = 5.0))
            )

        fun locationTrackF(trackNumberId: IntId<LayoutTrackNumber>) =
            locationTrack(
                name = "F",
                trackNumber = trackNumberId,
                basePoint = BASE_POINT + Point(x = 40.0, y = -18.0),
                incrementPoints =  listOf(Point(x = 20.0, y = 2.0), Point(x = 20.0, y = 2.0))
            )


        fun locationTrackG(trackNumber: IntId<LayoutTrackNumber>): Pair<LocationTrack, LayoutAlignment> =
            locationTrack(
                name = "G",
                trackNumber = trackNumber,
                basePoint = BASE_POINT + Point(x = 0.0, y = 10.0),
                incrementPoints =  listOf(Point(x = 100.0, y = 5.0), Point(x = 100.0, y = 5.0)))


        fun geometryAlignmentI(trackNumberId: IntId<LayoutTrackNumber>) =
            createGeometryAlignment(
                alignmentName = GEO_ALIGNMENT_I_NAME,
                trackNumberId = trackNumberId,
                basePoint = BASE_POINT + Point(x = -20.0, y = 3.0),
                incrementPoints = listOf(Point(x = 150.0, y = 10.0), Point(x = 150.0, y = 10.0))
            )

        fun locationTrackH(trackNumber: IntId<LayoutTrackNumber>): Pair<LocationTrack, LayoutAlignment> =
            locationTrack(
                name = "H",
                trackNumber = trackNumber,
                basePoint = BASE_POINT + Point(x = 0.0, y = 15.0),
                incrementPoints =  listOf(Point(x = 100.0, y = 5.0), Point(x = 100.0, y = 5.0)))


        fun trackLayoutSwitchA(): TrackLayoutSwitch {
            val location = BASE_POINT + Point(x = 0.0, y = 150.00)
            return trackLayoutSwitch("switch-A", listOf(location), switchStructures.first { it.type.typeName == "YV60-300-1:9-O" })
        }

        fun locationTrackJ(trackNumberId: IntId<LayoutTrackNumber>) =
            locationTrack(
                name = "J",
                trackNumber = trackNumberId,
                basePoint = BASE_POINT + Point(x = 10.0, y = -20.0),
                incrementPoints = listOf(Point(x = 30.0, y = 0.0), Point(x = 30.0, y = 0.0))
            )

        fun referenceLine1(trackNumber: IntId<LayoutTrackNumber>): Pair<ReferenceLine, LayoutAlignment> {
            val points = pointsFromIncrementList(BASE_POINT + Point(x = -20.0, y = 5.0), listOf(Point(x = 150.0, y = 10.0), Point(x = 150.0, y = 10.0)) )

            val trackLayoutPoints = toTrackLayoutPoints(*points.toTypedArray())
            val alignment = alignment(segment(trackLayoutPoints))
            return referenceLine(
                alignment = alignment,
                trackNumberId = trackNumber,
                startAddress = TrackMeter(KmNumber(0), 0),
            ) to alignment
        }

        fun referenceLine2(trackNumber: IntId<LayoutTrackNumber>): Pair<ReferenceLine, LayoutAlignment> {
            val points = pointsFromIncrementList(BASE_POINT + Point(x =  0.0, y = 0.0), listOf(Point(x = 15.0, y = 1.0), Point(x = 15.0, y = 1.0)) )

            val trackLayoutPoints = toTrackLayoutPoints(*points.toTypedArray())
            val alignment = alignment(segment(trackLayoutPoints))
            return referenceLine(
                alignment = alignment,
                trackNumberId = trackNumber,
                startAddress = TrackMeter(KmNumber(1), 150),
            ) to alignment
        }

        fun referenceLine3(trackNumber: IntId<LayoutTrackNumber>): Pair<ReferenceLine, LayoutAlignment> {
            val points = pointsFromIncrementList(BASE_POINT + Point(x =  -20.0, y = 10.0), listOf(Point(x = 15+.0, y = 10.0), Point(x = 150.0, y = 10.0)) )

            val trackLayoutPoints = toTrackLayoutPoints(*points.toTypedArray())
            val alignment = alignment(segment(trackLayoutPoints))
            return referenceLine(
                alignment = alignment,
                trackNumberId = trackNumber,
                startAddress = TrackMeter(KmNumber(1), 150),
            ) to alignment
        }

    }
}
