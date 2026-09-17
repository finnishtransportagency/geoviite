package fi.fta.geoviite.infra.math

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PolygonTest {

    @Test
    fun `line crossing polygon intersects`() {
        val square = Polygon(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(0.0, 0.0))
        assertTrue(square.intersects(listOf(Point(-5.0, 5.0), Point(15.0, 5.0))))
    }

    @Test
    fun `line fully outside polygon does not intersect`() {
        val square = Polygon(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(0.0, 0.0))
        assertFalse(square.intersects(listOf(Point(20.0, 20.0), Point(30.0, 30.0))))
    }

    @Test
    fun `line fully inside polygon intersects`() {
        val square = Polygon(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(0.0, 0.0))
        assertTrue(square.intersects(listOf(Point(2.0, 2.0), Point(8.0, 8.0))))
    }

    @Test
    fun `line touching polygon edge intersects`() {
        val square = Polygon(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(0.0, 0.0))
        assertTrue(square.intersects(listOf(Point(10.0, 5.0), Point(20.0, 5.0))))
    }

    @Test
    fun `single point inside polygon intersects`() {
        val square = Polygon(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(0.0, 0.0))
        assertTrue(square.intersects(listOf(Point(5.0, 5.0))))
    }

    @Test
    fun `single point outside polygon does not intersect`() {
        val square = Polygon(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(0.0, 0.0))
        assertFalse(square.intersects(listOf(Point(50.0, 50.0))))
    }

    @Test
    fun `empty point list does not intersect`() {
        val square = Polygon(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(0.0, 0.0))
        assertFalse(square.intersects(listOf()))
    }
}
