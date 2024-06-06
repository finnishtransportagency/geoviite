package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
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
) : DBTestBase() {

    @Test
    fun trackNumberIsStoredAndLoadedOk() {
        val original = TrackLayoutTrackNumber(
            number = testDBService.getUnusedTrackNumber(),
            description = FreeText("empty-test-track-number"),
            state = IN_USE,
            externalId = null,
            contextData = LayoutContextData.newDraft(LayoutBranch.main),
        )
        val (id, version) = trackNumberDao.insert(original)
        val fromDb = trackNumberDao.fetch(version)
        assertEquals(id, fromDb.id)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(original, fromDb, contextMatch = false)
    }

    @Test
    fun trackNumberExternalIdIsUnique() {
        val oid = Oid<TrackLayoutTrackNumber>("99.99.99.99.99.99")

        // If the OID is already in use, remove it
        transactional {
            val deleteSql = "delete from layout.track_number where external_id = :external_id"
            jdbc.update(deleteSql, mapOf("external_id" to oid))
        }

        val tn1 = trackNumber(testDBService.getUnusedTrackNumber(), externalId = oid, draft = false)
        val tn2 = trackNumber(testDBService.getUnusedTrackNumber(), externalId = oid, draft = false)
        trackNumberDao.insert(tn1)
        assertThrows<DuplicateKeyException> { trackNumberDao.insert(tn2) }
    }

    @Test
    fun trackNumberVersioningWorks() {
        val tempTrackNumber = trackNumber(testDBService.getUnusedTrackNumber(), description = "test 1", draft = false)
        val (id, insertVersion) = trackNumberDao.insert(tempTrackNumber)
        val inserted = trackNumberDao.fetch(insertVersion)
        assertMatches(tempTrackNumber, inserted, contextMatch = false)
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.draft, id))

        val tempDraft1 = asMainDraft(inserted).copy(description = FreeText("test 2"))
        val draftVersion1 = trackNumberDao.insert(tempDraft1).rowVersion
        val draft1 = trackNumberDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1, contextMatch = false)
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion1, trackNumberDao.fetchVersion(MainLayoutContext.draft, id))

        val tempDraft2 = draft1.copy(description = FreeText("test 3"))
        val draftVersion2 = trackNumberDao.update(tempDraft2).rowVersion
        val draft2 = trackNumberDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2, contextMatch = false)
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion2, trackNumberDao.fetchVersion(MainLayoutContext.draft, id))

        trackNumberDao.deleteDraft(LayoutBranch.main, insertVersion.id).rowVersion
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.draft, id))

        assertEquals(inserted, trackNumberDao.fetch(insertVersion))
        assertEquals(draft1, trackNumberDao.fetch(draftVersion1))
        assertEquals(draft2, trackNumberDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { trackNumberDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun listingTrackNumberVersionsWorks() {
        val officialVersion = mainOfficialContext.createLayoutTrackNumber().rowVersion
        val undeletedDraftVersion = mainDraftContext.createLayoutTrackNumber().rowVersion
        val deleteStateDraftVersion = mainDraftContext.insert(
            trackNumber(number = testDBService.getUnusedTrackNumber(), state = DELETED),
        ).rowVersion
        val (_, deletedDraftVersion) = mainDraftContext.createLayoutTrackNumber()
            .also { (id, _) -> trackNumberDao.deleteDraft(LayoutBranch.main, id) }

        val official = trackNumberDao.fetchVersions(MainLayoutContext.official, false)
        assertContains(official, officialVersion)
        assertFalse(official.contains(undeletedDraftVersion))
        assertFalse(official.contains(deleteStateDraftVersion))
        assertFalse(official.contains(deletedDraftVersion))

        val draftWithoutDeleted = trackNumberDao.fetchVersions(MainLayoutContext.draft, false)
        assertContains(draftWithoutDeleted, undeletedDraftVersion)
        assertFalse(draftWithoutDeleted.contains(deleteStateDraftVersion))
        assertFalse(draftWithoutDeleted.contains(deletedDraftVersion))

        val draftWithDeleted = trackNumberDao.fetchVersions(MainLayoutContext.draft, true)
        assertContains(draftWithDeleted, undeletedDraftVersion)
        assertContains(draftWithDeleted, deleteStateDraftVersion)
        assertFalse(draftWithDeleted.contains(deletedDraftVersion))
    }

    @Test
    fun fetchOfficialVersionByMomentWorks() {
        val beforeCreationTime = trackNumberDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps
        val (id, firstVersion) = mainOfficialContext.createLayoutTrackNumber()
        val firstVersionTime = trackNumberDao.fetchChangeTime()

        Thread.sleep(1) // Ensure that they get different timestamps
        val updatedVersion = updateOfficial(firstVersion).rowVersion
        val updatedVersionTime = trackNumberDao.fetchChangeTime()

        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, id, beforeCreationTime))
        assertEquals(firstVersion, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, id, firstVersionTime))
        assertEquals(updatedVersion, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, id, updatedVersionTime))
        // TODO: GVT-2616 test with design branches
    }

    private fun updateOfficial(
        originalVersion: RowVersion<TrackLayoutTrackNumber>,
    ): DaoResponse<TrackLayoutTrackNumber> {
        val original = trackNumberDao.fetch(originalVersion)
        assertFalse(original.isDraft)
        return trackNumberDao.update(original.copy(description = original.description + "_update"))
    }
}
