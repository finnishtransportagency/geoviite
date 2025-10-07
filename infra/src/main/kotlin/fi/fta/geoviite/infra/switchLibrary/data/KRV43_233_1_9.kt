package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun KRV43_233_1_9() =
    SwitchStructureData(
        type = SwitchType.of("KRV43-233-1:9"),
        presentationJointNumber = JointNumber(5),
        joints =
            setOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(16.846, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(33.692, 0.0)),
                SwitchStructureJoint(JointNumber(4), Point(0.103, 1.86)),
                SwitchStructureJoint(JointNumber(3), Point(33.589, -1.86)),
            ),
        alignments =
            listOf(
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.0, 0.0), end = Point(16.846, 0.0)),
                            SwitchStructureLine(start = Point(16.846, 0.0), end = Point(33.692, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchStructureLine(start = Point(0.103, 1.86), end = Point(16.846, 0.0)),
                            SwitchStructureLine(start = Point(16.846, 0.0), end = Point(33.692, 0.0)),
                        ),
                ),
                SwitchStructureAlignment(
                    // The center line of this alignment does not actually go through the math point
                    // (number 5), but alignment is modelled this way to make handling simpler.
                    // Usually this is modelled the same way in infra model files.
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            // NOTE:
                            // Most probably there should be line elements before and after the
                            // curve,
                            // but did not have information about those elements and therefore this
                            // is simplified. This does not affect current functionality, joint
                            // point
                            // locations are the most important information.
                            SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(33.589, -1.86), radius = 233.0)
                        ),
                ),
                SwitchStructureAlignment(
                    // The center line of this alignment does not actually go through the math point
                    // (number 5), but alignment is modelled this way to make handling simpler.
                    // Usually this is modelled the same way in infra model files.
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            // NOTE:
                            // Most probably there should be line elements before and after the
                            // curve,
                            // but did not have information about those elements and therefore this
                            // is simplified. This does not affect current functionality, joint
                            // point
                            // locations are the most important information.
                            SwitchStructureCurve(start = Point(0.103, 1.86), end = Point(33.692, 0.0), radius = 233.0)
                        ),
                ),
            ),
    )
