package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.error.NoSuchEntityException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutSwitchDaoIT @Autowired constructor(
    private val switchDao: LayoutSwitchDao,
): ITTestBase() {

    @Test
    fun switchExternalIdIsUnique() {
        val oid = Oid<TrackLayoutSwitch>("99.99.99.99.99.99")

        // If the OID is already in use, remove it
        transactional {
            val deleteSql = "delete from layout.switch where external_id = :external_id"
            jdbc.update(deleteSql, mapOf("external_id" to oid))
        }

        val switch1 = switch(5).copy(externalId = oid)
        val switch2 = switch(6).copy(externalId = oid)
        switchDao.insert(switch1)
        assertThrows<DuplicateKeyException> { switchDao.insert(switch2) }
    }

    @Test
    fun switchesAreStoredAndLoadedOk() {
        (1..10).map { seed -> switch(seed) }.forEach { switch ->
            val id = switchDao.insert(switch)
            assertMatches(switch, switchDao.fetch(id))
        }
    }

    @Test
    fun switchVersioningWorks() {
        val tempSwitch = switch(2, name = "TST001", joints = joints(3, 5))
        val insertVersion = switchDao.insert(tempSwitch)
        val inserted = switchDao.fetch(insertVersion)
        assertMatches(tempSwitch, inserted)
        assertEquals(VersionPair(insertVersion, null), switchDao.fetchVersionPair(insertVersion.id))

        val tempDraft1 = draft(inserted).copy(name = SwitchName("TST002"))
        val draftVersion1 = switchDao.insert(tempDraft1)
        val draft1 = switchDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1)
        assertEquals(VersionPair(insertVersion, draftVersion1), switchDao.fetchVersionPair(insertVersion.id))

        val tempDraft2 = draft1.copy(joints = joints(5, 4))
        val draftVersion2 = switchDao.update(tempDraft2)
        val draft2 = switchDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2)
        assertEquals(VersionPair(insertVersion, draftVersion2), switchDao.fetchVersionPair(insertVersion.id))

        switchDao.deleteDrafts(insertVersion.id)
        assertEquals(VersionPair(insertVersion, null), switchDao.fetchVersionPair(insertVersion.id))

        assertEquals(inserted, switchDao.fetch(insertVersion))
        assertEquals(draft1, switchDao.fetch(draftVersion1))
        assertEquals(draft2, switchDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { switchDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun shouldSuccessfullyDeleteDraftSwitches() {
        val draftSwitch = draft(switch(0))
        val insertedSwitchVersion = switchDao.insert(draftSwitch)
        val insertedSwitch = switchDao.fetch(insertedSwitchVersion)

        val deletedId = switchDao.deleteUnpublishedDraft(insertedSwitchVersion.id)
        assertEquals(insertedSwitchVersion.id, deletedId.id)
        assertFalse(switchDao.fetchAllVersions().any { id -> id == insertedSwitchVersion.id })

        // Verify that we can still fetch the deleted row with version
        assertEquals(insertedSwitch, switchDao.fetch(insertedSwitchVersion))
    }

    @Test
    fun shouldThrowExceptionWhenDeletingNormalSwitch() {
        val switch = switch(1)
        val insertedSwitch = switchDao.insert(switch)

        assertThrows<NoSuchEntityException> {
            switchDao.deleteUnpublishedDraft(insertedSwitch.id)
        }
    }

}
