package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class ReferenceLineServiceIT
@Autowired
constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val referenceLineService: ReferenceLineService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
) : DBTestBase() {

    @Test
    fun creatingAndDeletingUnpublishedReferenceLineWithAlignmentWorks() {
        val trackNumberId =
            createTrackNumber() // automatically creates first version of reference line
        val (savedLine, savedAlignment) =
            requireNotNull(
                referenceLineService.getByTrackNumberWithAlignment(
                    MainLayoutContext.draft, trackNumberId)) {
                    "Reference line was not automatically created"
                }
        assertTrue(alignmentExists(savedLine.alignmentVersion!!.id))
        assertEquals(savedLine.alignmentVersion?.id, savedAlignment.id as IntId)
        assertThrows<DataIntegrityViolationException> {
            trackNumberService.deleteDraft(LayoutBranch.main, trackNumberId)
        }
        referenceLineService.deleteDraft(LayoutBranch.main, savedLine.id as IntId)
        assertFalse(alignmentExists(savedLine.alignmentVersion!!.id))
        assertDoesNotThrow { trackNumberService.deleteDraft(LayoutBranch.main, trackNumberId) }
    }

    @Test
    fun deletingOfficialAlignmentThrowsException() {
        val trackNumber = createAndPublishTrackNumber()
        val referenceLineId =
            referenceLineService.getByTrackNumber(MainLayoutContext.draft, trackNumber)?.id as IntId
        publish(referenceLineId)
        val (line, _) =
            referenceLineService.getWithAlignmentOrThrow(
                MainLayoutContext.official, referenceLineId)
        assertFalse(line.isDraft)
        assertThrows<DeletingFailureException> {
            referenceLineService.deleteDraft(LayoutBranch.main, referenceLineId)
        }
    }

    @Test
    fun referenceLineAddAndUpdateWorks() {
        val trackNumberId = createTrackNumber() // First version is created automatically

        val referenceLine =
            referenceLineService.getByTrackNumber(MainLayoutContext.draft, trackNumberId)
        assertNotNull(referenceLine)
        assertTrue(referenceLine?.isDraft ?: false)
        assertEquals(0.0, referenceLine?.length)
        assertEquals(trackNumberId, referenceLine?.trackNumberId)
        val changeTimeAfterInsert = referenceLineService.getChangeTime()
        val referenceLineId = referenceLine?.id as IntId

        val address = address(2)
        val updateResponse =
            referenceLineService.updateTrackNumberReferenceLine(
                LayoutBranch.main, trackNumberId, address)
        assertEquals(referenceLineId, updateResponse?.id)
        val updatedLine = referenceLineService.get(MainLayoutContext.draft, referenceLineId)!!
        assertEquals(address, updatedLine.startAddress)
        assertEquals(trackNumberId, updatedLine.trackNumberId)
        assertEquals(referenceLine.alignmentVersion, updatedLine.alignmentVersion)
        val changeTimeAfterUpdate = referenceLineService.getChangeTime()

        val changeInfo =
            referenceLineService.getLayoutAssetChangeInfo(MainLayoutContext.draft, referenceLineId)
        assertEquals(changeTimeAfterInsert, changeInfo?.created)
        assertEquals(changeTimeAfterUpdate, changeInfo?.changed)
    }

    @Test
    fun updatingThroughTrackNumberCreatesDraft() {
        val trackNumberId = createAndPublishTrackNumber() // First version is created automatically

        val referenceLineId =
            referenceLineService.getByTrackNumber(MainLayoutContext.draft, trackNumberId)!!.id
                as IntId
        val (publishResponse, published) = publishAndVerify(trackNumberId, referenceLineId)

        val editResponse =
            referenceLineService.updateTrackNumberReferenceLine(
                LayoutBranch.main, trackNumberId, TrackMeter(3, 5))
        assertEquals(publishResponse.id, editResponse?.id)
        assertNotEquals(publishResponse.rowVersion.rowId, editResponse?.rowVersion?.rowId)

        val editedDraft = getAndVerifyDraft(publishResponse.id)
        assertEquals(TrackMeter(3, 5), editedDraft.startAddress)
        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val editResponse2 =
            referenceLineService.updateTrackNumberReferenceLine(
                LayoutBranch.main, trackNumberId, TrackMeter(8, 9))
        assertEquals(publishResponse.id, editResponse2?.id)
        assertNotEquals(publishResponse.rowVersion.rowId, editResponse2?.rowVersion?.rowId)

        val editedDraft2 = referenceLineService.get(MainLayoutContext.draft, publishResponse.id)!!
        assertEquals(TrackMeter(8, 9), editedDraft2.startAddress)
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
    }

    @Test
    fun savingCreatesDraft() {
        val trackNumberId = createAndPublishTrackNumber() // First version is created automatically

        val referenceLineId =
            referenceLineService.getByTrackNumber(MainLayoutContext.draft, trackNumberId)!!.id
                as IntId
        val (publishResponse, published) = publishAndVerify(trackNumberId, referenceLineId)

        val editedVersion =
            referenceLineService.saveDraft(
                LayoutBranch.main,
                published.copy(startAddress = TrackMeter(1, 1)),
            )
        assertEquals(publishResponse.id, editedVersion.id)
        assertNotEquals(publishResponse.rowVersion.rowId, editedVersion.rowVersion.rowId)

        val editedDraft = getAndVerifyDraft(publishResponse.id)
        assertEquals(TrackMeter(1, 1), editedDraft.startAddress)
        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val editedVersion2 =
            referenceLineService.saveDraft(
                LayoutBranch.main,
                editedDraft.copy(startAddress = TrackMeter(2, 2)),
            )
        assertEquals(publishResponse.id, editedVersion2.id)
        assertNotEquals(publishResponse.rowVersion.rowId, editedVersion2.rowVersion.rowId)

        val editedDraft2 = getAndVerifyDraft(publishResponse.id)
        assertEquals(TrackMeter(2, 2), editedDraft2.startAddress)
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
    }

    @Test
    fun savingWithAlignmentCreatesDraft() {
        val trackNumberId = createAndPublishTrackNumber() // First version is created automatically

        val referenceLineId =
            referenceLineService.getByTrackNumber(MainLayoutContext.draft, trackNumberId)!!.id
                as IntId
        val (publishResponse, published) = publishAndVerify(trackNumberId, referenceLineId)

        val alignmentTmp = alignment(segment(2, 10.0, 20.0, 10.0, 20.0))
        val editedVersion =
            referenceLineService.saveDraft(LayoutBranch.main, published, alignmentTmp)
        assertEquals(publishResponse.id, editedVersion.id)
        assertNotEquals(publishResponse.rowVersion.rowId, editedVersion.rowVersion.rowId)

        val (editedDraft, editedAlignment) = getAndVerifyDraftWithAlignment(publishResponse.id)
        assertEquals(
            alignmentTmp.segments.flatMap(LayoutSegment::alignmentPoints),
            editedAlignment.segments.flatMap(LayoutSegment::alignmentPoints),
        )

        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val alignmentTmp2 = alignment(segment(4, 10.0, 20.0, 10.0, 20.0))
        val editedVersion2 =
            referenceLineService.saveDraft(LayoutBranch.main, editedDraft, alignmentTmp2)
        assertEquals(publishResponse.id, editedVersion2.id)
        assertNotEquals(publishResponse.rowVersion.rowId, editedVersion2.rowVersion.rowId)

        val (editedDraft2, editedAlignment2) = getAndVerifyDraftWithAlignment(publishResponse.id)
        assertEquals(
            alignmentTmp2.segments.flatMap(LayoutSegment::alignmentPoints),
            editedAlignment2.segments.flatMap(LayoutSegment::alignmentPoints),
        )
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        assertNotEquals(
            editedDraft.alignmentVersion!!.version, editedDraft2.alignmentVersion!!.version)
    }

    @Test
    fun `should throw exception when there are switches linked to reference line`() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id

        val (referenceLine, alignment) =
            referenceLineAndAlignment(
                trackNumberId = trackNumberId,
                segments =
                    listOf(
                        segment(
                            Point(0.0, 0.0),
                            Point(1.0, 1.0),
                            switchId = IntId(100),
                            startJointNumber = JointNumber(1)),
                    ),
                draft = false,
            )

        assertThrows<IllegalArgumentException> {
            referenceLineService.saveDraft(LayoutBranch.main, referenceLine, alignment)
        }
    }

    private fun getAndVerifyDraft(id: IntId<ReferenceLine>): ReferenceLine {
        val draft = referenceLineService.get(MainLayoutContext.draft, id)!!
        assertEquals(id, draft.id)
        assertTrue(draft.isDraft)
        return draft
    }

    private fun getAndVerifyDraftWithAlignment(
        id: IntId<ReferenceLine>
    ): Pair<ReferenceLine, LayoutAlignment> {
        val (draft, alignment) =
            referenceLineService.getWithAlignmentOrThrow(MainLayoutContext.draft, id)
        assertEquals(id, draft.id)
        assertTrue(draft.isDraft)
        assertEquals(draft.alignmentVersion!!.id, alignment.id)
        return draft to alignment
    }

    private fun publishAndVerify(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        referenceLineId: IntId<ReferenceLine>,
    ): Pair<LayoutDaoResponse<ReferenceLine>, ReferenceLine> {
        val (draft, draftAlignment) =
            referenceLineService.getWithAlignmentOrThrow(MainLayoutContext.draft, referenceLineId)
        assertTrue(draft.isDraft)
        assertEquals(
            draft, referenceLineService.getByTrackNumber(MainLayoutContext.draft, trackNumberId))
        assertNull(referenceLineService.getByTrackNumber(MainLayoutContext.official, trackNumberId))

        val publishedVersion = publish(draft.id as IntId)
        val (published, publishedAlignment) =
            referenceLineService.getWithAlignmentOrThrow(
                MainLayoutContext.official, publishedVersion.id)
        val publishedByTrackNumber =
            referenceLineService.getByTrackNumber(MainLayoutContext.official, trackNumberId)
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

    private fun createAndPublishTrackNumber() =
        createTrackNumber().let { id ->
            val version = trackNumberDao.fetchVersionOrThrow(MainLayoutContext.draft, id)
            trackNumberService.publish(LayoutBranch.main, ValidationVersion(id, version)).id
        }

    private fun createTrackNumber() =
        trackNumberService.insert(
            LayoutBranch.main,
            TrackNumberSaveRequest(
                number = testDBService.getUnusedTrackNumber(),
                description = FreeText(trackNumberDescription),
                state = LayoutState.IN_USE,
                startAddress = TrackMeter.ZERO,
            ))

    private fun address(seed: Int = 0) = TrackMeter(KmNumber(seed), seed * 100)

    private fun publish(id: IntId<ReferenceLine>) =
        referenceLineDao.fetchPublicationVersions(LayoutBranch.main, listOf(id)).first().let {
            version ->
            referenceLineService.publish(LayoutBranch.main, version)
        }
}
