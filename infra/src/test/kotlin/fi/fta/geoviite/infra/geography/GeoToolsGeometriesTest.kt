package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.math.Point
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.locationtech.jts.geom.Coordinate as JtsCoordinate

class GeoToolsGeometriesTest {

    // Triangle with vertices at (0,0), (10,0), (5,10)
    private val triangle = toJtsPolygon(listOf(Point(0.0, 0.0), Point(10.0, 0.0), Point(5.0, 10.0)))

    @Test
    fun `point inside triangle returns true`() {
        val point = toJtsGeoPoint(JtsCoordinate(5.0, 3.0))
        assertTrue(jtsTriangleContainsPoint(triangle, point))
    }

    @Test
    fun `point on edge of triangle returns true`() {
        val point = toJtsGeoPoint(JtsCoordinate(5.0, 0.0))
        assertTrue(jtsTriangleContainsPoint(triangle, point))
    }

    @Test
    fun `point on vertex of triangle returns true`() {
        val point = toJtsGeoPoint(JtsCoordinate(0.0, 0.0))
        assertTrue(jtsTriangleContainsPoint(triangle, point))
    }

    @Test
    fun `point outside triangle returns false`() {
        val point = toJtsGeoPoint(JtsCoordinate(20.0, 20.0))
        assertFalse(jtsTriangleContainsPoint(triangle, point))
    }

    @Test
    fun `non-triangle polygon throws`() {
        val square = toJtsPolygon(listOf(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0)))
        val point = toJtsGeoPoint(JtsCoordinate(5.0, 5.0))
        assertThrows<IllegalArgumentException> { jtsTriangleContainsPoint(square, point) }
    }
}
