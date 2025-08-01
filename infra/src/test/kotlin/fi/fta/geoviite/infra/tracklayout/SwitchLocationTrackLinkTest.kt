package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SwitchLocationTrackLinkTest {

    @Test
    fun `getDuplicateMatches finds full match in the middle`() {
        // Main:  0    1    2    3    4    5
        //       1-1..1-2..2-1..2-2..3-1..3-2
        // Dupl:            0    1    2
        //                 2-1..2-2..3-1
        assertEquals(
            expected = listOf(fullMatch(2 to 1, 3 to 1)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(2 to 1, 2 to 2, 3 to 1),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `getDuplicateMatches finds full match in the beginning`() {
        // Main:  0    1    2    3    4    5
        //       1-1..1-2..2-1..2-2..3-1..3-2
        // Dupl:  0    1
        //       1-1..1-2
        assertEquals(
            expected = listOf(fullMatch(1 to 1, 1 to 2)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(1 to 1, 1 to 2),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `getDuplicateMatches finds full match in the end`() {
        // Main:  0    1    2    3    4    5
        //       1-1..1-2..2-1..2-2..3-1..3-2
        // Dupl:                      0    1
        //                           3-1..3-2
        assertEquals(
            expected = listOf(fullMatch(3 to 1, 3 to 2)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(3 to 1, 3 to 2),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `getDuplicateMatches finds partial match that separates at end`() {
        // Main:  0    1    2    3    4    5
        //       1-1..1-2..2-1..2-2..3-1..3-2
        // Dupl:  0    1    2    3
        //       1-1..1-2..5-1..5-2
        assertEquals(
            expected = listOf(partialMatch(1 to 1, 1 to 2)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(1 to 1, 1 to 2, 5 to 1, 5 to 2),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `getDuplicateMatches finds partial match that separates in both directions`() {
        // Main:  0    1    2    3    4    5
        //       1-1..1-2..2-1..2-2..3-1..3-2
        // Dupl:  0    1    2    3
        //       5-1..1-2..2-1..6-2
        assertEquals(
            expected = listOf(partialMatch(1 to 2, 2 to 1, from = 1)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(5 to 1, 1 to 2, 2 to 1, 6 to 2),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `getDuplicateMatches finds partial match that separates at beginning`() {
        // Main:  0    1    2    3    4    5
        //       1-1..1-2..2-1..2-2..3-1..3-2
        // Dupl:            0    1    2    3
        //                 5-1..2-2..3-1..3-2
        assertEquals(
            expected = listOf(partialMatch(2 to 2, 3 to 2, from = 1)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(5 to 1, 2 to 2, 3 to 1, 3 to 2),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `getDuplicateMatches breaks full match into two if there's a difference in the middle`() {
        // Main:  0    1    2    3    4    5
        //       1-1..1-2..2-1..2-2..3-1..3-2
        // Dupl:  0    1         2    3
        //       1-1..1-2..5-5..2-2..3-1
        assertEquals(
            expected = listOf(partialMatch(1 to 1, 1 to 2), partialMatch(2 to 2, 3 to 1, from = 2)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 2, 3 to 1),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `getDuplicateMatches breaks full match into two if main lacks a joint`() {
        // Main:  0    1         2    3    4
        //       1-1..1-2.......2-2..3-1..3-2
        // Dupl:  0    1    2    3    4
        //       1-1..1-2..2-1..2-2..3-1
        assertEquals(
            expected = listOf(partialMatch(1 to 1, 1 to 2), partialMatch(2 to 2, 3 to 1, from = 3)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `getDuplicateMatches breaks full match into two if duplicate lacks a joint`() {
        // Main:  0    1    2    3    4    5
        //       1-1..1-2..2-1..2-2..3-1..3-2
        // Dupl:  0    1         2    3
        //       1-1..1-2.......2-2..3-1
        assertEquals(
            expected = listOf(partialMatch(1 to 1, 1 to 2), partialMatch(2 to 2, 3 to 1, from = 2)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 2, 3 to 1),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `Lone extra match does not affect the result`() {
        // Main:  0    1    2    3    4    5
        //       1-1..1-2..2-1..2-2..3-1..3-2
        // Dupl:  0    1              2
        //       1-1..1-2............3-1
        assertEquals(
            expected = listOf(partialMatch(1 to 1, 1 to 2)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(1 to 1, 1 to 2, 3 to 1),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `Spatially matching endpoints are included into duplicate match`() {
        // Main: 0      1    2    3
        //       start..1-2..2-1..end
        // Dupl: 0      1    2    3
        //       start..1-2..2-1..end
        val start = Point(0.0, 0.0)
        val end = Point(10.0, 0.0)
        assertEquals(
            expected =
                listOf(
                    0 to
                        DuplicateStatus(
                            DuplicateMatch.FULL,
                            duplicateOfId = null,
                            startSplitPoint = startPoint(start),
                            endSplitPoint = endPoint(end),
                            overlappingLength = 0.0,
                        )
                ),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints =
                        listOf(startPoint(start), switchSplitPoint(1, 2), switchSplitPoint(2, 1), endPoint(end)),
                    duplicateTrackSplitPoints =
                        listOf(startPoint(start), switchSplitPoint(1, 2), switchSplitPoint(2, 1), endPoint(end)),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `Spatially non-matching endpoints are excluded from duplicate match`() {
        // Main: 0            1    2    3
        //       start........1-2..2-1..end
        // Dupl: 0            1    2    3
        //       other-start..1-2..2-1..other-end
        val start = Point(0.0, 0.0)
        val end = Point(10.0, 0.0)
        val otherStart = Point(2.5, 0.0)
        val otherEnd = Point(12.5, 0.0)
        assertEquals(
            expected =
                listOf(
                    1 to
                        DuplicateStatus(
                            DuplicateMatch.PARTIAL,
                            duplicateOfId = null,
                            startSplitPoint = switchSplitPoint(1, 2),
                            endSplitPoint = switchSplitPoint(2, 1),
                            overlappingLength = 0.0,
                        )
                ),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints =
                        listOf(startPoint(start), switchSplitPoint(1, 2), switchSplitPoint(2, 1), endPoint(end)),
                    duplicateTrackSplitPoints =
                        listOf(
                            startPoint(otherStart),
                            switchSplitPoint(1, 2),
                            switchSplitPoint(2, 1),
                            endPoint(otherEnd),
                        ),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }

    @Test
    fun `Same endpoint results a hit`() {
        // Main:  0    1    2    3    4    5
        //       start..1-2..2-1..2-2..3-1..3-2
        // Dupl:  0    1              2
        //       start..1-2............3-1
        assertEquals(
            expected = listOf(partialMatch(1 to 1, 1 to 2)),
            actual =
                getDuplicateMatches(
                    mainTrackSplitPoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                    duplicateTrackSplitPoints = matchRange(1 to 1, 1 to 2, 3 to 1),
                    mainTrackId = StringId(),
                    duplicateOf = null,
                ),
        )
    }
}

fun emptyPoint() = AlignmentPoint(0.0, 0.0, 0.0, LineM<LocationTrackM>(0.0), 0.0)

fun partialMatch(
    startSwitch: Pair<Int, Int>,
    endSwitch: Pair<Int, Int>,
    from: Int = 0,
    startPoint: AlignmentPoint<LocationTrackM> = emptyPoint(),
    endPoint: AlignmentPoint<LocationTrackM> = emptyPoint(),
) =
    from to
        DuplicateStatus(
            DuplicateMatch.PARTIAL,
            null,
            startSplitPoint =
                SwitchSplitPoint(startPoint, null, IntId(startSwitch.first), JointNumber(startSwitch.second)),
            endSplitPoint = SwitchSplitPoint(endPoint, null, IntId(endSwitch.first), JointNumber(endSwitch.second)),
            overlappingLength = 0.0,
        )

fun fullMatch(
    startSwitch: Pair<Int, Int>,
    endSwitch: Pair<Int, Int>,
    from: Int = 0,
    startPoint: AlignmentPoint<LocationTrackM> = emptyPoint(),
    endPoint: AlignmentPoint<LocationTrackM> = emptyPoint(),
) =
    from to
        DuplicateStatus(
            DuplicateMatch.FULL,
            null,
            startSplitPoint =
                SwitchSplitPoint(startPoint, null, IntId(startSwitch.first), JointNumber(startSwitch.second)),
            endSplitPoint = SwitchSplitPoint(endPoint, null, IntId(endSwitch.first), JointNumber(endSwitch.second)),
            overlappingLength = 0.0,
        )

fun startPoint(point: IPoint): EndpointSplitPoint {
    return EndpointSplitPoint(
        location = AlignmentPoint(point.x, point.y, null, LineM(0.0), null),
        address = null,
        DuplicateEndPointType.START,
    )
}

fun endPoint(point: IPoint): EndpointSplitPoint {
    return EndpointSplitPoint(
        location = AlignmentPoint(point.x, point.y, null, LineM(0.0), null),
        address = null,
        DuplicateEndPointType.END,
    )
}

fun switchSplitPoint(switchId: Int, joint: Int): SwitchSplitPoint {
    return SwitchSplitPoint(emptyPoint(), null, IntId(switchId), JointNumber(joint))
}

fun matchRange(vararg switchToJoint: Pair<Int, Int>): List<SplitPoint> =
    switchToJoint.map { (id, joint) -> SwitchSplitPoint(emptyPoint(), null, IntId(id), JointNumber(joint)) }
