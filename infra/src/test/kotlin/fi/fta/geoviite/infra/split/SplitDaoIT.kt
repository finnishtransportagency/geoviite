package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.draft
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Test
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
            locationTrack(trackNumberId = trackNumberId) to alignment
        )

        val targetTrack = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)) to alignment
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        val split = splitDao.saveSplit(
            sourceTrack.id,
            listOf(SplitTargetSaveRequest(targetTrack.id, 0..0)),
            listOf(relinkedSwitchId),
        ).let(splitDao::getSplit)

        assertTrue { split.bulkTransferState == BulkTransferState.PENDING }
        assertNull(split.errorCause)
        assertNull(split.publicationId)
        assertEquals(sourceTrack.id, split.locationTrackId)
        assertContains(split.targetLocationTracks, SplitTarget(split.id, targetTrack.id, 0..0))
        assertContains(split.relinkedSwitches, relinkedSwitchId)
    }

    @Test
    fun `should update split with new state, errorCause, and publicationId`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId) to alignment
        )

        val targetTrack = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)) to alignment
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        val split = splitDao.saveSplit(
            sourceTrack.id,
            listOf(SplitTargetSaveRequest(targetTrack.id, 0..0)),
            listOf(relinkedSwitchId),
        ).let(splitDao::getSplit)

        val publicationId = publicationDao.createPublication("SPLIT PUBLICATION")
        val updatedSplit = splitDao.updateSplitState(
            split.copy(
                bulkTransferState = BulkTransferState.FAILED,
                errorCause = "TEST",
                publicationId = publicationId,
            )
        ).let(splitDao::getSplit)

        assertEquals(BulkTransferState.FAILED, updatedSplit.bulkTransferState)
        assertEquals("TEST", updatedSplit.errorCause)
        assertEquals(publicationId, updatedSplit.publicationId)
    }

    @Test
    fun `should fetch unfinished splits only`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId) to alignment
        )

        val targetTrack1 = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)) to alignment
        )

        val targetTrack2 = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)) to alignment
        )

        val relinkedSwitchId1 = insertUniqueSwitch().id
        val relinkedSwitchId2 = insertUniqueSwitch().id

        val doneSplit = splitDao.saveSplit(
            sourceTrack.id,
            listOf(SplitTargetSaveRequest(targetTrack1.id, 0..0)),
            listOf(relinkedSwitchId1),
        ).also { splitId ->
            val split = splitDao.getSplit(splitId)
            splitDao.updateSplitState(split.copy(bulkTransferState = BulkTransferState.DONE))
        }

        val pendingSplitId = splitDao.saveSplit(
            sourceTrack.id,
            listOf(SplitTargetSaveRequest(targetTrack2.id, 0..0)),
            listOf(relinkedSwitchId2),
        )

        val splits = splitDao.fetchUnfinishedSplits()

        assertTrue { splits.any { s -> s.id == pendingSplitId } }
        assertTrue { splits.none { s -> s.id == doneSplit } }
    }

    @Test
    fun `should delete split`() {
        val trackNumberId = insertOfficialTrackNumber()
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId) to alignment
        )

        val targetTrack1 = insertLocationTrack(
            draft(locationTrack(trackNumberId = trackNumberId)) to alignment
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        val splitId = splitDao.saveSplit(
            sourceTrack.id,
            listOf(SplitTargetSaveRequest(targetTrack1.id, 0..0)),
            listOf(relinkedSwitchId),
        )

        assertTrue { splitDao.fetchUnfinishedSplits().any { it.id == splitId } }

        splitDao.deleteSplit(splitId)

        assertTrue { splitDao.fetchUnfinishedSplits().none { it.id == splitId } }
    }
}
