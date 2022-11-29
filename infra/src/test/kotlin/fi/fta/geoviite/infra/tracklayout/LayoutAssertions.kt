package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertNull

private const val COORDINATE_DELTA: Double = 0.000000001
private const val LENGTH_DELTA: Double = 0.00001
private const val HEIGHT_DELTA: Double = 0.000001
private const val CANT_DELTA: Double = 0.000001

fun assertMatches(expected: TrackLayoutTrackNumber, actual: TrackLayoutTrackNumber, idMatch: Boolean = false) {
    if (idMatch) {
        assertEquals(expected, actual)
    } else {
        val unified = actual.copy(
            id = expected.id,
            dataType = expected.dataType,
            version = expected.version,
        )
        assertEquals(expected, unified)
    }
}

fun assertMatches(expected: ReferenceLine, actual: ReferenceLine, idMatch: Boolean = false) {
    val expectedWithSameFloats = expected.copy(
        boundingBox = actual.boundingBox,
        length = actual.length,
    )
    if (idMatch) {
        assertEquals(expectedWithSameFloats, actual)
    } else {
        val unified = actual.copy(
            id = expected.id,
            dataType = expected.dataType,
            draft = expected.draft,
            version = expected.version,
        )
        assertEquals(expectedWithSameFloats, unified)
        assertEquals(expected.sourceId != null, actual.sourceId != null)
        assertEquals(expected.draft != null, actual.draft != null)
    }
    assertEquals(expected.length, actual.length, LENGTH_DELTA)
}

fun assertMatches(expected: LocationTrack, actual: LocationTrack, idMatch: Boolean = false) {
    val expectedWithSameFloats = expected.copy(
        boundingBox = actual.boundingBox,
        length = actual.length,
    )
    if (idMatch) {
        assertEquals(expectedWithSameFloats, actual)
    } else {
        val unified = actual.copy(
            id = expected.id,
            dataType = expected.dataType,
            draft = expected.draft,
            version = expected.version,
        )
        assertEquals(expectedWithSameFloats, unified)
        assertEquals(expected.sourceId != null, actual.sourceId != null)
        assertEquals(expected.draft != null, actual.draft != null)
    }
    assertEquals(expected.length, actual.length, LENGTH_DELTA)
}

fun assertMatches(expected: LayoutAlignment, actual: LayoutAlignment, idMatch: Boolean = false) {
    val expectedWithSameFloats = expected.copy(
        segments = actual.segments,
    )
    if (idMatch) {
        assertEquals(expectedWithSameFloats, actual)
    } else {
        val unified = actual.copy(
            id = expected.id,
            dataType = expected.dataType,
        )
        assertEquals(expectedWithSameFloats, unified)
        assertEquals(expected.sourceId != null, actual.sourceId != null)
    }
    assertEquals(expected.length, actual.length, LENGTH_DELTA)
    assertEquals(expected.segments.size, actual.segments.size)
    expected.segments.forEachIndexed { index, expectedSegment ->
        assertMatches(expectedSegment, actual.segments[index], idMatch)
    }
}

fun assertMatches(expected: LayoutSegment, actual: LayoutSegment, idMatch: Boolean = false) {
    val expectedWithSameFloats = expected.copy(points = actual.points, start = actual.start)
    if (idMatch) {
        assertEquals(expectedWithSameFloats, actual)
    } else {
        assertEquals(expectedWithSameFloats, actual.copy(id = expected.id, sourceId = expected.sourceId))
        assertEquals(expected.sourceId != null, actual.sourceId != null)
    }
    assertEquals(expected.start, actual.start, LENGTH_DELTA)
    assertEquals(expected.length, actual.length, LENGTH_DELTA)
    assertEquals(expected.points.size, actual.points.size)
    expected.points.forEachIndexed { index, point -> assertMatches(point, actual.points[index]) }
}

fun assertMatches(expected: LayoutPoint, actual: LayoutPoint) {
    assertEquals(expected.x, actual.x, COORDINATE_DELTA)
    assertEquals(expected.y, actual.y, COORDINATE_DELTA)
    assertEquals(expected.m, actual.m, COORDINATE_DELTA)
    if (expected.z == null) assertNull(actual.z)
    else assertEquals(expected.z!!, actual.z!!, HEIGHT_DELTA)
    if (expected.cant == null) assertNull(actual.cant)
    else assertEquals(expected.cant!!, actual.cant!!, CANT_DELTA)
}

fun assertMatches(expected: TrackLayoutKmPost, actual: TrackLayoutKmPost, idMatch: Boolean = false) {
    if (idMatch) {
        assertEquals(expected, actual)
    } else {
        val unified = actual.copy(
            id = expected.id,
            sourceId = expected.sourceId,
            dataType = expected.dataType,
            draft = expected.draft,
            version = expected.version,
        )
        assertEquals(expected, unified)
        assertEquals(expected.sourceId != null, actual.sourceId != null)
        assertEquals(expected.draft != null, actual.draft != null)
    }
    assertEquals(expected.location == null, actual.location == null)
    if (expected.location != null) {
        assertApproximatelyEquals(expected.location!!, actual.location!!, COORDINATE_DELTA)
    }
}

fun assertMatches(expected: TrackLayoutSwitch, actual: TrackLayoutSwitch, idMatch: Boolean = false) {
    val expectedWithSameFloats = expected.copy(joints = actual.joints, switchStructureId = actual.switchStructureId)
    if (idMatch) {
        assertEquals(expectedWithSameFloats, actual)
    } else {
        val unified = actual.copy(
            id = expected.id,
            dataType = expected.dataType,
            draft = expected.draft,
            version = expected.version,
        )
        assertEquals(expectedWithSameFloats, unified)
        assertEquals(expected.sourceId != null, actual.sourceId != null)
        assertEquals(expected.draft != null, actual.draft != null)
    }
    assertEquals(expected.switchStructureId, actual.switchStructureId)
    assertEquals(expected.joints.size, actual.joints.size)
    val expectedJoints = expected.joints.sortedBy(TrackLayoutSwitchJoint::number)
    expectedJoints.forEachIndexed { index, expJoint -> assertMatches(expJoint, actual.joints[index]) }
}

fun assertMatches(expected: TrackLayoutSwitchJoint, actual: TrackLayoutSwitchJoint) {
    assertEquals(expected, actual.copy(location = expected.location))
    assertApproximatelyEquals(expected.location, actual.location, COORDINATE_DELTA)
}
