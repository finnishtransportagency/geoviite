package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.dataImport.switchStructures
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchElement
import fi.fta.geoviite.infra.switchLibrary.SwitchElementCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchElementLine
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.switchLibrary.data.RR54_2x1_9
import fi.fta.geoviite.infra.switchLibrary.data.YV60_300A_1_9_O
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class SwitchStructureIT
@Autowired
constructor(val switchStructureDao: SwitchStructureDao, val switchLibraryService: SwitchLibraryService) : DBTestBase() {

    @Test
    fun shouldGetSwitchStructures() {
        switchStructureDao.fetchSwitchStructures()
    }

    @Test
    fun insertAndFetchSwitchStructureShouldWork() {
        val seq = System.currentTimeMillis()
        val uniqueSwitchType = SwitchType("YV60-$seq-1:10")
        val switchStructure =
            SwitchStructure(
                id = IntId(0), // can be any ID
                type = uniqueSwitchType,
                presentationJointNumber = JointNumber(5),
                joints =
                    listOf(
                        SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                        SwitchJoint(JointNumber(5), Point(10.0, 0.0)),
                        SwitchJoint(JointNumber(2), Point(20.0, 0.0)),
                        SwitchJoint(JointNumber(3), Point(20.0, 2.0)),
                    ),
                alignments =
                    listOf(
                        SwitchAlignment(
                            jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                            elements =
                                listOf(
                                    SwitchElementLine(
                                        id = IndexedId(0, 0), // can be any ID
                                        start = Point(0.0, 0.0),
                                        end = Point(10.0, 0.0),
                                    ),
                                    SwitchElementLine(
                                        id = IndexedId(0, 0), // can be any ID
                                        start = Point(10.0, 0.0),
                                        end = Point(20.0, 0.0),
                                    ),
                                ),
                        ),
                        SwitchAlignment(
                            jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                            elements =
                                listOf(
                                    SwitchElementCurve(
                                        id = IndexedId(0, 0), // can be any ID
                                        start = Point(0.0, 0.0),
                                        end = Point(20.0, 2.0),
                                        radius = 200.0,
                                    )
                                ),
                        ),
                    ),
            )

        val version = switchStructureDao.insertSwitchStructure(switchStructure)
        val loadedSwitchStructure = switchStructureDao.fetchSwitchStructure(version)

        switchStructuresEqual(switchStructure, loadedSwitchStructure)
    }

    @Test
    fun `Update switch structure should work as described`() {
        val seq = System.currentTimeMillis()
        val originalSwitchType = SwitchType("YV60-$seq-1:9")
        val originalSwitchStructure = YV60_300A_1_9_O().copy(type = originalSwitchType)
        val originalVersionId = switchStructureDao.insertSwitchStructure(originalSwitchStructure)
        val originalLoadedSwitchStructure = switchStructureDao.fetchSwitchStructure(originalVersionId)

        val updatedSwitchType = SwitchType("RR54-$seq-1:9")
        val updatedSwitchStructure = RR54_2x1_9().copy(type = updatedSwitchType, id = originalLoadedSwitchStructure.id)
        val updatedVersionId = switchStructureDao.updateSwitchStructure(updatedSwitchStructure)
        val updatedLoadedSwitchStructure = switchStructureDao.fetchSwitchStructure(updatedVersionId)

        assertEquals(originalLoadedSwitchStructure.id, updatedLoadedSwitchStructure.id)
        switchStructuresEqual(updatedSwitchStructure, updatedLoadedSwitchStructure)
    }

    @Test
    fun `Upsert should update modified switch structure`() {
        val seq = System.currentTimeMillis()
        val switchType = SwitchType("YV60-$seq-1:9")
        val switchStructure = YV60_300A_1_9_O().copy(type = switchType)
        val versionId = switchStructureDao.insertSwitchStructure(switchStructure)

        val modifiedSwitchStructure = modifyStructure(YV60_300A_1_9_O(), switchType)

        val versionBeforeUpsert = switchStructureDao.fetchSwitchStructureVersion(versionId.id)
        val existingSwitchStructure = switchStructureDao.fetchSwitchStructure(versionBeforeUpsert)
        switchLibraryService.upsertSwitchStructure(modifiedSwitchStructure, existingSwitchStructure)
        val versionAfterUpsert = switchStructureDao.fetchSwitchStructureVersion(versionId.id)
        assertNotEquals(versionBeforeUpsert.version, versionAfterUpsert.version)
    }

    @Test
    fun `Upsert should not update unmodified switch structure`() {
        val seq = System.currentTimeMillis()
        val switchType = SwitchType("YV60-$seq-1:9")
        val switchStructure = YV60_300A_1_9_O().copy(type = switchType)
        val versionId = switchStructureDao.insertSwitchStructure(switchStructure)

        val similarSwitchStructure = YV60_300A_1_9_O().copy(type = switchType)

        val versionBeforeUpsert = switchStructureDao.fetchSwitchStructureVersion(versionId.id)
        val existingSwitchStructure = switchStructureDao.fetchSwitchStructure(versionBeforeUpsert)
        switchLibraryService.upsertSwitchStructure(similarSwitchStructure, existingSwitchStructure)
        val versionAfterUpsert = switchStructureDao.fetchSwitchStructureVersion(versionId.id)
        assertEquals(versionBeforeUpsert.version, versionAfterUpsert.version)
    }

    @Test
    fun `Replacing switch structures should add new ones`() {
        val existingSwitchStructuresBeforeUpdate = switchStructureDao.fetchSwitchStructures()

        val seq = System.currentTimeMillis()
        val newSwitchType = SwitchType("YV60-$seq-1:9")
        val newSwitchStructure = YV60_300A_1_9_O().copy(type = newSwitchType)

        switchLibraryService.replaceExistingSwitchStructures(existingSwitchStructuresBeforeUpdate + newSwitchStructure)

        val existingSwitchStructuresAfterUpdate = switchStructureDao.fetchSwitchStructures()
        assert(existingSwitchStructuresBeforeUpdate.none { s -> s.type == newSwitchType })
        assert(existingSwitchStructuresAfterUpdate.any { s -> s.type == newSwitchType })
    }

    @Test
    fun `Replacing switch structures should delete not-defined structures`() {
        val existingSwitchStructuresBeforeUpdate = switchStructureDao.fetchSwitchStructures()

        val seq = System.currentTimeMillis()
        val newSwitchType = SwitchType("YV60-$seq-1:9")
        val newSwitchStructure = YV60_300A_1_9_O().copy(type = newSwitchType)

        switchLibraryService.replaceExistingSwitchStructures(existingSwitchStructuresBeforeUpdate + newSwitchStructure)
        val existingSwitchStructuresAfterFirstUpdate = switchStructureDao.fetchSwitchStructures()

        switchLibraryService.replaceExistingSwitchStructures(existingSwitchStructuresBeforeUpdate)
        val existingSwitchStructuresAfterSecondUpdate = switchStructureDao.fetchSwitchStructures()

        assert(existingSwitchStructuresBeforeUpdate.none { s -> s.type == newSwitchType })
        assert(existingSwitchStructuresAfterFirstUpdate.any { s -> s.type == newSwitchType })
        assert(existingSwitchStructuresAfterSecondUpdate.none { s -> s.type == newSwitchType })
    }

    fun switchStructuresEqual(s1: SwitchStructure, s2: SwitchStructure) {
        assertEquals(s1.type, s2.type)
        assertEquals(s1.presentationJointNumber, s2.presentationJointNumber)
        assertEquals(s1.joints.sortedBy { it.number }, s2.joints.sortedBy { it.number })
        s1.alignments.forEachIndexed { index, alignment -> switchAlignmentsEqual(alignment, s2.alignments[index]) }
        s2.alignments.forEachIndexed { index, alignment -> switchAlignmentsEqual(alignment, s1.alignments[index]) }
    }

    fun switchAlignmentsEqual(a1: SwitchAlignment, a2: SwitchAlignment) {
        assertEquals(a1.jointNumbers, a2.jointNumbers)
        a1.elements.forEachIndexed { index, element -> switchElementsEqual(element, a2.elements[index]) }
        a2.elements.forEachIndexed { index, element -> switchElementsEqual(element, a1.elements[index]) }
    }

    fun switchElementsEqual(e1: SwitchElement, e2: SwitchElement) {
        assertEquals(e1.type, e2.type)
        assertEquals(e1.start, e2.start)
        assertEquals(e1.end, e1.end)
        if (e1 is SwitchElementCurve && e2 is SwitchElementCurve) {
            assertEquals(e1.radius, e2.radius)
        }
    }

    @Test
    fun `Should produce different hashcode when switch structures are modified`() {
        val firstSet = switchStructures
        val modifiedSet =
            firstSet.mapIndexed { index, struct -> struct.takeIf { index > 0 } ?: modifyStructure(struct) }

        assertNotEquals(
            firstSet.map { s -> s.stripUniqueIdentifiers() }.hashCode(),
            modifiedSet.map { s -> s.stripUniqueIdentifiers() }.hashCode(),
        )
    }

    @Test
    fun `Should produce same hashcode when switch structures are not modified`() {
        val firstSet = switchStructures
        val similarSet = firstSet.map { struct -> struct.copy() }

        assertEquals(
            firstSet.map { s -> s.stripUniqueIdentifiers() }.hashCode(),
            similarSet.map { s -> s.stripUniqueIdentifiers() }.hashCode(),
        )
    }

    private fun modifyStructure(struct: SwitchStructure, switchType: SwitchType = struct.type) =
        struct.copy(
            type = switchType,
            alignments =
                struct.alignments.mapIndexed { alignmentIndex, alignment ->
                    alignment.takeIf { alignmentIndex > 0 }
                        ?: alignment.copy(
                            elements =
                                alignment.elements.mapIndexed { elementIndex, element ->
                                    element.takeIf { elementIndex > 0 }
                                        ?: SwitchElementLine(
                                            id = element.id,
                                            start = element.start + 10.0,
                                            end = element.end,
                                        )
                                }
                        )
                },
        )
}
