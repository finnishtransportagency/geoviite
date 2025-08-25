package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV60_5000_2500_1_26_O() =
    SwitchStructureData(
        type = SwitchType.of("YV60-5000/2500-1:26-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(56.563, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(107.180, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(107.143, -1.945)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(56.563, 0.0)),
                            SwitchStructureLine(start = Point(56.563, 0.0), end = Point(107.180, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(36.979, -0.182), radius = 5000.0),
                            SwitchStructureCurve(
                                start = Point(36.979, -0.182),
                                end = Point(105.327, -1.876),
                                radius = 2500.0,
                            ),
                            SwitchStructureLine(start = Point(105.327, -1.876), end = Point(107.143, -1.945)),
                        ),
                ),
            ),
    )

fun YV60_5000_2500_1_26_V() =
    YV60_5000_2500_1_26_O().flipAlongYAxis().copy(type = SwitchType.of("YV60-5000/2500-1:26-V"))
