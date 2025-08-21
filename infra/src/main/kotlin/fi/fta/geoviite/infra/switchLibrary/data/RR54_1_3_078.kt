package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun RR54_1_3_078() =
    SwitchStructureData(
        type = SwitchType.of("RR54-1:3,078"),
        presentationJointNumber = JointNumber(5),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(-6.910, 1.008)),
                SwitchStructureJoint(JointNumber(5), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(6.910, -1.008)),
                SwitchStructureJoint(JointNumber(4), Point(-6.870, -1.250)),
                SwitchStructureJoint(JointNumber(3), Point(6.870, 1.250)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(-6.910, 1.008), end = Point(0.0, 0.0)),
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(6.910, -1.008)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(-6.870, -1.250), end = Point(0.0, 0.0)),
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(6.870, 1.250)),
                        ),
                ),
            ),
    )
