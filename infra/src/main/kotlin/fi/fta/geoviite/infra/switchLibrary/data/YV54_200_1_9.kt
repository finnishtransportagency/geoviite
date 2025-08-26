package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV54_200_1_9_O() =
    SwitchStructureData(
        type = SwitchType.of("YV54-200-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(11.077, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(28.300, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(28.195, -1.902)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(11.077, 0.0)),
                            SwitchStructureLine(start = Point(11.077, 0.0), end = Point(28.300, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(22.086, -1.223), radius = 300.0),
                            SwitchStructureLine(start = Point(22.086, -1.223), end = Point(28.195, -1.902)),
                        ),
                ),
            ),
    )

fun YV54_200_1_9_V() = YV54_200_1_9_O().flipAlongYAxis().copy(type = SwitchType.of("YV54-200-1:9-V"))

fun YV54_200N_1_9_O() = YV54_200_1_9_O().copy(type = SwitchType.of("YV54-200N-1:9-O"))

fun YV54_200N_1_9_V() = YV54_200_1_9_V().copy(type = SwitchType.of("YV54-200N-1:9-V"))

fun YV54_200_1_9_1435_O() = YV54_200_1_9_O().copy(type = SwitchType.of("YV54-200-1:9-1435-O"))

fun YV54_200_1_9_1435_V() = YV54_200_1_9_V().copy(type = SwitchType.of("YV54-200-1:9-1435-V"))

fun YV54_200N_1_9_1435_O() = YV54_200_1_9_O().copy(type = SwitchType.of("YV54-200N-1:9-1435-O"))

fun YV54_200N_1_9_1435_V() = YV54_200_1_9_V().copy(type = SwitchType.of("YV54-200N-1:9-1435-V"))

fun YV54_200_1_9_1524_1435_O() = YV54_200_1_9_O().copy(type = SwitchType.of("YV54-200-1:9-1524/1435-O"))

fun YV54_200_1_9_1524_1435_V() = YV54_200_1_9_V().copy(type = SwitchType.of("YV54-200-1:9-1524/1435-V"))
