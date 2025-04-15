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
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

data class TrackForSwitchFitting(
    val byJoints: List<JointNumber>,
    val locationTrack: LocationTrack,
    val geometry: LocationTrackGeometry,
) {
    val locationTrackId = locationTrack.id as IntId
    val trackAndGeometry = locationTrack to geometry
    val length = geometry.length

    fun startPoint(): IPoint {
        return requireNotNull(geometry.start)
    }

    fun getPointAtM(m: Double): IPoint? {
        return geometry.getPointAtM(m)
    }

    fun cutFromStart(length: Double): TrackForSwitchFitting {
        return cutFromStart(locationTrack, geometry, length).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }

    fun expandFromStart(length: Double): TrackForSwitchFitting {
        return expandTrackFromStart(locationTrack, geometry, length).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }

    fun expandFromEnd(length: Double): TrackForSwitchFitting {
        return expandTrackFromEnd(locationTrack, geometry, length).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }

    fun moveForward(distance: Double): TrackForSwitchFitting {
        return moveTrackForward(locationTrack, geometry, distance).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }

    fun withSwitchJointAtEnd(
        switchId: IntId<LayoutSwitch>,
        switchStructure: SwitchStructureData,
        jointNumber: Int,
        direction: RelativeDirection = RelativeDirection.Along,
    ): TrackForSwitchFitting {
        return withSwitchJoint(length, switchId, switchStructure, jointNumber, direction)
    }

    fun withSwitchJointAtStart(
        switchId: IntId<LayoutSwitch>,
        switchStructure: SwitchStructureData,
        jointNumber: Int,
        direction: RelativeDirection = RelativeDirection.Along,
    ): TrackForSwitchFitting {
        return withSwitchJoint(0.0, switchId, switchStructure, jointNumber, direction)
    }

    fun withSwitchJoint(
        mValue: Double,
        switchId: IntId<LayoutSwitch>,
        switchStructure: SwitchStructureData,
        jointNumber: Int,
        direction: RelativeDirection = RelativeDirection.Along,
    ): TrackForSwitchFitting {
        return withSwitchJoint(
                locationTrack,
                geometry,
                mValue,
                switchId,
                switchStructure,
                JointNumber(jointNumber),
                direction,
            )
            .let { (locationTrack, geometry) -> this.copy(locationTrack = locationTrack, geometry = geometry) }
    }

    fun withTopologicalStartSwitch(
        switchId: IntId<LayoutSwitch>,
        switchStructure: SwitchStructureData,
        jointNumber: Int,
    ): TrackForSwitchFitting {
        return withTopologicalStartSwitch(locationTrack, geometry, switchId, switchStructure, JointNumber(jointNumber))
            .let { (locationTrack, geometry) -> this.copy(locationTrack = locationTrack, geometry = geometry) }
    }

    fun withTopologicalEndSwitch(
        switchId: IntId<LayoutSwitch>,
        switchStructure: SwitchStructureData,
        jointNumber: Int,
    ): TrackForSwitchFitting {
        return withTopologicalEndSwitch(locationTrack, geometry, switchId, switchStructure, JointNumber(jointNumber))
            .let { (locationTrack, geometry) -> this.copy(locationTrack = locationTrack, geometry = geometry) }
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
        // Track 1-5-2 is going straight
        // Track 1-3 is a diverging track.
        // Geometry is optimal, tracks intersect at joint 1.

        // init data
        val switchStructure = YV60_300_1_9_O()
        val track152 = createTrack(switchStructure, asJointNumbers(1, 5, 2))
        val track13 = createTrack(switchStructure, asJointNumbers(1, 3))

        // fit switch
        val targetPoint = track152.startPoint()
        val fitted = fitSwitch(targetPoint, switchStructure, listOf(track152, track13))

        // assert
        val distance1to5 = switchJointDistance(switchStructure, 1, 5)
        val distance1to2 = switchAlignmentLength(switchStructure, 1, 2)
        val distance1to3 = switchAlignmentLength(switchStructure, 1, 3)
        assertJoint(fitted, 1, track152.locationTrack, 0.0)
        assertJoint(fitted, 5, track152.locationTrack, distance1to5)
        assertJoint(fitted, 2, track152.locationTrack, distance1to2)
        assertJoint(fitted, 1, track13.locationTrack, 0.0)
        assertJoint(fitted, 3, track13.locationTrack, distance1to3)
        assertMatchCount(fitted, 5)
    }

    @Test
    fun `Should find correct joint positions for YV switch, straight track continues to both directions`() {
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
        // Track 1-5-2 is going straight and trough
        // Track 1-3 is a diverging track.
        // Geometry is optimal, tracks intersect at joint 1.

        // init data
        val switchStructure = YV60_300_1_9_O()
        val extraTrackLength = 10.0
        val track152 =
            createTrack(switchStructure, asJointNumbers(1, 5, 2))
                .expandFromStart(extraTrackLength)
                .expandFromEnd(extraTrackLength)
        val track13 = createTrack(switchStructure, asJointNumbers(1, 3))

        // fit switch
        val targetPoint = track152.getPointAtM(extraTrackLength)!!
        val fitted = fitSwitch(targetPoint, switchStructure, listOf(track152, track13))

        // assert
        val distance1to5 = switchJointDistance(switchStructure, 1, 5)
        val distance1to2 = switchAlignmentLength(switchStructure, 1, 2)
        val distance1to3 = switchAlignmentLength(switchStructure, 1, 3)
        assertJoint(fitted, 1, track152.locationTrack, expectedM = 0.0 + extraTrackLength)
        assertJoint(fitted, 5, track152.locationTrack, expectedM = distance1to5 + extraTrackLength)
        assertJoint(fitted, 2, track152.locationTrack, expectedM = distance1to2 + extraTrackLength)
        assertJoint(fitted, 1, track13.locationTrack, 0.0)
        assertJoint(fitted, 3, track13.locationTrack, expectedM = distance1to3)
        assertMatchCount(fitted, 5)
    }

    @Test
    fun `Should find correct joint positions for YV switch in a case of three connecting tracks`() {
        // track A   track C
        //  2 |    / 3
        //    |   /
        //  5 |  /
        //    | /
        //  1 |/ 1
        //    T
        //    |
        //  track B
        //
        // Track B ends at joint 1
        // Track A starts from joint 1
        // Track C is diverging track
        // Geometry is optimal

        // init data
        val switchStructure = YV60_300_1_9_O()
        val trackBLength = 20.0
        val trackA = createTrack(switchStructure, asJointNumbers(1, 5, 2))
        val trackB = createPrependingTrack(trackA, trackBLength, "Track B")
        val trackC = createTrack(switchStructure, asJointNumbers(1, 3))

        // fit switch
        val targetPoint = trackA.startPoint()
        val fitted = fitSwitch(targetPoint, switchStructure, listOf(trackA, trackB, trackC))

        // assert
        val distance1to5 = switchJointDistance(switchStructure, 1, 5)
        val distance1to2 = switchAlignmentLength(switchStructure, 1, 2)
        val distance1to3 = switchAlignmentLength(switchStructure, 1, 3)
        assertJoint(fitted, 1, trackA.locationTrack, expectedM = 0.0)
        assertJoint(fitted, 5, trackA.locationTrack, expectedM = distance1to5)
        assertJoint(fitted, 2, trackA.locationTrack, expectedM = distance1to2)
        assertJoint(fitted, 1, trackC.locationTrack, expectedM = 0.0)
        assertJoint(fitted, 3, trackC.locationTrack, expectedM = distance1to3)
        assertJoint(fitted, 1, trackB.locationTrack, expectedM = trackBLength)
        assertMatchCount(fitted, 6)
    }

    @Test
    fun `XXX`() {
        // track A   track C
        //  2 |    / 3
        //    |   /
        //  5 |  /
        //    | /
        //  1 ╋/ 1
        //    |
        //    |
        // track B
        //
        // In this case geometry is not optimal.
        // Track B ends slightly after joint 1
        // Track A continues from track B

        // init data
        val switchStructure = YV60_300_1_9_O()
        val positioningErrorInMeters = 0.1
        val trackBLength = 20.0
        val trackA = createTrack(switchStructure, asJointNumbers(1, 5, 2)).cutFromStart(positioningErrorInMeters)
        val trackB = createPrependingTrack(trackA, trackBLength, "track B")
        val trackC = createTrack(switchStructure, asJointNumbers(1, 3))

        // fit switch
        val targetPoint = trackC.geometry.start!!
        val fitted = fitSwitch(targetPoint, switchStructure, listOf(trackA, trackB, trackC))

        // assert
        val distance1to5 = switchJointDistance(switchStructure, 1, 5)
        val distance1to2 = switchAlignmentLength(switchStructure, 1, 2)
        val distance1to3 = switchAlignmentLength(switchStructure, 1, 3)
        assertJoint(fitted, 1, trackA.locationTrack, expectedM = 0.0)
        assertJoint(fitted, 5, trackA.locationTrack, expectedM = distance1to5)
        assertJoint(fitted, 2, trackA.locationTrack, expectedM = distance1to2)
        assertJoint(fitted, 3, trackC.locationTrack, expectedM = distance1to3)
    }

    @Test
    fun `Should map fitted switch to graph model`() {
        // init data
        // tässä pitäisi saada ymmärrettävästi luotua tilanne, jonka fitting tuottaa
        // TAI oikeastaan pitäisikö luoda tilanne, jossa fitted Switch on jo muutettu edge maailmaan
        // Muutos täytyy myös testata, mutta se on varmaan melko yksinkertainen
        // - On siinä kuitenkin itse asiassa jointtien siirtämistä yms, joten on tärkeää testata
        // - Tai mäppäys voi olla suoraviivainen, mutta sen käsittely on jotain muuta kuin mäppäystä
        // eli tarvitaan
        // - jointtien sijainnit raiteilla
        // - raiteet
        // tulos
        // - jointtien sijainnit edgeillä
        /*
        Onko tässä enää merkitystä vaihderakenteella?
         */

        // TODO: Tämä testi ei ole tällaisenään kovin mielekäs
        // Mielenkiintoisia ovat edgen käsitelyt ennen varsinaista linkitystä
        // - silloin testataan jalostettuun mallin mäpättyjä malleja

        // init data
        val switchStructure = YV60_300_1_9_O()
        val track152 = createTrack(switchStructure, asJointNumbers(1, 5, 2))
        val track13 = createTrack(switchStructure, asJointNumbers(1, 3))

        val track152Id = track152.locationTrack.id as IntId
        val track13Id = track13.locationTrack.id as IntId

        val distance1to5 = switchJointDistance(switchStructure, 1, 5)
        val distance1to2 = switchAlignmentLength(switchStructure, 1, 2)
        val distance1to3 = switchAlignmentLength(switchStructure, 1, 3)

        val fitted =
            fittedSwitch(
                YV60_300_1_9_O(),
                fittedJointMatch(track152Id, joint = 1, m = 0.0),
                fittedJointMatch(track152Id, joint = 5, m = distance1to5),
                fittedJointMatch(track152Id, joint = 2, m = distance1to2),
                fittedJointMatch(track13Id, joint = 1, m = 0.0),
                fittedJointMatch(track13Id, joint = 3, m = distance1to3),
            )

        // map on edges
        val fittingOnEdges =
            mapFittedSwitchToEdges(
                fitted,
                listOf(track152.locationTrack to track152.geometry, track13.locationTrack to track13.geometry),
            )

        // assert
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

fun assertMatchCount(fitted: FittedSwitch, matchCount: Int) {
    assertEquals(matchCount, fitted.joints.sumOf { joint -> joint.matches.size }, "Match count does not match!")
}

/** Utility function to hide fitting implementation details. */
fun fitSwitch(
    point: IPoint,
    switchStructure: SwitchStructureData,
    nearbyLocationTracks: List<TrackForSwitchFitting>,
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
