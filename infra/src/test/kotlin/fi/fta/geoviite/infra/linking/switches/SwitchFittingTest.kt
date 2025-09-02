package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.asSwitchStructure
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationTrackName
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.data.YV60_300_1_9_O
import fi.fta.geoviite.infra.tracklayout.LayoutRowId
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.MainOfficialContextData
import fi.fta.geoviite.infra.tracklayout.StoredAssetId
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.assertEquals
import fi.fta.geoviite.infra.tracklayout.combineEdges
import fi.fta.geoviite.infra.tracklayout.locationTrackM
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

data class TrackForSwitchFitting(
    val byJoints: List<JointNumber>,
    val locationTrack: LocationTrack,
    val geometry: LocationTrackGeometry,
) {
    val name = locationTrack.name
    val locationTrackId = locationTrack.id as IntId
    val trackAndGeometry = locationTrack to geometry
    val length = geometry.length

    fun asNew(name: String): TrackForSwitchFitting {
        val newId = locationTrackIdByName(name)
        // must fake a stored asset ID because the switch linking code implements its own version-based locking
        val contextData =
            MainOfficialContextData(StoredAssetId(LayoutRowVersion(LayoutRowId(newId, LayoutBranch.main.official), 1)))
        return copy(locationTrack = locationTrack.copy(contextData = contextData, name = LocationTrackName(name)))
    }

    fun startPoint(): IPoint {
        return requireNotNull(geometry.start)
    }

    fun getPointAtM(m: LineM<LocationTrackM>): IPoint? {
        return geometry.getPointAtM(m)
    }

    fun setTrackNumber(trackNumberId: IntId<LayoutTrackNumber>): TrackForSwitchFitting {
        return TrackForSwitchFitting(byJoints, locationTrack.copy(trackNumberId = trackNumberId), geometry)
    }

    fun cutFromStart(length: LineM<LocationTrackM>): TrackForSwitchFitting {
        return cutFromStart(locationTrack, geometry, length).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }

    fun cutFromEnd(length: LineM<LocationTrackM>): TrackForSwitchFitting {
        return cutFromEnd(locationTrack, geometry, length).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }

    fun expandFromStart(length: LineM<LocationTrackM>): TrackForSwitchFitting {
        return expandTrackFromStart(locationTrack, geometry, length).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }

    fun expandFromEnd(length: LineM<LocationTrackM>): TrackForSwitchFitting {
        return expandTrackFromEnd(locationTrack, geometry, length).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }

    fun moveForward(distance: Double): TrackForSwitchFitting {
        return moveTrackForward(locationTrack, geometry, distance).let { (locationTrack, geometry) ->
            this.copy(locationTrack = locationTrack, geometry = geometry)
        }
    }

    fun moveBackward(distance: Double): TrackForSwitchFitting {
        return moveForward(-distance)
    }

    fun withSwitch(
        switchId: IntId<LayoutSwitch>,
        switchStructure: SwitchStructureData,
        vararg joints: TrackForSwitchFittingJointDescription,
    ): TrackForSwitchFitting {
        val jointsByEdgeIndex =
            joints
                .map { joint ->
                    val mOnTrack =
                        when (joint.location) {
                            is PlaceInnerJointAtTrackStart,
                            is PlaceTopologicalJointAtTrackStart -> LineM(0.0)
                            is PlaceInnerJointAtTrackEnd,
                            is PlaceTopologicalJointAtTrackEnd -> geometry.length
                            is PlaceInnerJointAtM -> joint.location.mOnTrack
                        }
                    val (edge, mRange) = geometry.getEdgeAtMOrThrow(mOnTrack)
                    val edgeIndex = geometry.edges.indexOf(edge)
                    val mOnEdge = mOnTrack - mRange.min
                    edgeIndex to
                        SwitchLinkingJoint(
                            mOnEdge.castToDifferentM(),
                            joint.jointNumber,
                            edge.getPointAtM(mOnEdge.castToDifferentM())!!.toPoint(),
                        )
                }
                .groupBy({ it.first }, { it.second })
        val newEdges =
            geometry.edges.flatMapIndexed { index, edge ->
                jointsByEdgeIndex[index]?.let { joints -> linkJointsToEdge(switchId, switchStructure, edge, joints) }
                    ?: listOf(edge)
            }
        val newGeometry = TmpLocationTrackGeometry.of(combineEdges(newEdges), geometry.trackId)
        return this.copy(geometry = newGeometry)
    }
}

sealed class TrackForSwitchFittingJointLocation

data object PlaceInnerJointAtTrackStart : TrackForSwitchFittingJointLocation()

data object PlaceInnerJointAtTrackEnd : TrackForSwitchFittingJointLocation()

data object PlaceTopologicalJointAtTrackStart : TrackForSwitchFittingJointLocation()

data object PlaceTopologicalJointAtTrackEnd : TrackForSwitchFittingJointLocation()

data class PlaceInnerJointAtM(val mOnTrack: LineM<LocationTrackM>) : TrackForSwitchFittingJointLocation()

data class TrackForSwitchFittingJointDescription(
    val location: TrackForSwitchFittingJointLocation,
    val jointNumber: JointNumber,
)

fun innerJointAtStart(jointNumber: Int) =
    TrackForSwitchFittingJointDescription(PlaceInnerJointAtTrackStart, JointNumber(jointNumber))

fun topologicalJointAtStart(jointNumber: Int) =
    TrackForSwitchFittingJointDescription(PlaceTopologicalJointAtTrackStart, JointNumber(jointNumber))

fun innerJointAtEnd(jointNumber: Int) =
    TrackForSwitchFittingJointDescription(PlaceInnerJointAtTrackEnd, JointNumber(jointNumber))

fun topologicalJointAtEnd(jointNumber: Int) =
    TrackForSwitchFittingJointDescription(PlaceTopologicalJointAtTrackEnd, JointNumber(jointNumber))

fun innerJointAtM(mOnTrack: LineM<LocationTrackM>, jointNumber: Int) =
    TrackForSwitchFittingJointDescription(PlaceInnerJointAtM(mOnTrack), JointNumber(jointNumber))

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
        assertJoint(fitted, 1, track152.locationTrack, LineM(0.0))
        assertJoint(fitted, 5, track152.locationTrack, LineM(distance1to5))
        assertJoint(fitted, 2, track152.locationTrack, LineM(distance1to2))
        assertJoint(fitted, 1, track13.locationTrack, LineM(0.0))
        assertJoint(fitted, 3, track13.locationTrack, LineM(distance1to3), absoluteMPrecision = 0.01)
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
                .expandFromStart(LineM(extraTrackLength))
                .expandFromEnd(LineM(extraTrackLength))
        val track13 = createTrack(switchStructure, asJointNumbers(1, 3))

        // fit switch
        val targetPoint = track152.getPointAtM(LineM(extraTrackLength))!!
        val fitted = fitSwitch(targetPoint, switchStructure, listOf(track152, track13))

        // assert
        val distance1to5 = switchJointDistance(switchStructure, 1, 5)
        val distance1to2 = switchAlignmentLength(switchStructure, 1, 2)
        val distance1to3 = switchAlignmentLength(switchStructure, 1, 3)
        assertJoint(fitted, 1, track152.locationTrack, expectedM = LineM(0.0 + extraTrackLength))
        assertJoint(fitted, 5, track152.locationTrack, expectedM = LineM(distance1to5 + extraTrackLength))
        assertJoint(fitted, 2, track152.locationTrack, expectedM = LineM(distance1to2 + extraTrackLength))
        assertJoint(fitted, 1, track13.locationTrack, LineM(0.0))
        assertJoint(fitted, 3, track13.locationTrack, expectedM = LineM(distance1to3), absoluteMPrecision = 0.01)
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
        assertJoint(fitted, 1, trackA.locationTrack, expectedM = LineM(0.0))
        assertJoint(fitted, 5, trackA.locationTrack, expectedM = LineM(distance1to5))
        assertJoint(fitted, 2, trackA.locationTrack, expectedM = LineM(distance1to2))
        assertJoint(fitted, 1, trackC.locationTrack, expectedM = LineM(0.0))
        assertJoint(fitted, 3, trackC.locationTrack, expectedM = LineM(distance1to3), absoluteMPrecision = 0.01)
        assertJoint(fitted, 1, trackB.locationTrack, expectedM = LineM(trackBLength))
        assertMatchCount(fitted, 6)
    }

    @Test
    fun `Switch fitting should snap to the segments when near enough `() {
        // track A   track C
        //  2 |    / 3
        //    |   /
        //  5 |  /
        //    | /
        //  1 T/ 1
        //    |
        //    |
        // track B
        //
        // In this case geometry is not optimal.
        // Track B ends slightly after joint 1.
        // Track A continues from track B.
        //
        // There should be matches:
        // - joint 1 for track B
        // - joint 1, 5, 2 for track A
        // - joint 1, 3 for track C

        // init data
        val switchStructure = YV60_300_1_9_O()
        val positioningErrorInMeters = 0.1
        val trackBLength = 20.0
        val trackA = createTrack(switchStructure, asJointNumbers(1, 5, 2)).cutFromStart(LineM(positioningErrorInMeters))
        val trackB = createPrependingTrack(trackA, trackBLength, "track B")
        val trackC = createTrack(switchStructure, asJointNumbers(1, 3))

        // fit switch
        val targetPoint = trackC.geometry.start!!
        val fitted = fitSwitch(targetPoint, switchStructure, listOf(trackA, trackB, trackC))

        // assert
        val distance1to5 = switchJointDistance(switchStructure, 1, 5) - positioningErrorInMeters
        val distance1to2 = switchAlignmentLength(switchStructure, 1, 2) - positioningErrorInMeters
        val distance1to3 = trackC.length
        assertJoint(fitted, 1, trackA.locationTrack, expectedM = LineM(0.0))
        assertJoint(fitted, 5, trackA.locationTrack, expectedM = LineM(distance1to5))
        assertJoint(fitted, 2, trackA.locationTrack, expectedM = LineM(distance1to2))
        assertJoint(fitted, 1, trackC.locationTrack, expectedM = LineM(0.0))
        assertJoint(fitted, 3, trackC.locationTrack, expectedM = LineM(distance1to3.distance))

        // TODO: Selvitettävä vielä, vaikka todennäköisesti tällä ei ole juuri merkitystä.
        /*
        Eli track B:n osalta snäppäystä ei tällä hetkellä tapahdu, vaan getBestMatchesForJoint-funktio
        suodattaa pois snpäpätyn matchin, jolloin jää jäljelle snäppäämätön match. Näin tapahtuu, koska tällä
        raiteella on match vain yhteen jointtiin (numero 1), jolloin joint 1 on sekä start että end joint,
        jolloin end tyyppinen match suodattuu pois.
        */
        // assertJoint(fitted, 1, trackB.locationTrack, expectedM = trackBLength)
    }

    @Test
    fun `Switch fitting should recognize direction of the track`() {
        // track A   track B
        //  2 |    / 3
        //    ↓   /
        //  5 |  /
        //    | /
        //  1 |/ 1
        //
        // Track A goes against switch structure alignment.

        // init data
        val switchStructure = YV60_300_1_9_O()
        val trackA = createTrack(switchStructure, asJointNumbers(2, 5, 1)) // Note! reverse order 2-5-1
        val trackB = createTrack(switchStructure, asJointNumbers(1, 3))

        // fit switch
        val targetPoint = trackA.geometry.start!!
        val fitted = fitSwitch(targetPoint, switchStructure, listOf(trackA, trackB))

        // assert
        val expectedMValueJoint2 = locationTrackM(0.0)
        val expectedMValueJoint5 = trackA.length - switchJointDistance(switchStructure, 1, 5)
        val expectedMValueJoint1 = trackA.length
        val expectedMValueJoint3 = trackB.length
        assertJoint(
            fitted,
            2,
            trackA.locationTrack,
            expectedM = expectedMValueJoint2,
            direction = RelativeDirection.Against,
        )
        assertJoint(
            fitted,
            5,
            trackA.locationTrack,
            expectedM = expectedMValueJoint5,
            direction = RelativeDirection.Against,
        )
        assertJoint(
            fitted,
            1,
            trackA.locationTrack,
            expectedM = expectedMValueJoint1,
            direction = RelativeDirection.Against,
        )
        assertJoint(fitted, 1, trackB.locationTrack, expectedM = LineM(0.0))
        assertJoint(fitted, 3, trackB.locationTrack, expectedM = expectedMValueJoint3)
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

fun assertJoint(
    fitted: FittedSwitch,
    joint: Int,
    track: LocationTrack,
    expectedM: LineM<LocationTrackM>,
    direction: RelativeDirection = RelativeDirection.Along,
    absoluteMPrecision: Double = 0.001,
) {
    val jointNumber = JointNumber(joint)
    val fittedJoint = fitted.joints.first { fittedJoint -> fittedJoint.number == jointNumber }
    val matches = fittedJoint.matches.filter { match -> match.locationTrackId == track.id as IntId }
    assertEquals(1, matches.count(), "Expecting one match per location track \"${track.name}\" and joint $joint")
    val match = matches.first()
    assertEquals(
        expectedM,
        match.mOnTrack,
        absoluteMPrecision,
        "M-value is not matching for location track \"${track.name}\" and joint $joint",
    )
    assertEquals(
        direction,
        match.direction,
        "Direction is not matching for location track \"${track.name}\" and joint $joint",
    )
}

fun assertMatchCount(fitted: FittedSwitch, matchCount: Int) {
    assertEquals(matchCount, fitted.joints.sumOf { joint -> joint.matches.size }, "Match count does not match!")
}

fun removeDuplicateMatches(fittedSwitch: FittedSwitch): FittedSwitch {
    return fittedSwitch.copy(
        joints =
            fittedSwitch.joints.map { joint ->
                joint.copy(
                    matches =
                        joint.matches.distinctBy { match ->
                            listOf(match.locationTrackId, match.switchJoint, match.mOnTrack)
                        }
                )
            }
    )
}

/** Utility function for hiding fitting implementation details. */
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
        .let(::removeDuplicateMatches)
}
