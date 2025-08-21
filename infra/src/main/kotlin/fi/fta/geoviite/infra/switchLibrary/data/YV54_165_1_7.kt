package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun YV54_165_1_7_V() =
    SwitchStructureData(
        type = SwitchType.of("YV54-165-1:7-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(13.482, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(26.506, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(26.376, 1.842)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(13.482, 0.0)),
                            SwitchStructureLine(start = Point(13.482, 0.0), end = Point(26.506, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(25.09, 1.658), radius = 165.0),
                            SwitchStructureLine(start = Point(25.09, 1.658), end = Point(26.376, 1.842)),
                        ),
                ),
            ),
    )

fun YV54_165_1_7_O() = YV54_165_1_7_V().flipAlongYAxis().copy(type = SwitchType.of("YV54-165-1:7-O"))
