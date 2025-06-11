package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Assertions.assertEquals

private const val COORDINATE_DELTA: Double = 0.000000001
private const val LENGTH_DELTA: Double = 0.00001
private const val HEIGHT_DELTA: Double = 0.000001
private const val CANT_DELTA: Double = 0.000001

fun assertMatches(expected: LayoutTrackNumber, actual: LayoutTrackNumber, contextMatch: Boolean = false) {
    if (contextMatch) {
        assertEquals(expected, actual)
    } else {
        val unified = actual.copy(contextData = expected.contextData)
        assertEquals(expected, unified)
    }
}

fun assertMatches(expected: ReferenceLine, actual: ReferenceLine, contextMatch: Boolean = false) {
    val expectedWithSameFloats = expected.copy(boundingBox = actual.boundingBox, length = actual.length)
    if (contextMatch) {
        assertEquals(expectedWithSameFloats, actual)
    } else {
        val unified = actual.copy(contextData = expected.contextData)
        assertEquals(expectedWithSameFloats, unified)
    }
    assertEquals(expected.length, actual.length, LENGTH_DELTA)
}

fun assertMatches(expected: LocationTrack, actual: LocationTrack, contextMatch: Boolean = false) {
    val expectedWithSameFloats = expected.copy(boundingBox = actual.boundingBox, length = actual.length)
    if (contextMatch) {
        assertEquals(expectedWithSameFloats, actual)
    } else {
        val unified = actual.copy(contextData = expected.contextData)
        assertEquals(expectedWithSameFloats, unified)
    }
    assertEquals(expected.length, actual.length, LENGTH_DELTA)
}

fun assertMatches(expected: DbLocationTrackNaming, actual: DbLocationTrackNaming) {
    assertEquals(expected.namingScheme, actual.namingScheme)
    assertEquals(expected.nameFreeText, actual.nameFreeText)
    assertEquals(expected.nameSpecifier, actual.nameSpecifier)
}

fun assertMatches(expected: DbLocationTrackDescription, actual: DbLocationTrackDescription) {
    assertEquals(expected.descriptionBase, actual.descriptionBase)
    assertEquals(expected.descriptionSuffix, actual.descriptionSuffix)
}

fun assertMatches(expected: LocationTrackGeometry, actual: LocationTrackGeometry, idMatch: Boolean = false) =
    if (idMatch) {
        assertEquals(expected, actual)
    } else {
        assertEquals(expected.edges.size, actual.edges.size)
        expected.edges.forEachIndexed { index, expectedEdge -> assertMatches(expectedEdge, actual.edges[index]) }
    }

fun assertMatches(expected: LayoutEdge, actual: LayoutEdge, idMatch: Boolean = false) =
    if (idMatch) {
        assertEquals(expected, actual)
    } else {
        assertMatches(expected.startNode, actual.startNode)
        assertMatches(expected.endNode, actual.endNode)
        assertEquals(expected.length, actual.length, LENGTH_DELTA)
        assertEquals(expected.segments.size, actual.segments.size)
        expected.segments.forEachIndexed { index, expectedSegment ->
            assertMatches(expectedSegment, actual.segments[index], idMatch)
        }
        expected.segmentMValues.forEachIndexed { index, m ->
            assertEquals(m.min, actual.segmentMValues[index].min, LENGTH_DELTA)
            assertEquals(m.max, actual.segmentMValues[index].max, LENGTH_DELTA)
        }
    }

fun assertMatches(expected: NodeConnection, actual: NodeConnection, idMatch: Boolean = false) {
    if (idMatch) {
        assertEquals(expected, actual)
    } else {
        assertEquals(expected.type, actual.type)
        if (expected.type == LayoutNodeType.SWITCH) {
            assertEquals(expected.switchIn, actual.switchIn)
            assertEquals(expected.switchOut, actual.switchOut)
        }
    }
}

fun assertMatches(expected: LayoutAlignment, actual: LayoutAlignment, idMatch: Boolean = false) {
    val expectedWithSameFloats = expected.copy(segments = actual.segments)
    if (idMatch) {
        assertEquals(expectedWithSameFloats, actual)
    } else {
        val unified = actual.copy(id = expected.id, dataType = expected.dataType)
        assertEquals(expectedWithSameFloats, unified)
    }
    assertEquals(expected.length, actual.length, LENGTH_DELTA)
    assertEquals(expected.segments.size, actual.segments.size)
    expected.segments.forEachIndexed { index, expectedSegment ->
        assertMatches(expectedSegment, actual.segments[index], idMatch)
    }
    expected.segmentMValues.forEachIndexed { index, m ->
        assertEquals(m.min, actual.segmentMValues[index].min, LENGTH_DELTA)
        assertEquals(m.max, actual.segmentMValues[index].max, LENGTH_DELTA)
    }
}

fun assertMatches(expected: LayoutSegment, actual: LayoutSegment, idMatch: Boolean = false) {
    val expectedWithSameFloats = expected.copy(geometry = actual.geometry)
    if (idMatch) {
        assertEquals(expectedWithSameFloats, actual)
    } else {
        assertEquals(expectedWithSameFloats, actual.copy(sourceId = expected.sourceId))
        assertEquals(expected.sourceId != null, actual.sourceId != null)
    }
    assertEquals(expected.length, actual.length, LENGTH_DELTA)
    assertEquals(expected.segmentPoints.size, actual.segmentPoints.size)
    assertEquals(expected.resolution, actual.resolution)
    expected.segmentPoints.forEachIndexed { index, point -> assertMatches(point, actual.segmentPoints[index]) }
}

fun assertMatches(expected: LayoutPoint, actual: LayoutPoint) {
    assertEquals(expected.x, actual.x, COORDINATE_DELTA)
    assertEquals(expected.y, actual.y, COORDINATE_DELTA)
    assertEquals(expected.m, actual.m, COORDINATE_DELTA)
    if (expected.z == null) assertNull(actual.z) else assertEquals(expected.z!!, actual.z!!, HEIGHT_DELTA)
    if (expected.cant == null) assertNull(actual.cant) else assertEquals(expected.cant!!, actual.cant!!, CANT_DELTA)
}

fun assertMatches(expected: LayoutKmPost, actual: LayoutKmPost, contextMatch: Boolean = false) {
    if (contextMatch) {
        assertEquals(expected, actual)
    } else {
        val unified = actual.copy(contextData = expected.contextData, sourceId = expected.sourceId)
        assertEquals(expected, unified)
        assertEquals(expected.sourceId != null, actual.sourceId != null)
    }
}

fun assertMatches(expected: LayoutSwitch, actual: LayoutSwitch, contextMatch: Boolean = false) {
    val expectedWithSameFloats = expected.copy(joints = actual.joints, switchStructureId = actual.switchStructureId)
    if (contextMatch) {
        assertEquals(expectedWithSameFloats, actual)
    } else {
        val unified = actual.copy(contextData = expected.contextData)
        assertEquals(expectedWithSameFloats, unified)
    }
    assertEquals(expected.switchStructureId, actual.switchStructureId)
    assertEquals(expected.joints.size, actual.joints.size)
    val expectedJoints = expected.joints.sortedBy(LayoutSwitchJoint::number)
    expectedJoints.forEachIndexed { index, expJoint -> assertMatches(expJoint, actual.joints[index]) }
}

fun assertMatches(expected: LayoutSwitchJoint, actual: LayoutSwitchJoint) {
    assertEquals(expected, actual.copy(location = expected.location))
    assertApproximatelyEquals(expected.location, actual.location, COORDINATE_DELTA)
}
