package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun RR43_1_9_514() =
    SwitchStructureData(
        type = SwitchType("RR43-1:9,514"),
        presentationJointNumber = JointNumber(5),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(5), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(1), Point(-17.44, 1.84)),
                SwitchStructureJoint(JointNumber(2), Point(17.44, -1.84)),
                SwitchStructureJoint(JointNumber(4), Point(-17.44, -1.84)),
                SwitchStructureJoint(JointNumber(3), Point(17.44, 1.84)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(-17.44, 1.84), end = Point(0.0, 0.0)),
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(17.44, -1.84)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(-17.44, -1.84), end = Point(0.0, 0.0)),
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(17.44, 1.84)),
                        ),
                ),
            ),
    )
