package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun UKV60_1000_244_1_9_O() =
    SwitchStructureData(
        type = SwitchType("UKV60-1000/244-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(7), Point(10.074, 0.0)),
                SwitchStructureJoint(JointNumber(8), Point(14.141, 0.0)),
                SwitchStructureJoint(JointNumber(10), Point(28.290, -0.400)),
                SwitchStructureJoint(JointNumber(11), Point(28.217, 1.498)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(10)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(28.290, -0.400), radius = 1000.0)
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(11)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(28.217, 1.498), radius = 244.45)
                        ),
                ),
            ),
    )

fun UKV60_1000_244_1_9_V() = UKV60_1000_244_1_9_O().flipAlongYAxis().copy(type = SwitchType("UKV60-1000/244-1:9-V"))
