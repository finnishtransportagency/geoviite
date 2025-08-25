package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

// NOTE:
// This switch type may contain inaccurate point locations and wrong elements
// as this model is constructed with insufficient information from IM models and
// with limited understanding of domain.

fun YV60_300P_1_9_O() =
    SwitchStructureData(
        type = SwitchType.of("YV60-300P-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(16.615, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(33.23, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(33.128, -1.835)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(16.615, 0.0)),
                            SwitchStructureLine(start = Point(16.615, 0.0), end = Point(33.23, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(33.128, -1.835), radius = 300.0)
                        ),
                ),
            ),
    )

fun YV60_300P_1_9_V() = YV60_300P_1_9_O().flipAlongYAxis().copy(type = SwitchType.of("YV60-300P-1:9-V"))
