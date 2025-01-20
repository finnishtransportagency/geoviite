package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun RR54_1_9() =
    SwitchStructureData(
        type = SwitchType("RR54-1:9"),
        presentationJointNumber = JointNumber(5),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(17.223, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(34.446, 0.0)),
                SwitchStructureJoint(JointNumber(4), Point(0.105, 1.902)),
                SwitchStructureJoint(JointNumber(3), Point(34.341, -1.902)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(17.223, 0.0)),
                            SwitchStructureLine(start = Point(17.223, 0.0), end = Point(34.446, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.105, 1.902), end = Point(17.223, 0.0)),
                            SwitchStructureLine(start = Point(17.223, 0.0), end = Point(34.446, 0.0)),
                        ),
                ),
            ),
    )
