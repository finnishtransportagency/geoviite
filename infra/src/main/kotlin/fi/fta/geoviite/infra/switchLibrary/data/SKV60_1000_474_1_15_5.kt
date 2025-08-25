package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun SKV60_1000_474_1_15_5_O() =
    SwitchStructureData(
        type = SwitchType.of("SKV60-1000/474-1:15,5-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(13), Point(29.555, 0.0)),
                SwitchStructureJoint(JointNumber(16), Point(59.058, -1.745)),
                SwitchStructureJoint(JointNumber(17), Point(58.896, -3.553)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(16)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(59.058, -1.745), radius = 1000.0)
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(17)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(58.896, -3.553), radius = 474.0)
                        ),
                ),
            ),
    )

fun SKV60_1000_474_1_15_5_V() =
    SKV60_1000_474_1_15_5_O().flipAlongYAxis().copy(type = SwitchType.of("SKV60-1000/474-1:15,5-V"))
