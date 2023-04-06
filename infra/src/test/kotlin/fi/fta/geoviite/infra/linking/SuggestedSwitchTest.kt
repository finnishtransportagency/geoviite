package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SuggestedSwitchTest {
    private val switchStructure = switchStructureYV60_300_1_9()
    private val switchAlignment_1_5_2 = switchStructure.alignments.find { alignment ->
        alignment.jointNumbers.contains(JointNumber(5))
    } ?: throw IllegalStateException("Invalid switch structure")

    private fun transformPoint(
        point: IPoint,
        translation: IPoint = Point(2000.0, 3000.0),
        rotation: Double = degreesToRads(45.0),
    ): IPoint {
        return rotateAroundOrigin(rotation, point) + translation
    }

    private fun createAlignmentBySwitchAlignment(
        switchAlignment: SwitchAlignment,
        translation: Point,
        rotation: Double,
    ): LayoutAlignment {
        val points = listOf<Point>() +
                Point(-300.0, 0.0) +
                switchAlignment.elements.map { element -> element.start } +
                switchAlignment.elements.last().end +
                Point(400.0, 0.0)
        val transformedPoints = points.map { point -> transformPoint(point, translation, rotation) }

        return alignment(
            segments = (0 until transformedPoints.lastIndex).map { index ->
                segment(
                    transformedPoints[index],
                    transformedPoints[index + 1]
                )
            }
        )
    }

    @Test
    fun shouldInferSwitchTransformation() {
        val rotation = degreesToRads(45.0);
        val translation = Point(2000.0, 3000.0)

        val alignmentContainingSwitchSegments = createAlignmentBySwitchAlignment(
            switchAlignment_1_5_2,
            translation = translation,
            rotation = rotation
        )

        val presentationJointLocation =
            alignmentContainingSwitchSegments.segments[1].points.first()
        val switchTransformation = inferSwitchTransformation(
            presentationJointLocation,
            switchStructure,
            switchAlignment_1_5_2.id as StringId,
            alignmentContainingSwitchSegments,
            ascending = true
        )

        if (switchTransformation != null) {
            val scaledRotation = (switchTransformation.rotation.rads + Math.PI * 2) % (Math.PI * 2)
            assertEquals(rotation, scaledRotation, 0.01)
            assertApproximatelyEquals(translation, switchTransformation.translation, 0.01)
        } else {
            fail("Should be able to infer switch transformation")
        }
    }

    @Test
    fun shouldInferSwitchTransformationFromReversedAlignment() {
        val rotation = degreesToRads(45.0);
        val translation = Point(2000.0, 3000.0)

        val reversedAlignmentContainingSwitchSegments = reverseAlignment(
            createAlignmentBySwitchAlignment(
                switchAlignment_1_5_2,
                translation = translation,
                rotation = rotation
            )
        )

        val presentationJointLocation =
            reversedAlignmentContainingSwitchSegments.segments[2].points.last()
        val switchTransformation = inferSwitchTransformation(
            presentationJointLocation,
            switchStructure,
            switchAlignment_1_5_2.id as StringId,
            reversedAlignmentContainingSwitchSegments,
            ascending = false
        )
        if (switchTransformation != null) {
            val scaledRotation = (switchTransformation.rotation.rads + Math.PI * 2) % (Math.PI * 2)
            assertEquals(rotation, scaledRotation, 0.01)
            assertApproximatelyEquals(translation, switchTransformation.translation, 0.01)
        } else {
            fail("Should be able to infer switch transformation")
        }
    }

    private fun reverseAlignment(alignment: LayoutAlignment): LayoutAlignment {
        val reverseSegments = fixSegmentStarts(alignment.segments.reversed().map { segment ->
            val reversedPoints = segment.points.reversed()
            var cumulativeM = 0.0
            segment.copy(
                geometry = segment.geometry.withPoints(
                    reversedPoints.mapIndexed { index, point ->
                        cumulativeM += if (index == 0) 0.0 else lineLength(reversedPoints[index - 1], point)
                        point.copy(m = cumulativeM)
                    }
                ),
                sourceId = null,
                sourceStart = null,
            )
        })
        return alignment.copy(segments = reverseSegments)
    }

    private enum class SegmentEndPoint { START, END }

    private fun assertSuggestedSwitchContainsMatch(
        suggestedSwitch: SuggestedSwitch,
        jointNumber: JointNumber,
        alignmentId: IntId<LocationTrack>,
        segmentIndex: Int,
        endPoint: SegmentEndPoint,
    ) {
        val joint = suggestedSwitch.joints.find { joint -> joint.number == jointNumber }
            ?: throw Exception("Switch structure does not contain joint ${jointNumber.intValue}")
        if (!joint.matches.any { match ->
                match.locationTrackId == alignmentId &&
                        match.segmentIndex == segmentIndex
            }) {
            fail("Didn't found a match from joint ${jointNumber.intValue}: alignmentId $alignmentId, segmentIndex $segmentIndex, $endPoint")
        }
    }

    @Test
    fun shouldCreateSuggestedSwitch() {
        val rotation = degreesToRads(45.0);
        val translation = Point(2000.0, 3000.0)

        val trackId: IntId<LocationTrack> = IntId(1)
        val alignmentContainingSwitchSegments = createAlignmentBySwitchAlignment(
            switchAlignment_1_5_2,
            translation = translation,
            rotation = rotation
        )
        val locationTrack = locationTrack(IntId(0), alignmentContainingSwitchSegments, trackId)
        val presentationJointLocation = alignmentContainingSwitchSegments.segments[1].points.first()

        val missingLocationTrackEndpoint = LocationTrackEndpoint(
            trackId,
            Point(presentationJointLocation),
            LocationTrackPointUpdateType.START_POINT
        )

        val suggestedSwitch = createSuggestedSwitch(
            locationTrackEndpoint = missingLocationTrackEndpoint,
            switchStructure,
            alignmentMappings = listOf(
                SuggestedSwitchCreateParamsAlignmentMapping(
                    switchAlignmentId = switchAlignment_1_5_2.id as StringId,
                    locationTrackId = IntId(1),
                )
            ),
            nearbyAlignments = listOf(),
            alignmentById = mapOf(trackId to (locationTrack to alignmentContainingSwitchSegments)),
            getMeasurementMethod = { null },
        )

        if (suggestedSwitch != null) {
            assertSuggestedSwitchContainsMatch(
                suggestedSwitch,
                JointNumber(1),
                alignmentId = IntId(1),
                segmentIndex = 1,
                SegmentEndPoint.START
            )

            assertSuggestedSwitchContainsMatch(
                suggestedSwitch,
                JointNumber(5),
                alignmentId = IntId(1),
                segmentIndex = 1,
                SegmentEndPoint.END
            )

            assertSuggestedSwitchContainsMatch(
                suggestedSwitch,
                JointNumber(2),
                alignmentId = IntId(1),
                segmentIndex = 2,
                SegmentEndPoint.END
            )
        } else {
            fail("Should be able to create suggested switch")
        }
    }
}
