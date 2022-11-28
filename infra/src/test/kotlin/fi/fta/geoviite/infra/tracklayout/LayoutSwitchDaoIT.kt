package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.error.NoSuchEntityException
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun shouldSuccessfullyDeleteDraftSwitches() {
        val draftSwitch = draft(switch(0))
        val insertedSwitch = switchDao.insert(draftSwitch)

        val deletedId = switchDao.deleteUnpublishedDraft(insertedSwitch.id)
        assertEquals(insertedSwitch.id, deletedId.id)
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
