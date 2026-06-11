package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class ExternalIdDaoIT @Autowired constructor(private val trackNumberDao: LayoutTrackNumberDao) : DBTestBase() {

    @Test
    fun `Multiple external ids can be found`() {
        val testOids =
            (1..2).map {
                mainOfficialContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
            }

        testOids
            .map { (_, oid) -> oid }
            .let { oidList -> trackNumberDao.getByExternalIds(mainOfficialContext.context, oidList) }
            .let { oidMapping -> testOids.forEach { (id, oid) -> assertEquals(id, oidMapping[oid]?.id) } }
    }

    @Test
    fun `External ids can be found one-by-one`() {
        val testOids =
            (1..2).map {
                mainOfficialContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
            }

        testOids.forEach { (id, oid) -> assertEquals(id, trackNumberDao.getByExternalId(oid)?.id) }
    }

    @Test
    fun `External ids which are not found are returned as nulls when searching for multiple oids`() {
        val notExistingOid = Oid<LayoutTrackNumber>("999.888.777")
        val testOids =
            (1..2).map {
                mainOfficialContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber())).second
            }

        val oidResult = trackNumberDao.getByExternalIds(mainOfficialContext.context, testOids + listOf(notExistingOid))
        assertEquals(3, oidResult.size)
        assertEquals(null, oidResult[notExistingOid])
    }
}
