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
}
