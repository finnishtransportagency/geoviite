package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun UKV60_600_281_1_9_O() =
    SwitchStructureData(
        type = SwitchType("UKV60-600/281-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(7), Point(8.921, 0.0)),
                SwitchStructureJoint(JointNumber(8), Point(14.135, 0.0)),
                SwitchStructureJoint(JointNumber(10), Point(28.254, -0.666)),
                SwitchStructureJoint(JointNumber(11), Point(28.234, 1.229)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(10)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(28.254, -0.666), radius = 600.0)
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(11)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(28.234, 1.229), radius = 281.0)
                        ),
                ),
            ),
    )

fun UKV60_600_281_1_9_V() = UKV60_600_281_1_9_O().flipAlongYAxis().copy(type = SwitchType("UKV60-600/281-1:9-V"))
