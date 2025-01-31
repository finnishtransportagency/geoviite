package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun EV_SJ43_5_9_1_9_H() =
    SwitchStructureData(
        type = SwitchType("EV-SJ43-5,9-1:9-H"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(11.54, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(29.02, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(28.91, -1.94)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(11.54, 0.0)),
                            SwitchStructureLine(start = Point(11.54, 0.0), end = Point(29.02, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(
                                start = Point(0.0, 0.0),
                                end = Point(28.91, -1.94),
                                radius = 216.2, // Calculated from point locations, is inaccurate/estimation
                            )
                        ),
                ),
            ),
    )

fun EV_SJ43_5_9_1_9_V() = EV_SJ43_5_9_1_9_H().flipAlongYAxis().copy(type = SwitchType("EV-SJ43-5,9-1:9-V"))
