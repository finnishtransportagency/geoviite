package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.TrackMeter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class ReferenceLineDaoIT @Autowired constructor(
    private val alignmentDao: LayoutAlignmentDao,
    private val referenceLineDao: ReferenceLineDao,
): ITTestBase() {

    @Test
    fun referenceLineSaveAndLoadWorks() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment()
        val alignmentVersion = alignmentDao.insert(alignment)
        val referenceLine = referenceLine(trackNumberId, alignment).copy(
            startAddress = TrackMeter(KmNumber(10), 125.5, 3),
            alignmentVersion = alignmentVersion,
        )

        val version = referenceLineDao.insert(referenceLine)
        assertEquals(version, referenceLineDao.fetchVersion(version.id, OFFICIAL))
        assertEquals(version, referenceLineDao.fetchVersion(version.id, DRAFT))
        val fromDb = referenceLineDao.fetch(version)
        assertMatches(referenceLine, fromDb)

        val updatedLine = fromDb.copy(
            startAddress = TrackMeter(KmNumber(12), 321),
        )
        val updatedVersion = referenceLineDao.update(updatedLine)
        assertEquals(updatedVersion.id, version.id)
        assertEquals(updatedVersion, referenceLineDao.fetchVersion(version.id, OFFICIAL))
        assertEquals(updatedVersion, referenceLineDao.fetchVersion(version.id, DRAFT))
        val updatedFromDb = referenceLineDao.fetch(updatedVersion)
        assertMatches(updatedLine, updatedFromDb)
    }
}
