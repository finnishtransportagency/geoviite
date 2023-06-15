package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class SwitchStructureIT @Autowired constructor(
    val switchStructureDao: SwitchStructureDao
): DBTestBase() {

    @Test
    fun shouldGetSwitchStructures() {
        switchStructureDao.fetchSwitchStructures()
    }

    @Test
    fun insertAndFetchSwitchStructureShouldWork() {
        val seq = System.currentTimeMillis()
        val uniqueSwitchType = SwitchType("YV60-$seq-1:10")
        val switchStructure = SwitchStructure(
            id = IntId(0), // can be any ID
            type = uniqueSwitchType,
            presentationJointNumber = JointNumber(5),
            joints = listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(10.0, 0.0)),
                SwitchJoint(JointNumber(2), Point(20.0, 0.0)),
                SwitchJoint(JointNumber(3), Point(20.0, 2.0)),
            ),
            alignments = listOf(
                SwitchAlignment(
                    jointNumbers = listOf(
                        JointNumber(1),
                        JointNumber(5),
                        JointNumber(2)
                    ),
                    elements = listOf(
                        SwitchElementLine(
                            id = IndexedId(0, 0), // can be any ID
                            start = Point(0.0, 0.0),
                            end = Point(10.0, 0.0)
                        ),
                        SwitchElementLine(
                            id = IndexedId(0, 0), // can be any ID
                            start = Point(10.0, 0.0),
                            end = Point(20.0, 0.0)
                        )
                    )
                ),
                SwitchAlignment(
                    jointNumbers = listOf(
                        JointNumber(1),
                        JointNumber(3)
                    ),
                    elements = listOf(
                        SwitchElementCurve(
                            id = IndexedId(0, 0), // can be any ID
                            start = Point(0.0, 0.0),
                            end = Point(20.0, 2.0),
                            radius = 200.0
                        )
                    )
                )
            )
        )

        val version = switchStructureDao.insertSwitchStructure(
            switchStructure
        )
        val loadedSwitchStructure = switchStructureDao.fetchSwitchStructure(version)

        switchStructuresEqual(
            switchStructure,
            loadedSwitchStructure
        )
    }

    fun switchStructuresEqual(s1: SwitchStructure, s2: SwitchStructure) {
        assertEquals(s1.type, s2.type)
        assertEquals(s1.presentationJointNumber, s2.presentationJointNumber)
        assertEquals(s1.joints.sortedBy { it.number }, s2.joints.sortedBy { it.number })
        s1.alignments.forEachIndexed { index, alignment ->
            switchAlignmentsEqual(alignment, s2.alignments[index])
        }
        s2.alignments.forEachIndexed { index, alignment ->
            switchAlignmentsEqual(alignment, s1.alignments[index])
        }
    }

    fun switchAlignmentsEqual(a1: SwitchAlignment, a2: SwitchAlignment) {
        assertEquals(a1.jointNumbers, a2.jointNumbers)
        a1.elements.forEachIndexed { index, element ->
            switchElementsEqual(element, a2.elements[index])
        }
        a2.elements.forEachIndexed { index, element ->
            switchElementsEqual(element, a1.elements[index])
        }
    }

    fun switchElementsEqual(e1: SwitchElement, e2: SwitchElement) {
        assertEquals(e1.type, e2.type)
        assertEquals(e1.start, e2.start)
        assertEquals(e1.end, e1.end)
        if (e1 is SwitchElementCurve && e2 is SwitchElementCurve) {
            assertEquals(e1.radius, e2.radius)
        }
    }
}
