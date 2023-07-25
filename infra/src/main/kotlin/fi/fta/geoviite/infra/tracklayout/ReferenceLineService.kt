package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.ValidationVersion
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ReferenceLineService(
    dao: ReferenceLineDao,
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val referenceLineDao: ReferenceLineDao,
): DraftableObjectService<ReferenceLine, ReferenceLineDao>(dao) {

    @Transactional
    fun addTrackNumberReferenceLine(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        startAddress: TrackMeter,
    ): DaoResponse<ReferenceLine> {
        logger.serviceCall("insertTrackNumberReferenceLine",
            "trackNumberId" to trackNumberId, "startAddress" to startAddress)
        return saveDraftInternal(ReferenceLine(
            trackNumberId = trackNumberId,
            startAddress = startAddress,
            sourceId = null,
        ), emptyAlignment())
    }

    @Transactional
    fun updateTrackNumberReferenceLine(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        startAddress: TrackMeter,
    ): DaoResponse<ReferenceLine>? {
        logger.serviceCall("updateTrackNumberStart",
            "trackNumberId" to trackNumberId, "startAddress" to startAddress)
        val originalVersion = dao.fetchVersion(DRAFT, trackNumberId)
            ?: throw IllegalStateException("Track number should have a reference line")
        val original = dao.fetch(originalVersion)
        return if (original.startAddress != startAddress) {
            saveDraftInternal(original.copy(
                startAddress = startAddress,
                alignmentVersion = updatedAlignmentVersion(original),
            ))
        } else {
            null
        }
    }

    @Transactional
    override fun saveDraft(draft: ReferenceLine): DaoResponse<ReferenceLine> = super.saveDraft(
        draft.copy(alignmentVersion = updatedAlignmentVersion(draft))
    )

    @Transactional
    fun saveDraft(draft: ReferenceLine, alignment: LayoutAlignment): DaoResponse<ReferenceLine> {
        logger.serviceCall("save", "locationTrack" to draft.id, "alignment" to alignment.id)
        return saveDraftInternal(draft, alignment)
    }

    private fun saveDraftInternal(draft: ReferenceLine, alignment: LayoutAlignment): DaoResponse<ReferenceLine> {
        require(alignment.segments.all { it.switchId == null }) {
            "Reference line cannot have switches, id=${draft.id} trackNumberId=${draft.trackNumberId}"
        }
        val alignmentVersion =
            // If we're creating a new row or starting a draft, we duplicate the alignment to not edit any original
            if (draft.dataType == TEMP || draft.draft == null) {
                alignmentService.saveAsNew(alignment)
            }
            // Ensure that we update the correct one.
            else if (draft.getAlignmentVersionOrThrow().id != alignment.id) {
                alignmentService.save(alignment.copy(id = draft.getAlignmentVersionOrThrow().id, dataType = STORED))
            } else {
                alignmentService.save(alignment)
            }
        return saveDraftInternal(draft.copy(alignmentVersion = alignmentVersion))
    }

    private fun updatedAlignmentVersion(line: ReferenceLine) =
        // If we're creating a new row or starting a draft, we duplicate the alignment to not edit any original
        if (line.dataType == TEMP || line.draft == null) alignmentService.duplicateOrNew(line.alignmentVersion)
        else line.alignmentVersion

    @Transactional
    override fun publish(version: ValidationVersion<ReferenceLine>): DaoResponse<ReferenceLine> {
        logger.serviceCall("publish", "version" to version)
        val officialVersion = dao.fetchOfficialVersion(version.officialId)
        val oldDraft = dao.fetch(version.validatedAssetVersion)
        val oldOfficial = officialVersion?.let(dao::fetch)
        val publishedVersion = publishInternal(VersionPair(officialVersion, version.validatedAssetVersion))
        if (oldOfficial != null && oldDraft.alignmentVersion != oldOfficial.alignmentVersion) {
            // The alignment on the draft overrides the one on official -> delete the original, orphaned alignment
            oldOfficial.alignmentVersion?.id?.let(alignmentDao::delete)
        }
        return publishedVersion
    }

    @Transactional
    override fun deleteUnpublishedDraft(id: IntId<ReferenceLine>): DaoResponse<ReferenceLine> {
        val draft = getInternalOrThrow(DRAFT, id)
        val deletedVersion = super.deleteUnpublishedDraft(id)
        draft.alignmentVersion?.id?.let(alignmentDao::delete)
        return deletedVersion
    }

    override fun createDraft(item: ReferenceLine) = draft(item)

    override fun createPublished(item: ReferenceLine) = published(item)

    @Transactional
    fun deleteDraftOnlyReferenceLine(referenceLine: ReferenceLine): DaoResponse<ReferenceLine> {
        if (referenceLine.getDraftType() != DraftType.NEW_DRAFT)
            throw DeletingFailureException("Trying to delete non-draft Reference Line")
        require(referenceLine.id is IntId) { "Trying to delete or reset reference line not yet saved to database" }

        val response = referenceLine.draft?.draftRowId.let { draftRowId ->
            require(draftRowId is IntId) { "Trying to delete draft Reference Line that isn't yet stored in database" }
            referenceLineDao.deleteDraft(draftRowId)
        }

        referenceLine.alignmentVersion?.id?.let(alignmentDao::delete)
        return response
    }

    fun getByTrackNumber(publishType: PublishType, trackNumberId: IntId<TrackLayoutTrackNumber>): ReferenceLine? {
        logger.serviceCall("getByTrackNumber",
            "publishType" to publishType, "trackNumberId" to trackNumberId)
        return dao.fetchVersion(publishType, trackNumberId)?.let(dao::fetch)
    }

    fun getByTrackNumberAtMoment(publishType: PublishType, trackNumberId: IntId<TrackLayoutTrackNumber>, moment: Instant): ReferenceLine? {
        logger.serviceCall("getByTrackNumberAtMoment",
            "publishType" to publishType, "trackNumberId" to trackNumberId, "moment" to moment)
        return dao.fetchVersionAtMoment(publishType, trackNumberId, moment)?.let(dao::fetch)
    }

    fun getByTrackNumberWithAlignment(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): Pair<ReferenceLine,LayoutAlignment>? {
        logger.serviceCall("getByTrackNumberWithAlignment",
            "publishType" to publishType, "trackNumberId" to trackNumberId)
        return dao.fetchVersion(publishType, trackNumberId)?.let(::getWithAlignmentInternal)
    }

    fun getWithAlignmentOrThrow(publishType: PublishType, id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "publishType" to publishType, "id" to id)
        return getWithAlignmentInternalOrThrow(publishType, id)
    }

    fun getWithAlignment(publishType: PublishType, id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment>? {
        logger.serviceCall("getWithAlignment", "publishType" to publishType, "id" to id)
        return getWithAlignmentInternal(publishType, id)
    }

    fun getWithAlignment(version: RowVersion<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "version" to version)
        return getWithAlignmentInternal(version)
    }

    private fun getWithAlignmentInternalOrThrow(publishType: PublishType, id: IntId<ReferenceLine>) =
        getWithAlignmentInternal(dao.fetchVersionOrThrow(id, publishType))

    private fun getWithAlignmentInternal(publishType: PublishType, id: IntId<ReferenceLine>) =
        dao.fetchVersion(id, publishType)?.let { v -> getWithAlignmentInternal(v) }

    private fun getWithAlignmentInternal(version: RowVersion<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> =
        referenceLineWithAlignment(dao, alignmentDao, version)

    fun listNonLinked(): List<ReferenceLine> {
        logger.serviceCall("listNonLinked")
        return dao.fetchVersionsNonLinked(DRAFT).map(dao::fetch)
    }

    fun listNear(publishType: PublishType, bbox: BoundingBox): List<ReferenceLine> {
        logger.serviceCall("listNear", "publishType" to publishType, "bbox" to bbox)
        return dao.fetchVersionsNear(publishType, bbox).map(dao::fetch)
    }
}

fun referenceLineWithAlignment(
    referenceLineDao: ReferenceLineDao,
    alignmentDao: LayoutAlignmentDao,
    rowVersion: RowVersion<ReferenceLine>,
) = referenceLineDao.fetch(rowVersion).let { track ->
    track to alignmentDao.fetch(track.getAlignmentVersionOrThrow())
}
