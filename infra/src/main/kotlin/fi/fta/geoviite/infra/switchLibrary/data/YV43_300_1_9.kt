package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV43_300_1_9_O() =
    SwitchStructureData(
        type = SwitchType.of("YV43-300-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(12.004, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(28.85, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(28.747, -1.86)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(12.004, 0.0)),
                            SwitchStructureLine(start = Point(12.004, 0.0), end = Point(28.85, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(
                                start = Point(0.0, 0.0),
                                end = Point(23.935, -1.326),
                                radius = 216.737,
                            ),
                            SwitchStructureLine(start = Point(23.935, -1.326), end = Point(28.747, -1.86)),
                        ),
                ),
            ),
    )

fun YV43_300_1_9_V() = YV43_300_1_9_O().flipAlongYAxis().copy(type = SwitchType.of("YV43-300-1:9-V"))
