package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.asSwitchStructure
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.data.YV60_300_1_9_O
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

data class TrackForSwitchLinking(
    val byJoints: List<JointNumber>,
    val locationTrack: LocationTrack,
    val geometry: LocationTrackGeometry,
) {
    fun expandStart(length: Double): TrackForSwitchLinking {
        return expandTrackStart(locationTrack, geometry, length).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }

    fun expandEnd(length: Double): TrackForSwitchLinking {
        return expandTrackEnd(locationTrack, geometry, length).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }
}


class SwitchFittingTest {
    @Test
    fun `Should find correct joint positions for YV switch, optimal geometry`() {
        //
        //  2 |    / 3
        //    |   /
        //  5 |  /
        //    | /
        //  1 |/ 1
        //
        // Track 1-5-2 is going straight, track 1-3 is a diverging track.
        // Geometry is optimal, tracks intersect at joint 1.
        // No existing switch.

        // init data
        val switchStructure = YV60_300_1_9_O()
        val track152 = createTrack(switchStructure, asJointNumbers(1, 5, 2))
        val track13 = createTrack(switchStructure, asJointNumbers(1, 3))

        // fit switch
        val point = track152.geometry.start!!
        val fitted = fitSwitch(point, switchStructure, listOf(track152, track13))

        // assert
        val distance1to5 = switchJointDistance(switchStructure, 1, 5)
        val distance1to2 = switchAlignmentLength(switchStructure, 1, 2)
        val distance1to3 = switchAlignmentLength(switchStructure, 1, 3)
        assertJoint(fitted, 1, track152.locationTrack, 0.0)
        assertJoint(fitted, 5, track152.locationTrack, distance1to5)
        assertJoint(fitted, 2, track152.locationTrack, distance1to2)
        assertJoint(fitted, 3, track13.locationTrack, distance1to3)
    }

    @Test
    fun `Should find correct joint positions for YV switch, straight track continues to both direction`() {
        //
        //    |
        //  2 |    / 3
        //    |   /
        //  5 |  /
        //    | /
        //  1 |/ 1
        //    |
        //    |
        //
        // Track 1-5-2 is going straight and trough, track 1-3 is a diverging track.
        // Geometry is optimal, tracks intersect at joint 1.
        // No existing switch.

        // init data
        val switchStructure = YV60_300_1_9_O()
        val extraTrackLength = 10.0
        val track152 =
            createTrack(switchStructure, asJointNumbers(1, 5, 2))
                .expandStart(extraTrackLength)
                .expandEnd(extraTrackLength)
        val track13 = createTrack(switchStructure, asJointNumbers(1, 3))

        // fit switch
        val point = track152.geometry.getPointAtM(extraTrackLength)!!
        val fitted = fitSwitch(point, switchStructure, listOf(track152, track13))

        // assert
        val distance1to5 = switchJointDistance(switchStructure, 1, 5)
        val distance1to2 = switchAlignmentLength(switchStructure, 1, 2)
        val distance1to3 = switchAlignmentLength(switchStructure, 1, 3)
        assertJoint(fitted, 1, track152.locationTrack, expectedM = 0.0 + extraTrackLength)
        assertJoint(fitted, 5, track152.locationTrack, expectedM = distance1to5 + extraTrackLength)
        assertJoint(fitted, 2, track152.locationTrack, expectedM = distance1to2 + extraTrackLength)
        assertJoint(fitted, 3, track13.locationTrack, expectedM = distance1to3)
    }
}



fun switchAlignmentLength(switchStructureData: SwitchStructureData, start: Int, end: Int): Double {
    val alignment =
        requireNotNull(
            switchStructureData.alignments.firstOrNull { alignment ->
                alignment.jointNumbers.first() == JointNumber(start) &&
                    alignment.jointNumbers.last() == JointNumber(end)
            }
        ) {
            "Switch alignment $start-$end does not exist"
        }
    return alignment.length()
}

fun switchJointDistance(switchStructureData: SwitchStructureData, start: Int, end: Int): Double {
    return lineLength(
        switchStructureData.getJoint(JointNumber(start)).location,
        switchStructureData.getJoint(JointNumber(end)).location,
    )
}

fun assertJoint(fitted: FittedSwitch, joint: Int, track: LocationTrack, expectedM: Double) {
    val jointNumber = JointNumber(joint)
    val fittedJoint = fitted.joints.first { fittedJoint -> fittedJoint.number == jointNumber }
    val matches = fittedJoint.matches.filter { match -> match.locationTrackId == track.id as IntId }
    assertEquals(1, matches.count(), "Expecting one match per location track \"${track.name}\" and joint $joint")
    val match = matches.first()
    assertEquals(expectedM, match.m, 0.001, "M-value is not matching")
}

fun asJointNumbers(vararg joints: Int): List<JointNumber> {
    return joints.map { joint -> JointNumber(joint) }
}


fun fitSwitch(
    point: IPoint,
    switchStructure: SwitchStructureData,
    nearbyLocationTracks: List<TrackForSwitchLinking>,
    layoutSwitchId: IntId<LayoutSwitch> = IntId(1),
): FittedSwitch {
    val request = SwitchPlacingRequest(SamplingGridPoints(Point(point)), layoutSwitchId)
    val nearbyTracks = nearbyLocationTracks.map { track -> track.locationTrack to track.geometry }
    return findBestSwitchFitForAllPointsInSamplingGrid(
        request,
        switchStructure = asSwitchStructure(switchStructure),
        nearbyTracks,
    )
        .keys()
        .first()
}

fun createTrack(switchStructure: SwitchStructureData, jointNumbers: List<JointNumber>): TrackForSwitchLinking {
    val switchAlignment =
        requireNotNull(
            switchStructure.alignments.firstOrNull { switchAlignment ->
                switchAlignment.jointNumbers.containsAll(jointNumbers) &&
                    jointNumbers.containsAll(switchAlignment.jointNumbers)
            }
        ) {
            "Switch alignment does not exists by joints $jointNumbers"
        }
    val segmentEndPoints =
        switchAlignment.elements.map { element -> element.start to element.end }.map { (p1, p2) -> (p1) to (p2) }
    val trackName = jointNumbers.map { it.intValue }.joinToString("-")
    val (locationTrack, geometry) = createTrack(segmentEndPoints, trackName)
    return TrackForSwitchLinking(jointNumbers, locationTrack, geometry)
}

