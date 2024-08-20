package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.linking.switches.fitSwitch
import fi.fta.geoviite.infra.linking.switches.inferSwitchTransformation
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.math.degreesToRads
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.rotateAroundOrigin
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import kotlin.math.absoluteValue
import kotlin.test.assertEquals
import kotlin.test.fail
import org.junit.jupiter.api.Test

class FittedSwitchTest {
    private val switchStructure = switchStructureYV60_300_1_9()
    private val switchAlignment_1_5_2 =
        switchStructure.alignments.find { alignment ->
            alignment.jointNumbers.contains(JointNumber(5))
        } ?: error("Invalid switch structure")

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
                    segment(
                        transformedPoints[index],
                        transformedPoints[index + 1],
                    )
                },
        )
    }

    @Test
    fun shouldInferSwitchTransformation() {
        val rotation = degreesToRads(45.0)
        val translation = Point(2000.0, 3000.0)

        val alignmentContainingSwitchSegments =
            createAlignmentBySwitchAlignment(
                switchAlignment_1_5_2,
                translation = translation,
                rotation = rotation,
            )

        val presentationJointLocation =
            alignmentContainingSwitchSegments.segments[1].alignmentPoints.first()
        val switchTransformation =
            inferSwitchTransformation(
                presentationJointLocation,
                switchStructure,
                switchAlignment_1_5_2.id as StringId,
                alignmentContainingSwitchSegments,
                ascending = true)

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
        val rotation = degreesToRads(45.0)
        val translation = Point(2000.0, 3000.0)

        val reversedAlignmentContainingSwitchSegments =
            reverseAlignment(
                createAlignmentBySwitchAlignment(
                    switchAlignment_1_5_2,
                    translation = translation,
                    rotation = rotation,
                ))

        val presentationJointLocation =
            reversedAlignmentContainingSwitchSegments.segments[2].alignmentPoints.last()
        val switchTransformation =
            inferSwitchTransformation(
                presentationJointLocation,
                switchStructure,
                switchAlignment_1_5_2.id as StringId,
                reversedAlignmentContainingSwitchSegments,
                ascending = false)
        if (switchTransformation != null) {
            val scaledRotation = (switchTransformation.rotation.rads + Math.PI * 2) % (Math.PI * 2)
            assertEquals(rotation, scaledRotation, 0.01)
            assertApproximatelyEquals(translation, switchTransformation.translation, 0.01)
        } else {
            fail("Should be able to infer switch transformation")
        }
    }

    private fun reverseAlignment(alignment: LayoutAlignment): LayoutAlignment {
        val reverseSegments =
            fixSegmentStarts(
                alignment.segments.reversed().map { segment ->
                    val reversedPoints = segment.segmentPoints.reversed()
                    var cumulativeM = 0.0
                    segment.copy(
                        geometry =
                            segment.geometry.withPoints(
                                reversedPoints.mapIndexed { index, point ->
                                    cumulativeM +=
                                        if (index == 0) 0.0
                                        else lineLength(reversedPoints[index - 1], point)
                                    point.copy(m = cumulativeM)
                                },
                            ),
                        sourceId = null,
                        sourceStart = null,
                    )
                })
        return alignment.copy(segments = reverseSegments)
    }

    private enum class SegmentEndPoint {
        START,
        END
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
                ?: throw Exception(
                    "Switch structure does not contain joint ${jointNumber.intValue}")
        if (!joint.matches.any { match ->
            match.locationTrackId == alignmentId && (m - match.m).absoluteValue < 0.01
        }) {
            fail(
                "Didn't found a match from joint ${jointNumber.intValue}: alignmentId $alignmentId, m $m, $endPoint")
        }
    }

    @Test
    fun shouldCreateSuggestedSwitch() {
        val rotation = degreesToRads(45.0)
        val translation = Point(2000.0, 3000.0)

        val trackId: IntId<LocationTrack> = IntId(1)
        val alignmentContainingSwitchSegments =
            createAlignmentBySwitchAlignment(
                switchAlignment_1_5_2,
                translation = translation,
                rotation = rotation,
            )
        val locationTrack =
            locationTrack(
                IntId(0), alignment = alignmentContainingSwitchSegments, trackId, draft = false)
        val presentationJointLocation =
            alignmentContainingSwitchSegments.segments[1].alignmentPoints.first()

        val missingLocationTrackEndpoint =
            LocationTrackEndpoint(
                trackId,
                Point(presentationJointLocation),
                LocationTrackPointUpdateType.START_POINT,
            )

        val suggestedSwitch =
            fitSwitch(
                locationTrackEndpoint = missingLocationTrackEndpoint,
                switchStructure,
                alignmentMappings =
                    listOf(
                        SuggestedSwitchCreateParamsAlignmentMapping(
                            switchAlignmentId = switchAlignment_1_5_2.id as StringId,
                            locationTrackId = IntId(1),
                        )),
                nearbyAlignments = listOf(),
                alignmentById =
                    mapOf(trackId to (locationTrack to alignmentContainingSwitchSegments)),
                getMeasurementMethod = { null },
            )

        if (suggestedSwitch != null) {
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
        } else {
            fail("Should be able to create suggested switch")
        }
    }
}
