package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV30_270_1_7_V() =
    SwitchStructureData(
        type = SwitchType("YV30-270-1:7-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(12.685, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(25.928, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(25.795, 1.872)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(12.685, 0.0)),
                            SwitchStructureLine(start = Point(12.685, 0.0), end = Point(25.928, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(25.701, 1.859), radius = 185.0),
                            SwitchStructureLine(start = Point(25.701, 1.859), end = Point(25.795, 1.872)),
                        ),
                ),
            ),
    )

fun YV30_270_1_7_O() = YV30_270_1_7_V().flipAlongYAxis().copy(type = SwitchType("YV30-270-1:7-O"))
