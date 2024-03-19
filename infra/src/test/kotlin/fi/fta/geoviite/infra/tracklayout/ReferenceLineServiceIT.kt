package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.util.FreeText
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
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
) : DBTestBase() {

    @Test
    fun creatingAndDeletingUnpublishedReferenceLineWithAlignmentWorks() {
        val trackNumberId = createTrackNumber() // automatically creates first version of reference line
        val (savedLine, savedAlignment) = requireNotNull(
            referenceLineService.getByTrackNumberWithAlignment(DRAFT, trackNumberId)
        ) { "Reference line was not automatically created" }
        assertTrue(alignmentExists(savedLine.alignmentVersion!!.id))
        assertEquals(savedLine.alignmentVersion?.id, savedAlignment.id as IntId)
        assertThrows<DataIntegrityViolationException> { trackNumberService.deleteDraft(trackNumberId) }
        referenceLineService.deleteDraft(savedLine.id as IntId)
        assertFalse(alignmentExists(savedLine.alignmentVersion!!.id))
        assertDoesNotThrow { trackNumberService.deleteDraft(trackNumberId) }
    }

    @Test
    fun deletingOfficialAlignmentThrowsException() {
        val trackNumber = createAndPublishTrackNumber()
        val referenceLineId = referenceLineService.getByTrackNumber(DRAFT, trackNumber)?.id as IntId
        publish(referenceLineId)
        val (line, _) = referenceLineService.getWithAlignmentOrThrow(OFFICIAL, referenceLineId)
        assertFalse(line.isDraft)
        assertThrows<DeletingFailureException> { referenceLineService.deleteDraft(referenceLineId) }
    }

    @Test
    fun referenceLineAddAndUpdateWorks() {
        val trackNumberId = createTrackNumber() // First version is created automatically

        val referenceLine = referenceLineService.getByTrackNumber(DRAFT, trackNumberId)
        assertNotNull(referenceLine)
        assertTrue(referenceLine?.isDraft ?: false)
        assertEquals(0.0, referenceLine?.length)
        assertEquals(trackNumberId, referenceLine?.trackNumberId)
        val changeTimeAfterInsert = referenceLineService.getChangeTime()
        val referenceLineId = referenceLine?.id as IntId

        val address = address(2)
        val updateResponse = referenceLineService.updateTrackNumberReferenceLine(trackNumberId, address)
        assertEquals(referenceLineId, updateResponse?.id)
        val updatedLine = referenceLineService.get(DRAFT, referenceLineId)!!
        assertEquals(address, updatedLine.startAddress)
        assertEquals(trackNumberId, updatedLine.trackNumberId)
        assertEquals(referenceLine.alignmentVersion, updatedLine.alignmentVersion)
        val changeTimeAfterUpdate = referenceLineService.getChangeTime()

        val changeInfo = referenceLineService.getLayoutAssetChangeInfo(referenceLineId, DRAFT)
        assertEquals(changeTimeAfterInsert, changeInfo?.created)
        assertEquals(changeTimeAfterUpdate, changeInfo?.changed)
    }

    @Test
    fun updatingThroughTrackNumberCreatesDraft() {
        val trackNumberId = createAndPublishTrackNumber() // First version is created automatically

        val referenceLineId = referenceLineService.getByTrackNumber(DRAFT, trackNumberId)!!.id as IntId
        val (publishedVersion, published) = publishAndVerify(trackNumberId, referenceLineId)

        val editResponse = referenceLineService.updateTrackNumberReferenceLine(trackNumberId, TrackMeter(3, 5))
        assertEquals(publishedVersion.id, editResponse?.id)
        assertNotEquals(publishedVersion.rowVersion.id, editResponse?.rowVersion?.id)

        val editedDraft = getAndVerifyDraft(publishedVersion.id)
        assertEquals(TrackMeter(3, 5), editedDraft.startAddress)
        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val editResponse2 = referenceLineService.updateTrackNumberReferenceLine(trackNumberId, TrackMeter(8, 9))
        assertEquals(publishedVersion.id, editResponse2?.id)
        assertNotEquals(publishedVersion.rowVersion.id, editResponse2?.rowVersion?.id)

        val editedDraft2 = referenceLineService.get(DRAFT, publishedVersion.id)!!
        assertEquals(TrackMeter(8, 9), editedDraft2.startAddress)
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
    }

    @Test
    fun savingCreatesDraft() {
        val trackNumberId = createAndPublishTrackNumber() // First version is created automatically

        val referenceLineId = referenceLineService.getByTrackNumber(DRAFT, trackNumberId)!!.id as IntId
        val (publishedVersion, published) = publishAndVerify(trackNumberId, referenceLineId)

        val editedVersion = referenceLineService.saveDraft(published.copy(startAddress = TrackMeter(1, 1)))
        assertEquals(publishedVersion.id, editedVersion.id)
        assertNotEquals(publishedVersion.rowVersion.id, editedVersion.rowVersion.id)

        val editedDraft = getAndVerifyDraft(publishedVersion.id)
        assertEquals(TrackMeter(1, 1), editedDraft.startAddress)
        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val editedVersion2 = referenceLineService.saveDraft(editedDraft.copy(startAddress = TrackMeter(2, 2)))
        assertEquals(publishedVersion.id, editedVersion2.id)
        assertNotEquals(publishedVersion.rowVersion.id, editedVersion2.rowVersion.id)

        val editedDraft2 = getAndVerifyDraft(publishedVersion.id)
        assertEquals(TrackMeter(2, 2), editedDraft2.startAddress)
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
    }

    @Test
    fun savingWithAlignmentCreatesDraft() {
        val trackNumberId = createAndPublishTrackNumber() // First version is created automatically

        val referenceLineId = referenceLineService.getByTrackNumber(DRAFT, trackNumberId)!!.id as IntId
        val (publishedVersion, published) = publishAndVerify(trackNumberId, referenceLineId)

        val alignmentTmp = alignment(segment(2, 10.0, 20.0, 10.0, 20.0))
        val editedVersion = referenceLineService.saveDraft(published, alignmentTmp)
        assertEquals(publishedVersion.id, editedVersion.id)
        assertNotEquals(publishedVersion.rowVersion.id, editedVersion.rowVersion.id)

        val (editedDraft, editedAlignment) = getAndVerifyDraftWithAlignment(publishedVersion.id)
        assertEquals(
            alignmentTmp.segments.flatMap(LayoutSegment::alignmentPoints),
            editedAlignment.segments.flatMap(LayoutSegment::alignmentPoints),
        )

        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val alignmentTmp2 = alignment(segment(4, 10.0, 20.0, 10.0, 20.0))
        val editedVersion2 = referenceLineService.saveDraft(editedDraft, alignmentTmp2)
        assertEquals(publishedVersion.id, editedVersion2.id)
        assertNotEquals(publishedVersion.rowVersion.id, editedVersion2.rowVersion.id)

        val (editedDraft2, editedAlignment2) = getAndVerifyDraftWithAlignment(publishedVersion.id)
        assertEquals(
            alignmentTmp2.segments.flatMap(LayoutSegment::alignmentPoints),
            editedAlignment2.segments.flatMap(LayoutSegment::alignmentPoints),
        )
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        assertNotEquals(editedDraft.alignmentVersion!!.version, editedDraft2.alignmentVersion!!.version)
    }

    @Test
    fun `should throw exception when there are switches linked to reference line`() {
        val trackNumberId = getUnusedTrackNumberId()

        val (referenceLine, alignment) = referenceLineAndAlignment(
            trackNumberId = trackNumberId,
            segments = listOf(
                segment(
                    Point(0.0, 0.0), Point(1.0, 1.0), switchId = IntId(100), startJointNumber = JointNumber(1)
                ),
            ),
            draft = false,
        )

        assertThrows<IllegalArgumentException> {
            referenceLineService.saveDraft(referenceLine, alignment)
        }
    }

    private fun getAndVerifyDraft(id: IntId<ReferenceLine>): ReferenceLine {
        val draft = referenceLineService.get(DRAFT, id)!!
        assertEquals(id, draft.id)
        assertTrue(draft.isDraft)
        return draft
    }

    private fun getAndVerifyDraftWithAlignment(id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> {
        val (draft, alignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, id)
        assertEquals(id, draft.id)
        assertTrue(draft.isDraft)
        assertEquals(draft.alignmentVersion!!.id, alignment.id)
        return draft to alignment
    }

    private fun publishAndVerify(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        referenceLineId: IntId<ReferenceLine>,
    ): Pair<DaoResponse<ReferenceLine>, ReferenceLine> {
        val (draft, draftAlignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, referenceLineId)
        assertTrue(draft.isDraft)
        assertEquals(draft, referenceLineService.getByTrackNumber(DRAFT, trackNumberId))
        assertNull(referenceLineService.getByTrackNumber(OFFICIAL, trackNumberId))

        val publishedVersion = publish(draft.id as IntId)
        val (published, publishedAlignment) = referenceLineService.getWithAlignmentOrThrow(
            OFFICIAL, publishedVersion.id
        )
        val publishedByTrackNumber = referenceLineService.getByTrackNumber(OFFICIAL, trackNumberId)
        assertEquals(published, publishedByTrackNumber)
        assertFalse(published.isDraft)
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

    private fun createAndPublishTrackNumber() = createTrackNumber().let { id ->
        val version = trackNumberDao.fetchVersionOrThrow(id, DRAFT)
        trackNumberService.publish(ValidationVersion(id, version)).id
    }

    private fun createTrackNumber() = trackNumberService.insert(
        TrackNumberSaveRequest(
            number = getUnusedTrackNumber(),
            description = FreeText(trackNumberDescription),
            state = LayoutState.IN_USE,
            startAddress = TrackMeter.ZERO,
        )
    )

    private fun address(seed: Int = 0) = TrackMeter(KmNumber(seed), seed * 100)

    private fun publish(id: IntId<ReferenceLine>) = referenceLineDao
        .fetchPublicationVersions(listOf(id))
        .first()
        .let { version -> referenceLineService.publish(version) }
}
