package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.someOid
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
            listOf<Oid<LayoutTrackNumber>>(someOid(), someOid()).map { oid ->
                mainOfficialContext.createLayoutTrackNumberWithOid(oid).let { rowVersion ->
                    oid to trackNumberDao.get(mainOfficialContext.context, rowVersion.id)!!.id
                }
            }

        testOids
            .map { (oid, _) -> oid }
            .let { oidList -> getByExternalIds(trackNumberDao, mainOfficialContext.context, oidList) }
            .let { oidMapping -> testOids.forEach { (oid, id) -> assertEquals(id, oidMapping[oid]?.id) } }
    }

    @Test
    fun `Single external id can be found`() {
        val testOids =
            listOf<Oid<LayoutTrackNumber>>(someOid(), someOid()).map { oid ->
                mainOfficialContext.createLayoutTrackNumberWithOid(oid).let { rowVersion ->
                    oid to trackNumberDao.get(mainOfficialContext.context, rowVersion.id)!!.id
                }
            }

        testOids.forEach { (oid, id) -> assertEquals(id, getByExternalId(trackNumberDao, oid)?.id) }
    }

    @Test
    fun `External ids which are not found are returned as nulls`() {
        val testOids = listOf<Oid<LayoutTrackNumber>>(someOid(), someOid())
        val notExistingOid = Oid<LayoutTrackNumber>("999.888.777")
        testOids.forEach { oid -> mainOfficialContext.createLayoutTrackNumberWithOid(oid) }

        val oidResult = getByExternalIds(trackNumberDao, mainOfficialContext.context, testOids + listOf(notExistingOid))
        assertEquals(3, oidResult.size)
        assertEquals(null, oidResult[notExistingOid])
    }
}
