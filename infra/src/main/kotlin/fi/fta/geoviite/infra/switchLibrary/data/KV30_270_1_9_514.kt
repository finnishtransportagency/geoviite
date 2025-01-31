package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun KV30_270_1_9_514_O() =
    SwitchStructureData(
        type = SwitchType("KV30-270-1:9,514-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(10.42, 0.0)),
                SwitchStructureJoint(JointNumber(6), Point(20.42, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(37.96, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(27.864, -1.833)),
                SwitchStructureJoint(JointNumber(4), Point(37.96, 1.833)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(6), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(10.42, 0.0)),
                            SwitchStructureLine(start = Point(10.42, 0.0), end = Point(20.42, 0.0)),
                            SwitchStructureLine(start = Point(20.42, 0.0), end = Point(37.96, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(
                                start = Point(0.0, 0.0),
                                end = Point(20.783, -1.089),
                                radius = 198.825,
                            ),
                            SwitchStructureLine(start = Point(20.783, -1.089), end = Point(27.864, -1.833)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(4)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(10.0, 0.0)),
                            SwitchStructureCurve(
                                start = Point(10.0, 0.0),
                                end = Point(30.783, 1.089),
                                radius = 198.825,
                            ),
                            SwitchStructureLine(start = Point(30.783, 1.089), end = Point(37.864, 1.833)),
                        ),
                ),
            ),
    )

fun KV30_270_1_9_514_V() = KV30_270_1_9_514_O().flipAlongYAxis().copy(type = SwitchType("KV30-270-1:9,514-V"))
