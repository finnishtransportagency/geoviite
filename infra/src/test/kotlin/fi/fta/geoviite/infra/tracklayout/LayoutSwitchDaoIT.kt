package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutSwitchDaoIT @Autowired constructor(
    private val switchDao: LayoutSwitchDao,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        deleteFromTables("layout", "switch_joint", "switch")
    }

    @Test
    fun switchExternalIdIsUnique() {
        val oid = Oid<TrackLayoutSwitch>("99.99.99.99.99.99")

        // If the OID is already in use, remove it
        transactional {
            val deleteSql = "delete from layout.switch where external_id = :external_id"
            jdbc.update(deleteSql, mapOf("external_id" to oid))
        }

        val switch1 = switch(5, externalId = oid.toString(), draft = false)
        val switch2 = switch(6, externalId = oid.toString(), draft = false)
        switchDao.insert(switch1)
        assertThrows<DuplicateKeyException> { switchDao.insert(switch2) }
    }

    @Test
    fun switchesAreStoredAndLoadedOk() {
        (1..10).map { seed -> switch(seed, draft = false) }.forEach { switch ->
            val rowVersion = switchDao.insert(switch).rowVersion
            assertMatches(switch, switchDao.fetch(rowVersion))
        }
    }

    @Test
    fun switchVersioningWorks() {
        val tempSwitch = switch(2, name = "TST001", joints = joints(3, 5), draft = false)
        val (insertId, insertVersion) = switchDao.insert(tempSwitch)
        val inserted = switchDao.fetch(insertVersion)
        assertMatches(tempSwitch, inserted)
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.official, insertVersion.id))
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.draft, insertVersion.id))

        val tempDraft1 = asMainDraft(inserted).copy(name = SwitchName("TST002"))
        val draftVersion1 = switchDao.insert(tempDraft1).rowVersion
        val draft1 = switchDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1)
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.official, insertVersion.id))
        assertEquals(draftVersion1, switchDao.fetchVersion(MainLayoutContext.draft, insertVersion.id))

        val tempDraft2 = draft1.copy(joints = joints(5, 4))
        val draftVersion2 = switchDao.update(tempDraft2).rowVersion
        val draft2 = switchDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2)
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.official, insertVersion.id))
        assertEquals(draftVersion2, switchDao.fetchVersion(MainLayoutContext.draft, insertVersion.id))

        switchDao.deleteDraft(LayoutBranch.main, insertId)
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.official, insertVersion.id))
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.draft, insertVersion.id))

        assertEquals(inserted, switchDao.fetch(insertVersion))
        assertEquals(draft1, switchDao.fetch(draftVersion1))
        assertEquals(draft2, switchDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { switchDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun shouldSuccessfullyDeleteDraftSwitches() {
        val draftSwitch = switch(0, draft = true)
        val (insertedId, insertedVersion) = switchDao.insert(draftSwitch)
        val insertedSwitch = switchDao.fetch(insertedVersion)

        val deletedId = switchDao.deleteDraft(LayoutBranch.main, insertedId)
        assertEquals(insertedId, deletedId.id)
        assertFalse(switchDao.fetchAllVersions().any { id -> id == insertedId })

        // Verify that we can still fetch the deleted row with version
        assertEquals(insertedSwitch, switchDao.fetch(insertedVersion))
    }

    @Test
    fun shouldThrowExceptionWhenDeletingNormalSwitch() {
        val switch = switch(1, draft = false)
        val insertedSwitch = switchDao.insert(switch)

        assertThrows<DeletingFailureException> {
            switchDao.deleteDraft(LayoutBranch.main, insertedSwitch.id)
        }
    }

    @Test
    fun listingSwitchVersionsWorks() {
        val officialVersion = insertOfficial().rowVersion
        val undeletedDraftVersion = insertDraft().rowVersion
        val deleteStateDraftVersion = insertDraft(LayoutStateCategory.NOT_EXISTING).rowVersion
        val deletedDraftVersion = insertDraft().rowVersion
        assertEquals(deletedDraftVersion, switchDao.deleteDraft(LayoutBranch.main, deletedDraftVersion.id).rowVersion)

        val official = switchDao.fetchVersions(MainLayoutContext.official, false)
        assertContains(official, officialVersion)
        assertFalse(official.contains(undeletedDraftVersion))
        assertFalse(official.contains(deleteStateDraftVersion))
        assertFalse(official.contains(deletedDraftVersion))

        val draftWithoutDeleted = switchDao.fetchVersions(MainLayoutContext.draft, false)
        assertContains(draftWithoutDeleted, undeletedDraftVersion)
        assertFalse(draftWithoutDeleted.contains(deleteStateDraftVersion))
        assertFalse(draftWithoutDeleted.contains(deletedDraftVersion))

        val draftWithDeleted = switchDao.fetchVersions(MainLayoutContext.draft, true)
        assertContains(draftWithDeleted, undeletedDraftVersion)
        assertContains(draftWithDeleted, deleteStateDraftVersion)
        assertFalse(draftWithDeleted.contains(deletedDraftVersion))
    }

    @Test
    fun fetchOfficialVersionByMomentWorks() {
        val beforeCreationTime = switchDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps
        val (firstId, firstVersion) = insertOfficial()
        val firstVersionTime = switchDao.fetchChangeTime()

        Thread.sleep(1) // Ensure that they get different timestamps
        val updatedVersion = updateOfficial(firstVersion).rowVersion
        val updatedVersionTime = switchDao.fetchChangeTime()

        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(firstId, beforeCreationTime))
        assertEquals(firstVersion, switchDao.fetchOfficialVersionAtMoment(firstId, firstVersionTime))
        assertEquals(updatedVersion, switchDao.fetchOfficialVersionAtMoment(firstId, updatedVersionTime))
    }

    private fun insertOfficial(): DaoResponse<TrackLayoutSwitch> {
        return switchDao.insert(switch(456, draft = false))
    }

    private fun insertDraft(state: LayoutStateCategory = LayoutStateCategory.EXISTING): DaoResponse<TrackLayoutSwitch> {
        return switchDao.insert(switch(654, stateCategory = state, draft = true))
    }

    private fun updateOfficial(originalVersion: RowVersion<TrackLayoutSwitch>): DaoResponse<TrackLayoutSwitch> {
        val original = switchDao.fetch(originalVersion)
        assertFalse(original.isDraft)
        return switchDao.update(original.copy(name = SwitchName("${original.name}U")))
    }
}
