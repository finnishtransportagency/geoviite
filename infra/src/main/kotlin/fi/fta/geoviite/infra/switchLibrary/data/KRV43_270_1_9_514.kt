package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun KRV43_270_1_9_514() =
    SwitchStructureData(
        type = SwitchType.of("KRV43-270-1:9,514"),
        presentationJointNumber = JointNumber(5),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(17.540, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(35.080, 0.0)),
                SwitchStructureJoint(JointNumber(4), Point(0.096, 1.833)),
                SwitchStructureJoint(JointNumber(3), Point(34.984, -1.833)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(17.540, 0.0)),
                            SwitchStructureLine(start = Point(17.540, 0.0), end = Point(35.080, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.096, 1.833), end = Point(17.540, 0.0)),
                            SwitchStructureLine(start = Point(17.540, 0.0), end = Point(35.080, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(6.010, 0.0)),
                            SwitchStructureCurve(
                                start = Point(6.010, 0.0),
                                end = Point(29.006, -1.206),
                                radius = 220.0,
                            ),
                            SwitchStructureLine(start = Point(29.006, -1.206), end = Point(34.984, -1.833)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.096, 1.833), end = Point(6.0734, 1.206)),
                            SwitchStructureCurve(start = Point(6.0734, 1.206), end = Point(29.07, 0.0), radius = 220.0),
                            SwitchStructureLine(start = Point(29.07, 0.0), end = Point(35.080, 0.0)),
                        ),
                ),
            ),
    )
