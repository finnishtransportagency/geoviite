package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV43_300_1_9_514_O() =
    SwitchStructureData(
        type = SwitchType.of("YV43-300-1:9,514-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(11.31, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(28.85, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(28.754, -1.833)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(11.31, 0.0)),
                            SwitchStructureLine(start = Point(11.31, 0.0), end = Point(28.85, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(23.35, -1.265), radius = 231.0),
                            SwitchStructureLine(start = Point(23.35, -1.265), end = Point(28.754, -1.833)),
                        ),
                ),
            ),
    )

fun YV43_300_1_9_514_V() = YV43_300_1_9_514_O().flipAlongYAxis().copy(type = SwitchType.of("YV43-300-1:9,514-V"))

fun YV43_300_1_9_514_1435_O() = YV43_300_1_9_514_O().copy(type = SwitchType.of("YV43-300-1:9,514-1435-O"))

fun YV43_300_1_9_514_1435_V() = YV43_300_1_9_514_V().copy(type = SwitchType.of("YV43-300-1:9,514-1435-V"))
