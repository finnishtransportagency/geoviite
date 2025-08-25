package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV60_500_1_11_1_O() =
    SwitchStructureData(
        type = SwitchType.of("YV60-500-1:11,1-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(22.471, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(44.943, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(44.852, -2.016)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(22.471, 0.0)),
                            SwitchStructureLine(start = Point(22.471, 0.0), end = Point(44.943, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(44.852, -2.016), radius = 500.0)
                        ),
                ),
            ),
    )

fun YV60_500_1_11_1_V() = YV60_500_1_11_1_O().flipAlongYAxis().copy(type = SwitchType.of("YV60-500-1:11,1-V"))

fun YV60_500A_1_11_1_O() = YV60_500_1_11_1_O().copy(type = SwitchType.of("YV60-500A-1:11,1-O"))

fun YV60_500A_1_11_1_V() = YV60_500_1_11_1_V().copy(type = SwitchType.of("YV60-500A-1:11,1-V"))
