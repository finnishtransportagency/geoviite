package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.tracklayout.LayoutState.IN_USE
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutTrackNumberDaoIT @Autowired constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
): ITTestBase() {

    @Test
    fun trackNumberIsStoredAndLoadedOk() {
        val original = TrackLayoutTrackNumber(
            number = getUnusedTrackNumber(),
            description = FreeText("empty-test-track-number"),
            state = IN_USE,
            externalId = null,
        )
        val newId = trackNumberDao.insert(original)
        val fromDb = trackNumberDao.fetch(newId)
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
        val trackNumber = getUnusedTrackNumber()
        val tempTrackNumber = trackNumber(trackNumber, description = "test 1")
        val insertVersion = trackNumberDao.insert(tempTrackNumber)
        val inserted = trackNumberDao.fetch(insertVersion)
        assertMatches(tempTrackNumber, inserted)
        assertEquals(VersionPair(insertVersion, null), trackNumberDao.fetchVersionPair(insertVersion.id))

        val tempDraft1 = draft(inserted).copy(description = FreeText("test 2"))
        val draftVersion1 = trackNumberDao.insert(tempDraft1)
        val draft1 = trackNumberDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1)
        assertEquals(VersionPair(insertVersion, draftVersion1), trackNumberDao.fetchVersionPair(insertVersion.id))

        val tempDraft2 = draft1.copy(description = FreeText("test 3"))
        val draftVersion2 = trackNumberDao.update(tempDraft2)
        val draft2 = trackNumberDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2)
        assertEquals(VersionPair(insertVersion, draftVersion2), trackNumberDao.fetchVersionPair(insertVersion.id))

        trackNumberDao.deleteDrafts(insertVersion.id)
        assertEquals(VersionPair(insertVersion, null), trackNumberDao.fetchVersionPair(insertVersion.id))

        assertEquals(inserted, trackNumberDao.fetch(insertVersion))
        assertEquals(draft1, trackNumberDao.fetch(draftVersion1))
        assertEquals(draft2, trackNumberDao.fetch(draftVersion2))
    }
}
