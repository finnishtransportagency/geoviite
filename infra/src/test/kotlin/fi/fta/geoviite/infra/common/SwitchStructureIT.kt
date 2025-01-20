package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.dataImport.switchStructures
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
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
            SwitchStructureData(
                type = uniqueSwitchType,
                presentationJointNumber = JointNumber(5),
                joints =
                    setOf(
                        SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                        SwitchStructureJoint(JointNumber(5), Point(10.0, 0.0)),
                        SwitchStructureJoint(JointNumber(2), Point(20.0, 0.0)),
                        SwitchStructureJoint(JointNumber(3), Point(20.0, 2.0)),
                    ),
                alignments =
                    listOf(
                        SwitchStructureAlignment(
                            jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                            elements =
                                listOf(
                                    SwitchStructureLine(start = Point(0.0, 0.0), end = Point(10.0, 0.0)),
                                    SwitchStructureLine(start = Point(10.0, 0.0), end = Point(20.0, 0.0)),
                                ),
                        ),
                        SwitchStructureAlignment(
                            jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                            elements =
                                listOf(
                                    SwitchStructureCurve(
                                        start = Point(0.0, 0.0),
                                        end = Point(20.0, 2.0),
                                        radius = 200.0,
                                    )
                                ),
                        ),
                    ),
            )

        val version = switchStructureDao.upsertSwitchStructure(switchStructure)
        val loadedSwitchStructure = switchStructureDao.fetchSwitchStructure(version)

        assertEquals(switchStructure, loadedSwitchStructure.data)
    }

    @Test
    fun `Update switch structure should work as described`() {
        val seq = System.currentTimeMillis()
        val originalSwitchType = SwitchType("YV60-$seq-1:9")
        val originalSwitchStructure = YV60_300A_1_9_O().copy(type = originalSwitchType)
        val originalVersion = switchStructureDao.upsertSwitchStructure(originalSwitchStructure)
        val originalLoadedSwitchStructure = switchStructureDao.fetchSwitchStructure(originalVersion)

        val updatedSwitchStructure = RR54_2x1_9().copy(type = originalSwitchType)
        val updatedVersion = switchStructureDao.upsertSwitchStructure(updatedSwitchStructure)
        val updatedLoadedSwitchStructure = switchStructureDao.fetchSwitchStructure(updatedVersion)

        assertEquals(originalLoadedSwitchStructure.id, updatedLoadedSwitchStructure.id)
        assertEquals(updatedSwitchStructure, updatedLoadedSwitchStructure.data)
    }

    @Test
    fun `Upsert should update modified switch structure`() {
        val seq = System.currentTimeMillis()
        val switchType = SwitchType("YV60-$seq-1:9")
        val switchStructure = YV60_300A_1_9_O().copy(type = switchType)
        val version = switchStructureDao.upsertSwitchStructure(switchStructure)

        val modifiedSwitchStructure = modifyStructure(YV60_300A_1_9_O(), switchType)
        assertNotEquals(switchStructure, modifiedSwitchStructure)

        val versionBeforeUpsert = switchStructureDao.fetchSwitchStructureVersion(version.id)
        val existingSwitchStructure = switchStructureDao.fetchSwitchStructure(versionBeforeUpsert)
        switchLibraryService.upsertSwitchStructure(modifiedSwitchStructure, existingSwitchStructure)
        val versionAfterUpsert = switchStructureDao.fetchSwitchStructureVersion(version.id)
        assertNotEquals(versionBeforeUpsert.version, versionAfterUpsert.version)
        assertEquals(switchStructure, switchStructureDao.fetchSwitchStructure(versionBeforeUpsert).data)
        assertEquals(modifiedSwitchStructure, switchStructureDao.fetchSwitchStructure(versionAfterUpsert).data)
    }

    @Test
    fun `Upsert should not update unmodified switch structure`() {
        val seq = System.currentTimeMillis()
        val switchType = SwitchType("YV60-$seq-1:9")
        val switchStructure = YV60_300A_1_9_O().copy(type = switchType)
        val versionId = switchStructureDao.upsertSwitchStructure(switchStructure)

        val similarSwitchStructure = YV60_300A_1_9_O().copy(type = switchType)

        val versionBeforeUpsert = switchStructureDao.fetchSwitchStructureVersion(versionId.id)
        val existingSwitchStructure = switchStructureDao.fetchSwitchStructure(versionBeforeUpsert)
        switchLibraryService.upsertSwitchStructure(similarSwitchStructure, existingSwitchStructure)
        val versionAfterUpsert = switchStructureDao.fetchSwitchStructureVersion(versionId.id)
        assertEquals(versionBeforeUpsert.version, versionAfterUpsert.version)
    }

    @Test
    fun `Replacing switch structures should add new ones`() {
        val existingSwitchStructuresBeforeUpdate = switchStructureDao.fetchSwitchStructures().map { s -> s.data }

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
        val existingSwitchStructuresBeforeUpdate = switchStructureDao.fetchSwitchStructures().map { s -> s.data }

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

    @Test
    fun `Should produce different hashcode when switch structures are modified`() {
        val firstSet = switchStructures
        val modifiedSet =
            firstSet.mapIndexed { index, struct -> struct.takeIf { index > 0 } ?: modifyStructure(struct) }
        assertNotEquals(firstSet, modifiedSet)
    }

    @Test
    fun `Should produce same hashcode when switch structures are not modified`() {
        val firstSet = switchStructures
        val similarSet = firstSet.map { struct -> struct.copy() }
        assertEquals(firstSet, similarSet)
    }

    private fun modifyStructure(struct: SwitchStructureData, switchType: SwitchType = struct.type) =
        struct.copy(
            type = switchType,
            alignments =
                struct.alignments.mapIndexed { alignmentIndex, alignment ->
                    alignment.takeIf { alignmentIndex > 0 }
                        ?: alignment.copy(
                            elements =
                                alignment.elements.mapIndexed { elementIndex, element ->
                                    element.takeIf { elementIndex > 0 }
                                        ?: SwitchStructureLine(start = element.start + 10.0, end = element.end)
                                }
                        )
                },
        )
}
