package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

    @Test
    fun referenceLineVersioningWorks() {
        val trackNumberId = insertOfficialTrackNumber()
        val tempAlignment = alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0)))
        val alignmentVersion = alignmentDao.insert(tempAlignment)
        val tempTrack = referenceLine(
            trackNumberId = trackNumberId,
            startAddress = TrackMeter(12, 13),
            alignment = tempAlignment,
            alignmentVersion = alignmentVersion,
        )
        val insertVersion = referenceLineDao.insert(tempTrack)
        val inserted = referenceLineDao.fetch(insertVersion)
        assertMatches(tempTrack, inserted)
        assertEquals(VersionPair(insertVersion, null), referenceLineDao.fetchVersionPair(insertVersion.id))

        val tempDraft1 = draft(inserted).copy(startAddress = TrackMeter(2, 4))
        val draftVersion1 = referenceLineDao.insert(tempDraft1)
        val draft1 = referenceLineDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1)
        assertEquals(VersionPair(insertVersion, draftVersion1), referenceLineDao.fetchVersionPair(insertVersion.id))

        val newTempAlignment = alignment(segment(Point(2.0, 2.0), Point(4.0, 4.0)))
        val newAlignmentVersion = alignmentDao.insert(newTempAlignment)
        val tempDraft2 = draft1.copy(alignmentVersion = newAlignmentVersion, length = newTempAlignment.length)
        val draftVersion2 = referenceLineDao.update(tempDraft2)
        val draft2 = referenceLineDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2)
        assertEquals(VersionPair(insertVersion, draftVersion2), referenceLineDao.fetchVersionPair(insertVersion.id))

        referenceLineDao.deleteDrafts(insertVersion.id)
        alignmentDao.deleteOrphanedAlignments()
        assertEquals(VersionPair(insertVersion, null), referenceLineDao.fetchVersionPair(insertVersion.id))

        assertEquals(inserted, referenceLineDao.fetch(insertVersion))
        assertEquals(draft1, referenceLineDao.fetch(draftVersion1))
        assertEquals(draft2, referenceLineDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { referenceLineDao.fetch(draftVersion2.next()) }
    }
}
