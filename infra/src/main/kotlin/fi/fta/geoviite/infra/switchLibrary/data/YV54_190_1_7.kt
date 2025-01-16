package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

// NOTE:
// This switch is not in RATO4 but found from IM models

fun YV54_190_1_7_O() =
    SwitchStructureData(
        type = SwitchType("YV54-190-1:7-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(13.504, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(27.006, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(26.87, -1.91)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(13.504, 0.0)),
                            SwitchStructureLine(start = Point(13.504, 0.0), end = Point(27.006, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(26.87, -1.91), radius = 190.0)),
                ),
            ),
    )

fun YV54_190_1_7_V() = YV54_190_1_7_O().flipAlongYAxis().copy(type = SwitchType("YV54-190-1:7-V"))
