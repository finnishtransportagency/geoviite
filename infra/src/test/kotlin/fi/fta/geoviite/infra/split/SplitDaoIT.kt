package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
class SplitDaoIT @Autowired constructor(
    val splitDao: SplitDao,
    val publicationDao: PublicationDao,
) : DBTestBase() {

    @Test
    fun `should save split in pending state`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false) to alignment
        )

        val targetTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true) to alignment
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        val split = splitDao.saveSplit(
            sourceTrack.rowVersion,
            listOf(SplitTarget(targetTrack.id, 0..0, SplitTargetOperation.CREATE)),
            listOf(relinkedSwitchId),
            updatedDuplicates = emptyList(),
        ).let(splitDao::getOrThrow)

        assertTrue { split.bulkTransferState == BulkTransferState.PENDING }
        assertNull(split.publicationId)
        assertEquals(sourceTrack.id, split.sourceLocationTrackId)
        assertEquals(sourceTrack.rowVersion, split.sourceLocationTrackVersion)
        assertContains(
            split.targetLocationTracks,
            SplitTarget(targetTrack.id, 0..0, SplitTargetOperation.CREATE),
        )
        assertContains(split.relinkedSwitches, relinkedSwitchId)
    }

    @Test
    fun `should update split with new state, errorCause, and publicationId`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false) to alignment
        )

        val targetTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true) to alignment
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        val split = splitDao.saveSplit(
            sourceTrack.rowVersion,
            listOf(SplitTarget(targetTrack.id, 0..0, SplitTargetOperation.CREATE)),
            listOf(relinkedSwitchId),
            updatedDuplicates = emptyList(),
        ).let(splitDao::getOrThrow)

        val publicationId = publicationDao.createPublication("SPLIT PUBLICATION")
        val updatedSplit = splitDao.updateSplit(
            splitId = split.id,
            bulkTransferState = BulkTransferState.FAILED,
            publicationId = publicationId,
        ).id.let(splitDao::getOrThrow)

        assertEquals(BulkTransferState.FAILED, updatedSplit.bulkTransferState)
        assertEquals(publicationId, updatedSplit.publicationId)
        assertEquals(split.id, splitDao.fetchSplitIdByPublication(publicationId))
    }

    @Test
    fun `should fetch unfinished splits only`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false) to alignment
        )

        val targetTrack1 = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true) to alignment
        )

        val targetTrack2 = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true) to alignment
        )

        val relinkedSwitchId1 = insertUniqueSwitch().id
        val relinkedSwitchId2 = insertUniqueSwitch().id

        val doneSplit = splitDao.saveSplit(
            sourceTrack.rowVersion,
            listOf(SplitTarget(targetTrack1.id, 0..0, SplitTargetOperation.CREATE)),
            listOf(relinkedSwitchId1),
            updatedDuplicates = emptyList(),
        ).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplit(split.id, bulkTransferState = BulkTransferState.DONE)
        }

        val pendingSplitId = splitDao.saveSplit(
            sourceTrack.rowVersion,
            listOf(SplitTarget(targetTrack2.id, 0..0, SplitTargetOperation.CREATE)),
            listOf(relinkedSwitchId2),
            updatedDuplicates = emptyList(),
        )

        val splits = splitDao.fetchUnfinishedSplits(LayoutBranch.main)

        assertTrue { splits.any { s -> s.id == pendingSplitId } }
        assertTrue { splits.none { s -> s.id == doneSplit } }
    }

    @Test
    fun `should delete split`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false) to alignment
        )

        val someDuplicateTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true) to alignment
        )

        val targetTrack1 = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true) to alignment
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        val splitId = splitDao.saveSplit(
            sourceTrack.rowVersion,
            listOf(SplitTarget(targetTrack1.id, 0..0, SplitTargetOperation.OVERWRITE)),
            listOf(relinkedSwitchId),
            updatedDuplicates = listOf(someDuplicateTrack.id)
        )

        assertTrue { splitDao.fetchUnfinishedSplits(LayoutBranch.main).any { it.id == splitId } }

        splitDao.deleteSplit(splitId)

        assertTrue { splitDao.fetchUnfinishedSplits(LayoutBranch.main).none { it.id == splitId } }
    }

    @Test
    fun `Should fetch split header`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false) to alignment
        )

        val targetTrack1 = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true) to alignment
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        val splitId = splitDao.saveSplit(
            sourceTrack.rowVersion,
            listOf(SplitTarget(targetTrack1.id, 0..0, SplitTargetOperation.CREATE)),
            listOf(relinkedSwitchId),
            updatedDuplicates = emptyList(),
        )

        val splitHeader = splitDao.getSplitHeader(splitId)
        assertEquals(splitId, splitHeader.id)
        assertEquals(sourceTrack.id, splitHeader.locationTrackId)
        assertEquals(BulkTransferState.PENDING, splitHeader.bulkTransferState)
    }

    @Test
    fun `Should fetch split`() {
        val splitId = createSplit()
        val fetchedSplit = splitDao.getOrThrow(splitId)
        assertEquals(splitId, fetchedSplit.id)
    }

    @Test
    fun `Should throw instead of fetching split`() {
        assertThrows<NoSuchEntityException> { splitDao.getOrThrow(IntId(-1)) }
    }

    private fun createSplit(): IntId<Split> {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false) to alignment
        )

        val someDuplicateTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false) to alignment
        )

        val targetTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = true) to alignment
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        return splitDao.saveSplit(
            sourceTrack.rowVersion,
            listOf(SplitTarget(targetTrack.id, 0..0, SplitTargetOperation.OVERWRITE)),
            listOf(relinkedSwitchId),
            updatedDuplicates = listOf(someDuplicateTrack.id),
        )
    }
}
