package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class SplitDaoIT @Autowired constructor(val splitDao: SplitDao, val publicationDao: PublicationDao) : DBTestBase() {

    @Test
    fun `should save split in pending state`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val sourceTrack = mainOfficialContext.save(locationTrack(trackNumberId), geometry)
        val targetTrack = mainDraftContext.save(locationTrack(trackNumberId), geometry)

        val relinkedSwitchId = mainOfficialContext.createSwitch().id

        val split =
            splitDao
                .saveSplit(
                    sourceTrack,
                    listOf(SplitTarget(targetTrack.id, 0..0, SplitTargetOperation.CREATE)),
                    listOf(relinkedSwitchId),
                    updatedDuplicates = emptyList(),
                )
                .let(splitDao::getOrThrow)

        assertTrue { split.bulkTransferState == BulkTransferState.PENDING }
        assertNull(split.publicationId)
        assertEquals(sourceTrack.id, split.sourceLocationTrackId)
        assertEquals(sourceTrack, split.sourceLocationTrackVersion)
        assertContains(split.targetLocationTracks, SplitTarget(targetTrack.id, 0..0, SplitTargetOperation.CREATE))
        assertContains(split.relinkedSwitches, relinkedSwitchId)
    }

    @Test
    fun `should update split with new state, errorCause, and publicationId`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val sourceTrack = mainOfficialContext.save(locationTrack(trackNumberId), geometry)
        val targetTrack = mainDraftContext.save(locationTrack(trackNumberId), geometry)

        val relinkedSwitchId = mainOfficialContext.createSwitch().id

        val split =
            splitDao
                .saveSplit(
                    sourceTrack,
                    listOf(SplitTarget(targetTrack.id, 0..0, SplitTargetOperation.CREATE)),
                    listOf(relinkedSwitchId),
                    updatedDuplicates = emptyList(),
                )
                .let(splitDao::getOrThrow)

        val publicationId =
            publicationDao.createPublication(
                LayoutBranch.main,
                FreeTextWithNewLines.of("SPLIT PUBLICATION"),
                PublicationCause.MANUAL,
                parentId = null,
            )
        val updatedSplit =
            splitDao
                .updateSplit(
                    splitId = split.id,
                    bulkTransferState = BulkTransferState.FAILED,
                    publicationId = publicationId,
                )
                .id
                .let(splitDao::getOrThrow)

        assertEquals(BulkTransferState.FAILED, updatedSplit.bulkTransferState)
        assertEquals(publicationId, updatedSplit.publicationId)
        assertEquals(split.id, splitDao.fetchSplitIdsByPublication(setOf(publicationId)).getValue(publicationId))
    }

    @Test
    fun `should fetch unfinished splits only`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val sourceTrack = mainOfficialContext.save(locationTrack(trackNumberId), geometry)
        val targetTrack1 = mainDraftContext.save(locationTrack(trackNumberId), geometry)
        val targetTrack2 = mainDraftContext.save(locationTrack(trackNumberId), geometry)

        val relinkedSwitchId1 = mainOfficialContext.createSwitch().id
        val relinkedSwitchId2 = mainOfficialContext.createSwitch().id

        val doneSplit =
            splitDao
                .saveSplit(
                    sourceTrack,
                    listOf(SplitTarget(targetTrack1.id, 0..0, SplitTargetOperation.CREATE)),
                    listOf(relinkedSwitchId1),
                    updatedDuplicates = emptyList(),
                )
                .also { splitId ->
                    val split = splitDao.getOrThrow(splitId)
                    splitDao.updateSplit(split.id, bulkTransferState = BulkTransferState.DONE)
                }

        val pendingSplitId =
            splitDao.saveSplit(
                sourceTrack,
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
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val sourceTrack = mainOfficialContext.save(locationTrack(trackNumberId), geometry)
        val someDuplicateTrack = mainDraftContext.save(locationTrack(trackNumberId), geometry)
        val targetTrack1 = mainDraftContext.save(locationTrack(trackNumberId), geometry)

        val relinkedSwitchId = mainOfficialContext.createSwitch().id

        val splitId =
            splitDao.saveSplit(
                sourceTrack,
                listOf(SplitTarget(targetTrack1.id, 0..0, SplitTargetOperation.OVERWRITE)),
                listOf(relinkedSwitchId),
                updatedDuplicates = listOf(someDuplicateTrack.id),
            )

        assertTrue { splitDao.fetchUnfinishedSplits(LayoutBranch.main).any { it.id == splitId } }

        splitDao.deleteSplit(splitId)

        assertTrue { splitDao.fetchUnfinishedSplits(LayoutBranch.main).none { it.id == splitId } }
    }

    @Test
    fun `Should fetch split header`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val sourceTrack = mainOfficialContext.save(locationTrack(trackNumberId), geometry)
        val targetTrack1 = mainDraftContext.save(locationTrack(trackNumberId), geometry)

        val relinkedSwitchId = mainOfficialContext.createSwitch().id

        val splitId =
            splitDao.saveSplit(
                sourceTrack,
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

    @Test
    fun `Initial split bulk transfer state is PENDING`() {
        val splitId = createSplit()
        assertEquals(BulkTransferState.PENDING, splitDao.getOrThrow(splitId).bulkTransferState)
    }

    @Test
    fun `Split bulk transfer state can be updated`() {
        val splitId = createSplit()
        val publicationId =
            publicationDao.createPublication(
                LayoutBranch.main,
                FreeTextWithNewLines.of("test: bulk transfer state update"),
                PublicationCause.MANUAL,
                parentId = null,
            )

        BulkTransferState.entries.forEach { newBulkTransferState ->
            when (newBulkTransferState) {
                BulkTransferState.PENDING ->
                    splitDao.updateSplit(splitId = splitId, bulkTransferState = newBulkTransferState)

                else ->
                    splitDao.updateSplit(
                        splitId = splitId,
                        publicationId = publicationId,
                        bulkTransferState = newBulkTransferState,
                        bulkTransferId = testDBService.getUnusedBulkTransferId(),
                    )
            }
            splitDao.updateSplit(splitId, bulkTransferState = newBulkTransferState)
            assertEquals(newBulkTransferState, splitDao.getOrThrow(splitId).bulkTransferState)
        }
    }

    @Test
    fun `Split bulk transfer id can be updated`() {
        val splitId = createSplit()

        val bulkTransferId = testDBService.getUnusedBulkTransferId()
        splitDao.updateSplit(splitId, bulkTransferId = bulkTransferId)

        assertEquals(bulkTransferId, splitDao.getOrThrow(splitId).bulkTransferId)
    }

    private fun createSplit(): IntId<Split> {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val sourceTrack = mainOfficialContext.save(locationTrack(trackNumberId), geometry)
        val someDuplicateTrack = mainOfficialContext.save(locationTrack(trackNumberId), geometry)
        val targetTrack = mainDraftContext.save(locationTrack(trackNumberId), geometry)

        val relinkedSwitchId = mainOfficialContext.createSwitch().id

        return splitDao.saveSplit(
            sourceTrack,
            listOf(SplitTarget(targetTrack.id, 0..0, SplitTargetOperation.OVERWRITE)),
            listOf(relinkedSwitchId),
            updatedDuplicates = listOf(someDuplicateTrack.id),
        )
    }
}
