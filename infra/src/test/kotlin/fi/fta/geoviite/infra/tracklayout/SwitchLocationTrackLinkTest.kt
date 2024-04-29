package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.StringId
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
            expected = listOf(fullMatch(2, 3)),
            actual = getDuplicateMatches(
                mainTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                duplicateTrackJoints = matchRange(2 to 1, 2 to 2, 3 to 1),
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
            expected = listOf(fullMatch(1, 1)),
            actual = getDuplicateMatches(
                mainTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                duplicateTrackJoints = matchRange(1 to 1, 1 to 2),
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
            expected = listOf(fullMatch(3, 3)),
            actual = getDuplicateMatches(
                mainTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                duplicateTrackJoints = matchRange(3 to 1, 3 to 2),
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
            expected = listOf(partialMatch(1, 1)),
            actual = getDuplicateMatches(
                mainTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                duplicateTrackJoints = matchRange(1 to 1, 1 to 2, 5 to 1, 5 to 2),
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
            expected = listOf(partialMatch(1, 2, from = 1)),
            actual = getDuplicateMatches(
                mainTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                duplicateTrackJoints = matchRange(5 to 1, 1 to 2, 2 to 1, 6 to 2),
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
            expected = listOf(partialMatch(2, 3, from = 1)),
            actual = getDuplicateMatches(
                mainTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                duplicateTrackJoints = matchRange(5 to 1, 2 to 2, 3 to 1, 3 to 2),
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
            expected = listOf(
                partialMatch(1, 1),
                partialMatch(2, 3, from = 2),
            ),
            actual = getDuplicateMatches(
                mainTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                duplicateTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 2, 3 to 1),
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
            expected = listOf(
                partialMatch(1, 1),
                partialMatch(2, 3, from = 3),
            ),
            actual = getDuplicateMatches(
                mainTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 2, 3 to 1, 3 to 2),
                duplicateTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1),
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
            expected = listOf(
                partialMatch(1, 1),
                partialMatch(2, 3, from = 2),
            ),
            actual = getDuplicateMatches(
                mainTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                duplicateTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 2, 3 to 1),
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
            expected = listOf(partialMatch(1, 1)),
            actual = getDuplicateMatches(
                mainTrackJoints = matchRange(1 to 1, 1 to 2, 2 to 1, 2 to 2, 3 to 1, 3 to 2),
                duplicateTrackJoints = matchRange(1 to 1, 1 to 2, 3 to 1),
                mainTrackId = StringId(),
                duplicateOf = null,
            ),
        )
    }
}

fun emptyPoint() = AlignmentPoint(0.0, 0.0, 0.0, 0.0, 0.0)

fun partialMatch(
    startSwitch: Int,
    endSwitch: Int,
    from: Int = 0,
    startPoint: AlignmentPoint = emptyPoint(),
    endPoint: AlignmentPoint = emptyPoint(),
) = from to DuplicateStatus(
    DuplicateMatch.PARTIAL, null, IntId(startSwitch), IntId(endSwitch), startPoint, endPoint
)

fun fullMatch(
    startSwitch: Int,
    endSwitch: Int,
    from: Int = 0,
    startPoint: AlignmentPoint = emptyPoint(),
    endPoint: AlignmentPoint = emptyPoint(),
) = from to DuplicateStatus(
    DuplicateMatch.FULL, null, IntId(startSwitch), IntId(endSwitch), startPoint, endPoint
)

fun matchRange(vararg switchToJoint: Pair<Int, Int>): List<SwitchJointOnTrack> = switchToJoint.map { (id, joint) ->
    SwitchJointOnTrack(
        IntId(id), JointNumber(joint), emptyPoint()
    )
}
