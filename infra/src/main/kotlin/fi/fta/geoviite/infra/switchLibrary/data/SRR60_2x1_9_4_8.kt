package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

// NOTE: Joint point locations are inferred from geometry elements with just a little domain
// knowledge and therefore this switch model may be somewhat wrong.

fun SRR60_2x1_9_4_8() =
    SwitchStructureData(
        type = SwitchType("SRR60-2x1:9-4,8"),
        presentationJointNumber = JointNumber(5),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(-5.087, -5.087 / 9.0)),
                SwitchStructureJoint(JointNumber(5), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(5.087, 5.087 / 9.0)),
                SwitchStructureJoint(JointNumber(3), Point(5.087, -5.087 / 9.0)),
                SwitchStructureJoint(JointNumber(4), Point(-5.087, 5.087 / 9.0)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(-5.087, -5.087 / 9.0), end = Point(0.0, 0.0)),
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(5.087, 5.087 / 9.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(-5.087, 5.087 / 9.0), end = Point(0.0, 0.0)),
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(5.087, -5.087 / 9.0)),
                        ),
                ),
            ),
    )
