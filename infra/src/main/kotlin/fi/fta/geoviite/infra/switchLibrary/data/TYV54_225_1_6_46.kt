package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun TYV54_225_1_6_46() =
    SwitchStructureData(
        type = SwitchType("TYV54-225-1:6,46"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(8.706, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(20.942, 0.941)),
                SwitchStructureJoint(JointNumber(3), Point(20.942, -0.941)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(17.322, 0.663), radius = 225.0),
                            SwitchStructureLine(start = Point(17.322, 0.663), end = Point(20.942, 0.941)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(17.322, -0.663), radius = 225.0),
                            SwitchStructureLine(start = Point(17.322, 0.663), end = Point(20.942, -0.941)),
                        ),
                ),
            ),
    )
