package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun RR54_4x1_9() =
    SwitchStructureData(
        type = SwitchType.of("RR54-4x1:9"),
        presentationJointNumber = JointNumber(5),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(-5.075, -1.142)),
                SwitchStructureJoint(JointNumber(5), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(5.075, 1.142)),
                SwitchStructureJoint(JointNumber(4), Point(-5.075, 1.142)),
                SwitchStructureJoint(JointNumber(3), Point(5.075, -1.142)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(-5.075, -1.142), end = Point(0.0, 0.0)),
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(5.075, 1.142)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(-5.075, 1.142), end = Point(0.0, 0.0)),
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(5.075, -1.142)),
                        ),
                ),
            ),
    )
