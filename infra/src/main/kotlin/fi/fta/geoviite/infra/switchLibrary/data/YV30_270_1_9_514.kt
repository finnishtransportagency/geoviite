package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV30_270_1_9_514_V() =
    SwitchStructureData(
        type = SwitchType("YV30-270-1:9,514-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(10.422, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(27.962, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(27.866, 1.833)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(10.422, 0.0)),
                            SwitchStructureLine(start = Point(10.422, 0.0), end = Point(27.962, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(22.722, 1.293), radius = 236.0),
                            SwitchStructureLine(start = Point(22.722, 1.293), end = Point(27.866, 1.833)),
                        ),
                ),
            ),
    )

fun YV30_270_1_9_514_O() = YV30_270_1_9_514_V().flipAlongYAxis().copy(type = SwitchType("YV30-270-1:9,514-O"))
