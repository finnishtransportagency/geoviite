package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV43_300_1_7_O() =
    SwitchStructureData(
        type = SwitchType("YV43-300-1:7-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(13.481, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(26.506, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(26.375, -1.842)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(13.481, 0.0)),
                            SwitchStructureLine(start = Point(13.481, 0.0), end = Point(26.506, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(26.145, -1.809), radius = 180.0),
                            SwitchStructureLine(start = Point(26.145, -1.809), end = Point(26.375, -1.842)),
                        ),
                ),
            ),
    )

fun YV43_300_1_7_V() = YV43_300_1_7_O().flipAlongYAxis().copy(type = SwitchType("YV43-300-1:7-V"))
