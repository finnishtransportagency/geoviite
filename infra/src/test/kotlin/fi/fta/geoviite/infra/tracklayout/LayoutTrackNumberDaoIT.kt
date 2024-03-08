package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.tracklayout.LayoutState.DELETED
import fi.fta.geoviite.infra.tracklayout.LayoutState.IN_USE
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutTrackNumberDaoIT @Autowired constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
): DBTestBase() {

    @Test
    fun trackNumberIsStoredAndLoadedOk() {
        val original = TrackLayoutTrackNumber(
            number = getUnusedTrackNumber(),
            description = FreeText("empty-test-track-number"),
            state = IN_USE,
            externalId = null,
            contextData = LayoutContextData.newDraft(),
        )
        val (id, version) = trackNumberDao.insert(original)
        val fromDb = trackNumberDao.fetch(version)
        assertEquals(id, fromDb.id)
        assertMatches(original, fromDb)
    }

    @Test
    fun trackNumberExternalIdIsUnique() {
        val oid = Oid<TrackLayoutTrackNumber>("99.99.99.99.99.99")

        // If the OID is already in use, remove it
        transactional {
            val deleteSql = "delete from layout.track_number where external_id = :external_id"
            jdbc.update(deleteSql, mapOf("external_id" to oid))
        }

        val tn1 = trackNumber(getUnusedTrackNumber()).copy(externalId = oid)
        val tn2 = trackNumber(getUnusedTrackNumber()).copy(externalId = oid)
        trackNumberDao.insert(tn1)
        assertThrows<DuplicateKeyException> { trackNumberDao.insert(tn2) }
    }

    @Test
    fun trackNumberVersioningWorks() {
        val tempTrackNumber = trackNumber(getUnusedTrackNumber(), description = "test 1")
        val (id, insertVersion) = trackNumberDao.insert(tempTrackNumber)
        val inserted = trackNumberDao.fetch(insertVersion)
        assertMatches(tempTrackNumber, inserted)
        assertEquals(VersionPair(insertVersion, null), trackNumberDao.fetchVersionPair(id))

        val tempDraft1 = asMainDraft(inserted).copy(description = FreeText("test 2"))
        val draftVersion1 = trackNumberDao.insert(tempDraft1).rowVersion
        val draft1 = trackNumberDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1)
        assertEquals(VersionPair(insertVersion, draftVersion1), trackNumberDao.fetchVersionPair(id))

        val tempDraft2 = draft1.copy(description = FreeText("test 3"))
        val draftVersion2 = trackNumberDao.update(tempDraft2).rowVersion
        val draft2 = trackNumberDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2)
        assertEquals(VersionPair(insertVersion, draftVersion2), trackNumberDao.fetchVersionPair(id))

        trackNumberDao.deleteDraft(insertVersion.id).rowVersion
        assertEquals(VersionPair(insertVersion, null), trackNumberDao.fetchVersionPair(id))

        assertEquals(inserted, trackNumberDao.fetch(insertVersion))
        assertEquals(draft1, trackNumberDao.fetch(draftVersion1))
        assertEquals(draft2, trackNumberDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { trackNumberDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun listingTrackNumberVersionsWorks() {
        val officialVersion = insertNewTrackNumber(getUnusedTrackNumber(), false).rowVersion
        val undeletedDraftVersion = insertNewTrackNumber(getUnusedTrackNumber(), true).rowVersion
        val deleteStateDraftVersion = insertNewTrackNumber(getUnusedTrackNumber(), true, DELETED).rowVersion
        val (deletedDraftId, deletedDraftVersion) = insertNewTrackNumber(getUnusedTrackNumber(), true)
        trackNumberDao.deleteDraft(deletedDraftId)

        val official = trackNumberDao.fetchVersions(OFFICIAL, false)
        assertContains(official, officialVersion)
        assertFalse(official.contains(undeletedDraftVersion))
        assertFalse(official.contains(deleteStateDraftVersion))
        assertFalse(official.contains(deletedDraftVersion))

        val draftWithoutDeleted = trackNumberDao.fetchVersions(DRAFT, false)
        assertContains(draftWithoutDeleted, undeletedDraftVersion)
        assertFalse(draftWithoutDeleted.contains(deleteStateDraftVersion))
        assertFalse(draftWithoutDeleted.contains(deletedDraftVersion))

        val draftWithDeleted = trackNumberDao.fetchVersions(DRAFT, true)
        assertContains(draftWithDeleted, undeletedDraftVersion)
        assertContains(draftWithDeleted, deleteStateDraftVersion)
        assertFalse(draftWithDeleted.contains(deletedDraftVersion))
    }

    @Test
    fun fetchOfficialVersionByMomentWorks() {
        val beforeCreationTime = trackNumberDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps
        val (id, firstVersion) = insertNewTrackNumber(getUnusedTrackNumber(), false)
        val firstVersionTime = trackNumberDao.fetchChangeTime()

        Thread.sleep(1) // Ensure that they get different timestamps
        val updatedVersion = updateOfficial(firstVersion).rowVersion
        val updatedVersionTime = trackNumberDao.fetchChangeTime()

        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(id, beforeCreationTime))
        assertEquals(firstVersion, trackNumberDao.fetchOfficialVersionAtMoment(id, firstVersionTime))
        assertEquals(updatedVersion, trackNumberDao.fetchOfficialVersionAtMoment(id, updatedVersionTime))
    }

    private fun updateOfficial(
        originalVersion: RowVersion<TrackLayoutTrackNumber>,
    ): DaoResponse<TrackLayoutTrackNumber> {
        val original = trackNumberDao.fetch(originalVersion)
        assertFalse(original.isDraft)
        return trackNumberDao.update(original.copy(description = original.description + "_update"))
    }
}
