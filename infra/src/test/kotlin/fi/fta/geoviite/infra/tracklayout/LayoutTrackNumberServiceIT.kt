package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles


@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutTrackNumberServiceIT @Autowired constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val referenceLineService: ReferenceLineService,
    private val alignmentDao: LayoutAlignmentDao,
): ITTestBase() {

    @Test
    fun updatingExternalIdWorks() {
        val saveRequest = TrackNumberSaveRequest(
            getUnusedTrackNumber(), FreeText("description"), LayoutState.IN_USE, TrackMeter(
                KmNumber(5555), 5.5, 1
            )
        )
        val id = trackNumberService.insert(saveRequest)
        val trackNumber = trackNumberService.getDraft(id)

        Assertions.assertNull(trackNumber.externalId)

        trackNumberService.updateExternalId(trackNumber.id as IntId, externalIdForTrackNumber())

        val updatedTrackNumber = trackNumberService.getDraft(id)
        assertNotNull(updatedTrackNumber.externalId)
    }

    @Test
    fun deletingDraftOnlyTrackNumberDeletesItAndReferenceLineAndAlignment() {
        val (trackNumber, referenceLine, alignment) = createTrackNumberAndReferenceLineAndAlignment()
        assertEquals(referenceLine.alignmentVersion?.id, alignment.id)
        val trackNumberId = trackNumber.id as IntId

        assertDoesNotThrow { trackNumberService.deleteDraftOnlyTrackNumberAndReferenceLine(trackNumberId) }
        assertThrows<NoSuchEntityException> {
            referenceLineService.getOrThrow(DRAFT, referenceLine.id as IntId)
        }
        assertThrows<NoSuchEntityException> {
            trackNumberService.getOrThrow(DRAFT, trackNumberId)
        }
        assertFalse(alignmentDao.fetchVersions().map { rv -> rv.id }.contains(alignment.id))
    }

    @Test
    fun tryingToDeletePublishedTrackNumberThrows() {
        val (trackNumber, referenceLine, _) = createTrackNumberAndReferenceLineAndAlignment()
        trackNumberService.publish(trackNumber.id as IntId)
        referenceLineService.publish(referenceLine.id as IntId)

        assertThrows<DeletingFailureException> {
            trackNumberService.deleteDraftOnlyTrackNumberAndReferenceLine(trackNumber.id as IntId)
        }
    }

    fun createTrackNumberAndReferenceLineAndAlignment(): Triple<TrackLayoutTrackNumber, ReferenceLine, LayoutAlignment> {
        val saveRequest = TrackNumberSaveRequest(
            getUnusedTrackNumber(),
            FreeText(trackNumberDescription),
            LayoutState.IN_USE,
            TrackMeter(
                KmNumber(5555), 5.5, 1
            )
        )
        val id = trackNumberService.insert(saveRequest)
        val trackNumber = trackNumberService.getDraft(id)

        val (referenceLine, alignment) = referenceLineService.getByTrackNumberWithAlignment(
            DRAFT,
            trackNumber.id as IntId<TrackLayoutTrackNumber>
        )!! // Always exists, since we just created it

        return Triple(trackNumber, referenceLine, alignment)
    }
}
