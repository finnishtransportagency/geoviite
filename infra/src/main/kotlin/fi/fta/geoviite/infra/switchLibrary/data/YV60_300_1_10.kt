package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV60_300_1_10_V() =
    SwitchStructureData(
        type = SwitchType.of("YV60-300-1:10-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(12.97, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(32.008, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(31.914, 1.894)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(12.97, 0.0)),
                            SwitchStructureLine(start = Point(12.97, 0.0), end = Point(32.008, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(25.876, 1.291), radius = 260.047),
                            SwitchStructureLine(start = Point(25.876, 1.291), end = Point(31.914, 1.894)),
                        ),
                ),
            ),
    )

fun YV60_300_1_10_O() = YV60_300_1_10_V().flipAlongYAxis().copy(type = SwitchType.of("YV60-300-1:10-O"))
