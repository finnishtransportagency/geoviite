package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayoutSwitchTest {

    @Test
    fun `Switch area intersection checks its joints`() {
        val area =
            Polygon(
                Point(0.0, 0.0),
                Point(10.0, 0.0),
                Point(10.0, 10.0),
                Point(0.0, 10.0),
                Point(0.0, 0.0),
            )

        assertTrue(switchAt(Point(5.0, 5.0)).hasJointIntersecting(area))
        assertTrue(switchAt(Point(10.0, 10.0)).hasJointIntersecting(area))
        assertTrue(switchAt(Point(15.0, 15.0), Point(5.0, 5.0)).hasJointIntersecting(area))
        assertFalse(switchAt(Point(15.0, 15.0)).hasJointIntersecting(area))
        assertFalse(switchAt().hasJointIntersecting(area))
    }

    private fun switchAt(vararg locations: Point) =
        switch(
            joints =
                locations.mapIndexed { index, location ->
                    LayoutSwitchJoint(JointNumber(index + 1), SwitchJointRole.MAIN, location, null)
                }
        )
}
