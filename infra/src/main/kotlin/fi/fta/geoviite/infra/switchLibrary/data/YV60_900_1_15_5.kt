package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*

fun YV60_900_1_15_5_O() =
    SwitchStructureData(
        type = SwitchType("YV60-900-1:15,5-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(30.060, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(59.100, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(59.040, -1.870)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(30.060, 0.0)),
                            SwitchStructureLine(start = Point(30.060, 0.0), end = Point(59.100, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(1.058, 0.0)),
                            SwitchStructureCurve(
                                start = Point(1.058, 0.0),
                                end = Point(59.002, -1.867),
                                radius = 900.0,
                            ),
                            SwitchStructureLine(start = Point(59.002, -1.867), end = Point(59.040, -1.870)),
                        ),
                ),
            ),
    )

fun YV60_900_1_15_5_V() = YV60_900_1_15_5_O().flipAlongYAxis().copy(type = SwitchType("YV60-900-1:15,5-V"))

fun YV60_900A_1_15_5_O() = YV60_900_1_15_5_O().copy(type = SwitchType("YV60-900A-1:15,5-O"))

fun YV60_900A_1_15_5_V() = YV60_900_1_15_5_V().copy(type = SwitchType("YV60-900A-1:15,5-V"))

fun YV60_900E_1_15_5_O() = YV60_900_1_15_5_O().copy(type = SwitchType("YV60-900E-1:15,5-O"))

fun YV60_900E_1_15_5_V() = YV60_900_1_15_5_V().copy(type = SwitchType("YV60-900E-1:15,5-V"))
