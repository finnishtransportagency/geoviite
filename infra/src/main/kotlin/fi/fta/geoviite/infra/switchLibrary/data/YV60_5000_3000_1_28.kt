package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV60_5000_3000_1_28_O() =
    SwitchStructureData(
        type = SwitchType.of("YV60-5000/3000-1:28-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(58.220, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(112.039, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(112.005, -1.921)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(58.220, 0.0)),
                            SwitchStructureLine(start = Point(58.220, 0.0), end = Point(112.039, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(24.653, -0.074), radius = 5000.0),
                            SwitchStructureCurve(
                                start = Point(24.653, -0.074),
                                end = Point(112.005, -1.921),
                                radius = 3000.0,
                            ),
                        ),
                ),
            ),
    )

fun YV60_5000_3000_1_28_V() =
    YV60_5000_3000_1_28_O().flipAlongYAxis().copy(type = SwitchType.of("YV60-5000/3000-1:28-V"))
