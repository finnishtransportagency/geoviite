package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV60_300_1_9_O() =
    SwitchStructureData(
        type = SwitchType.of("YV60-300-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(16.615, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(34.430, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(34.321, -1.967)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(16.615, 0.0)),
                            SwitchStructureLine(start = Point(16.615, 0.0), end = Point(34.430, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(33.128, -1.835), radius = 300.0),
                            SwitchStructureLine(start = Point(33.128, -1.835), end = Point(34.321, -1.967)),
                        ),
                ),
            ),
    )

fun YV60_300_1_9_V() = YV60_300_1_9_O().flipAlongYAxis().copy(type = SwitchType.of("YV60-300-1:9-V"))

fun YV60_300A_1_9_O() = YV60_300_1_9_O().copy(type = SwitchType.of("YV60-300A-1:9-O"))

fun YV60_300A_1_9_V() = YV60_300_1_9_V().copy(type = SwitchType.of("YV60-300A-1:9-V"))

fun YV60_300E_1_9_O() = YV60_300_1_9_O().copy(type = SwitchType.of("YV60-300E-1:9-O"))

fun YV60_300E_1_9_V() = YV60_300_1_9_V().copy(type = SwitchType.of("YV60-300E-1:9-V"))
