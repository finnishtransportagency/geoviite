package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions
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
        Assertions.assertNotNull(updatedTrackNumber.externalId)
    }

    @Test
    fun deletingDraftOnlyTrackNumberDeletesItAndReferenceLineAndAlignment() {
        val items = createTrackNumberAndReferenceLineAndAlignment()
        Assertions.assertNotNull(items.second.first.alignmentVersion)
        val trackNumberId = items.first.id as IntId<LayoutTrackNumber>

        Assertions.assertDoesNotThrow { trackNumberService.deleteDraftOnlyTrackNumberAndReferenceLine(trackNumberId) }
        assertThrows<NoSuchEntityException> {
            referenceLineService.getOrThrow(PublishType.DRAFT, items.second.first.id as IntId<ReferenceLine>)
        }
        assertThrows<NoSuchEntityException> {
            trackNumberService.getOrThrow(PublishType.DRAFT, trackNumberId)
        }
        assertThrows<NoSuchEntityException> { alignmentDao.fetch(items.second.first.alignmentVersion!!) }
    }

    @Test
    fun tryingToDeletePublishedTrackNumberThrows() {
        val items = createTrackNumberAndReferenceLineAndAlignment()
        trackNumberService.publish(items.first.id as IntId<LayoutTrackNumber>)
        referenceLineService.publish(items.second.first.id as IntId<ReferenceLine>)

        assertThrows<DeletingFailureException> {
            trackNumberService.deleteDraftOnlyTrackNumberAndReferenceLine(items.first.id as IntId<LayoutTrackNumber>)
        }
    }

    fun createTrackNumberAndReferenceLineAndAlignment(): Pair<LayoutTrackNumber, Pair<ReferenceLine, LayoutAlignment>> {
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

        val referenceLineLayoutAlignment = referenceLineService.getByTrackNumberWithAlignment(
            PublishType.DRAFT,
            trackNumber.id as IntId<LayoutTrackNumber>
        )!! // Always exists, since we just created it

        return trackNumber to referenceLineLayoutAlignment
    }
}
