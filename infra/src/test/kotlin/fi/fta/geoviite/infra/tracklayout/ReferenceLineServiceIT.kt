package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.RowVersion
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles


@ActiveProfiles("dev", "test")
@SpringBootTest
class ReferenceLineServiceIT @Autowired constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val referenceLineService: ReferenceLineService,
): ITTestBase() {

    @Test
    fun creatingAndDeletingUnpublishedReferenceLineWithAlignmentWorks() {
        val trackNumber = createTrackNumber() // automatically creates first version of reference line
        val (savedLine, savedAlignment) = referenceLineService.getByTrackNumberWithAlignment(DRAFT, trackNumber)
            ?: throw IllegalStateException("Reference line was not automatically created")
        assertTrue(alignmentExists(savedLine.alignmentVersion!!.id))
        assertEquals(savedLine.alignmentVersion?.id, savedAlignment.id as IntId)
        assertThrows<DataIntegrityViolationException> { trackNumberService.deleteUnpublishedDraft(trackNumber) }
        referenceLineService.deleteUnpublishedDraft(savedLine.id as IntId)
        assertFalse(alignmentExists(savedLine.alignmentVersion!!.id))
        assertDoesNotThrow { trackNumberService.deleteUnpublishedDraft(trackNumber) }
    }

    @Test
    fun deletingOfficialAlignmentThrowsException() {
        val trackNumber = createAndPublishTrackNumber()
        val referenceLineId = referenceLineService.getByTrackNumber(DRAFT, trackNumber)?.id as IntId
        referenceLineService.publish(referenceLineId)
        val (line, _) = referenceLineService.getWithAlignment(OFFICIAL, referenceLineId)
        assertNull(line.draft)
        assertThrows<NoSuchEntityException> { referenceLineService.deleteUnpublishedDraft(referenceLineId) }
    }

    @Test
    fun referenceLineAddAndUpdateWorks() {
        val trackNumberId = createTrackNumber() // First version is created automatically

        val referenceLine = referenceLineService.getByTrackNumber(DRAFT, trackNumberId)
        assertNotNull(referenceLine)
        assertNotNull(referenceLine?.draft)
        assertEquals(0.0, referenceLine?.length)
        assertEquals(trackNumberId, referenceLine?.trackNumberId)
        val changeTimeAfterInsert = referenceLineService.getChangeTime()
        val referenceLineId = referenceLine?.id as IntId

        val address = address(2)
        val updatedLineVersion = referenceLineService.updateTrackNumberReferenceLine(trackNumberId, address)
        assertEquals(referenceLineId, updatedLineVersion.id)
        val updatedLine = referenceLineService.getDraft(referenceLineId)
        assertEquals(address, updatedLine.startAddress)
        assertEquals(trackNumberId, updatedLine.trackNumberId)
        assertEquals(referenceLine.alignmentVersion, updatedLine.alignmentVersion)
        val changeTimeAfterUpdate = referenceLineService.getChangeTime()

        val trackChangeTimes = referenceLineService.getChangeTimes(referenceLineId)
        assertEquals(changeTimeAfterInsert, trackChangeTimes.created)
        assertEquals(changeTimeAfterUpdate, trackChangeTimes.draftChanged)
    }

    @Test
    fun updatingThroughTrackNumberCreatesDraft() {
        val trackNumberId = createTrackNumber() // First version is created automatically
        trackNumberService.publish(trackNumberId)

        val referenceLineId = referenceLineService.getByTrackNumber(DRAFT, trackNumberId)!!.id as IntId
        val (publishedVersion, published) = publishAndVerify(trackNumberId, referenceLineId)

        val editedVersion = referenceLineService.updateTrackNumberReferenceLine(trackNumberId, TrackMeter(3,5))
        assertNotEquals(publishedVersion.id, editedVersion.id)

        val editedDraft = getAndVerifyDraft(publishedVersion.id)
        assertEquals(TrackMeter(3,5), editedDraft.startAddress)
        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val editedVersion2 = referenceLineService.updateTrackNumberReferenceLine(trackNumberId, TrackMeter(8,9))
        assertNotEquals(publishedVersion.id, editedVersion2.id)

        val editedDraft2 = referenceLineService.getDraft(publishedVersion.id)
        assertEquals(TrackMeter(8,9), editedDraft2.startAddress)
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
    }

    @Test
    fun savingCreatesDraft() {
        val trackNumberId = createTrackNumber() // First version is created automatically
        trackNumberService.publish(trackNumberId)

        val referenceLineId = referenceLineService.getByTrackNumber(DRAFT, trackNumberId)!!.id as IntId
        val (publishedVersion, published) = publishAndVerify(trackNumberId, referenceLineId)

        val editedVersion = referenceLineService.saveDraft(published.copy(startAddress = TrackMeter(1, 1)))
        assertNotEquals(publishedVersion.id, editedVersion.id)

        val editedDraft = getAndVerifyDraft(publishedVersion.id)
        assertEquals(TrackMeter(1,1), editedDraft.startAddress)
        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val editedVersion2 = referenceLineService.saveDraft(editedDraft.copy(startAddress = TrackMeter(2, 2)))
        assertNotEquals(publishedVersion.id, editedVersion2.id)

        val editedDraft2 = getAndVerifyDraft(publishedVersion.id)
        assertEquals(TrackMeter(2,2), editedDraft2.startAddress)
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
    }

    @Test
    fun savingWithAlignmentCreatesDraft() {
        val trackNumberId = createTrackNumber() // First version is created automatically
        trackNumberService.publish(trackNumberId)

        val referenceLineId = referenceLineService.getByTrackNumber(DRAFT, trackNumberId)!!.id as IntId
        val (publishedVersion, published) = publishAndVerify(trackNumberId, referenceLineId)

        val alignmentTmp = alignment(segment(2, 10.0, 20.0, 10.0, 20.0))
        val editedVersion = referenceLineService.saveDraft(published, alignmentTmp)
        assertNotEquals(publishedVersion.id, editedVersion.id)

        val (editedDraft, editedAlignment) = getAndVerifyDraftWithAlignment(publishedVersion.id)
        assertEquals(
            alignmentTmp.segments.flatMap(LayoutSegment::points),
            editedAlignment.segments.flatMap(LayoutSegment::points),
        )

        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val alignmentTmp2 = alignment(segment(4, 10.0, 20.0, 10.0, 20.0))
        val editedVersion2 = referenceLineService.saveDraft(editedDraft, alignmentTmp2)
        assertNotEquals(publishedVersion.id, editedVersion2.id)

        val (editedDraft2, editedAlignment2) = getAndVerifyDraftWithAlignment(publishedVersion.id)
        assertEquals(
            alignmentTmp2.segments.flatMap(LayoutSegment::points),
            editedAlignment2.segments.flatMap(LayoutSegment::points),
        )
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        assertNotEquals(editedDraft.alignmentVersion!!.version, editedDraft2.alignmentVersion!!.version)
    }

    private fun getAndVerifyDraft(id: IntId<ReferenceLine>): ReferenceLine {
        val draft = referenceLineService.getDraft(id)
        assertEquals(id, draft.id)
        assertNotNull(draft.draft)
        return draft
    }

    private fun getAndVerifyDraftWithAlignment(id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> {
        val (draft, alignment) = referenceLineService.getWithAlignment(DRAFT, id)
        assertEquals(id, draft.id)
        assertNotNull(draft.draft)
        assertEquals(draft.alignmentVersion!!.id, alignment.id)
        return draft to alignment
    }

    private fun publishAndVerify(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        referenceLineId: IntId<ReferenceLine>,
    ): Pair<RowVersion<ReferenceLine>, ReferenceLine> {
        val (draft, draftAlignment) = referenceLineService.getWithAlignment(DRAFT, referenceLineId)
        assertNotNull(draft.draft)
        assertEquals(draft, referenceLineService.getByTrackNumber(DRAFT, trackNumberId))
        assertNull(referenceLineService.getByTrackNumber(OFFICIAL, trackNumberId))

        val publishedVersion = referenceLineService.publish(draft.id as IntId)
        val (published, publishedAlignment) = referenceLineService.getWithAlignment(OFFICIAL, publishedVersion.id)
        val publishedByTrackNumber = referenceLineService.getByTrackNumber(OFFICIAL, trackNumberId)
        assertEquals(published, publishedByTrackNumber)
        assertNull(published.draft)
        assertEquals(draft.id, published.id)
        assertEquals(published.id, publishedVersion.id)
        assertEquals(draft.alignmentVersion, published.alignmentVersion)
        assertEquals(draftAlignment, publishedAlignment)

        return publishedVersion to published
    }

    private fun alignmentExists(id: IntId<LayoutAlignment>): Boolean {
        val sql = "select exists(select 1 from layout.alignment where id = :id) as exists"
        val params = mapOf("id" to id.intValue)
        return jdbc.queryForObject(sql, params) { rs, _ -> rs.getBoolean("exists") }
            ?: throw IllegalStateException("Exists-check failed")
    }

    private fun createAndPublishTrackNumber() = trackNumberService.publish(createTrackNumber()).id

    private fun createTrackNumber() = trackNumberService.insert(
        TrackNumberSaveRequest(
            number = getUnusedTrackNumber(),
            description = FreeText(trackNumberDescription),
            state = LayoutState.IN_USE,
            startAddress = TrackMeter.ZERO,
        )
    )

    private fun address(seed: Int = 0) = TrackMeter(KmNumber(seed), seed*100)
}
