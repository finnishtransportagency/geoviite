package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchType

// NOTE:
// This switch type may contain inaccurate point locations and wrong elements
// as this model is constructed with insufficient information from Ratko DB and
// with limited understanding of the domain.

fun SKV60_800_423_1_15_5_O() =
    SwitchStructureData(
        type = SwitchType("SKV60-800/423-1:15,5-V"),
        presentationJointNumber = JointNumber(1),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(16), Point(59.003, 2.105)),
                SwitchStructureJoint(JointNumber(17), Point(58.807, 3.967)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(16)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(59.003, 2.105), radius = 800.0)
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(17)),
                    elements =
                        listOf(
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(58.807, 3.967), radius = 423.0)
                        ),
                ),
            ),
    )

fun SKV60_800_423_1_15_5_V() =
    SKV60_800_423_1_15_5_O().flipAlongYAxis().copy(type = SwitchType("SKV60-800/423-1:15,5-O"))
