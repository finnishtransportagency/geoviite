package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.degreesToRads
import fi.fta.geoviite.infra.math.rotateAroundOrigin
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import kotlin.math.absoluteValue
import kotlin.test.fail
import org.junit.jupiter.api.Test

class FittedSwitchTest {
    private val switchStructure = switchStructureYV60_300_1_9()
    private val switchAlignment_1_5_2 =
        switchStructure.alignments.find { alignment -> alignment.jointNumbers.contains(JointNumber(5)) }
            ?: error("Invalid switch structure")

    private fun transformPoint(
        point: IPoint,
        translation: IPoint = Point(2000.0, 3000.0),
        rotation: Double = degreesToRads(45.0),
    ): IPoint {
        return rotateAroundOrigin(rotation, point) + translation
    }

    private fun createAlignmentBySwitchAlignment(
        switchAlignment: SwitchStructureAlignment,
        translation: Point,
        rotation: Double,
    ): LayoutAlignment {
        val points =
            listOf<Point>() +
                Point(-300.0, 0.0) +
                switchAlignment.elements.map { element -> element.start } +
                switchAlignment.elements.last().end +
                Point(400.0, 0.0)
        val transformedPoints = points.map { point -> transformPoint(point, translation, rotation) }

        return alignment(
            segments =
                (0 until transformedPoints.lastIndex).map { index ->
                    segment(transformedPoints[index], transformedPoints[index + 1])
                }
        )
    }

    private enum class SegmentEndPoint {
        START,
        END,
    }

    private fun assertSuggestedSwitchContainsMatch(
        switchSuggestion: FittedSwitch,
        jointNumber: JointNumber,
        alignmentId: IntId<LocationTrack>,
        m: Double,
        endPoint: SegmentEndPoint,
    ) {
        val joint =
            switchSuggestion.joints.find { joint -> joint.number == jointNumber }
                ?: throw Exception("Switch structure does not contain joint ${jointNumber.intValue}")
        if (
            !joint.matches.any { match -> match.locationTrackId == alignmentId && (m - match.m).absoluteValue < 0.01 }
        ) {
            fail("Didn't found a match from joint ${jointNumber.intValue}: alignmentId $alignmentId, m $m, $endPoint")
        }
    }

    @Test
    fun shouldCreateFittedSwitch() {
        val rotation = degreesToRads(45.0)
        val translation = Point(2000.0, 3000.0)

        val trackId: IntId<LocationTrack> = IntId(1)
        val alignmentContainingSwitchSegments =
            createAlignmentBySwitchAlignment(switchAlignment_1_5_2, translation = translation, rotation = rotation)
        val locationTrack =
            locationTrack(IntId(0), alignment = alignmentContainingSwitchSegments, trackId, draft = false)

        val suggestedSwitch =
            fitSwitch(
                jointsInLayoutSpace =
                    listOf(
                        SwitchStructureJoint(
                            JointNumber(1),
                            alignmentContainingSwitchSegments.segments[1].segmentStart.toPoint(),
                        ),
                        SwitchStructureJoint(
                            JointNumber(5),
                            alignmentContainingSwitchSegments.segments[2].segmentStart.toPoint(),
                        ),
                        SwitchStructureJoint(
                            JointNumber(2),
                            alignmentContainingSwitchSegments.segments[3].segmentStart.toPoint(),
                        ),
                    ),
                switchStructure,
                alignments = listOf(locationTrack to cropNothing(alignmentContainingSwitchSegments)),
                locationAccuracy = null,
            )

        assertSuggestedSwitchContainsMatch(
            suggestedSwitch,
            JointNumber(1),
            alignmentId = IntId(1),
            m = 300.0,
            SegmentEndPoint.START,
        )

        assertSuggestedSwitchContainsMatch(
            suggestedSwitch,
            JointNumber(5),
            alignmentId = IntId(1),
            m = 316.615,
            SegmentEndPoint.END,
        )

        assertSuggestedSwitchContainsMatch(
            suggestedSwitch,
            JointNumber(2),
            alignmentId = IntId(1),
            m = 334.43,
            SegmentEndPoint.END,
        )
    }
}
