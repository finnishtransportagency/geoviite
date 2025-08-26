package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

// NOTE:
// This switch may contain inaccurate location for joint point 3 as
// that point was not marked in the IM file but was reasoned by a developer.

fun YV43_530_1_15_O() =
    SwitchStructureData(
        type = SwitchType.of("YV43-530-1:15-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(15.802, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(43.195, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(42.984, -1.689)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(15.802, 0.0)),
                            SwitchStructureLine(start = Point(15.802, 0.0), end = Point(43.195, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(35.256, -1.174), radius = 530.0),
                            SwitchStructureLine(start = Point(35.256, -1.174), end = Point(42.984, -1.689)),
                        ),
                ),
            ),
    )

fun YV43_530_1_15_V() = YV43_530_1_15_O().flipAlongYAxis().copy(type = SwitchType.of("YV43-530-1:15-V"))
