package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.ValidationVersion
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReferenceLineService(
    dao: ReferenceLineDao,
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val referenceLineDao: ReferenceLineDao,
): LayoutAssetService<ReferenceLine, ReferenceLineDao>(dao) {

    @Transactional
    fun addTrackNumberReferenceLine(
        branch: LayoutBranch,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        startAddress: TrackMeter,
    ): DaoResponse<ReferenceLine> {
        logger.serviceCall("insertTrackNumberReferenceLine",
            "branch" to branch,
            "trackNumberId" to trackNumberId,
            "startAddress" to startAddress,
        )
        return saveDraftInternal(
            branch,
            ReferenceLine(
                trackNumberId = trackNumberId,
                startAddress = startAddress,
                sourceId = null,
                contextData = LayoutContextData.newDraft(branch),
            ),
            emptyAlignment(),
        )
    }

    @Transactional
    fun updateTrackNumberReferenceLine(
        branch: LayoutBranch,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        startAddress: TrackMeter,
    ): DaoResponse<ReferenceLine>? {
        logger.serviceCall("updateTrackNumberStart",
            "branch" to branch,
            "trackNumberId" to trackNumberId,
             "startAddress" to startAddress,
        )

        val originalVersion = dao.fetchVersionByTrackNumberId(branch.draft, trackNumberId)
            ?: throw IllegalStateException("Track number should have a reference line")
        val original = dao.fetch(originalVersion)
        return if (original.startAddress != startAddress) {
            saveDraftInternal(
                branch,
                original.copy(
                    startAddress = startAddress,
                    alignmentVersion = updatedAlignmentVersion(original),
                )
            )
        } else {
            null
        }
    }

    @Transactional
    override fun saveDraft(branch: LayoutBranch, draftAsset: ReferenceLine): DaoResponse<ReferenceLine> =
        super.saveDraft(branch, draftAsset.copy(alignmentVersion = updatedAlignmentVersion(draftAsset)))

    @Transactional
    fun saveDraft(
        branch: LayoutBranch,
        draftAsset: ReferenceLine,
        alignment: LayoutAlignment,
    ): DaoResponse<ReferenceLine> {
        logger.serviceCall("save", "branch" to branch, "draftAsset" to draftAsset, "alignment" to alignment)
        return saveDraftInternal(branch, draftAsset, alignment)
    }

    private fun saveDraftInternal(
        branch: LayoutBranch,
        draftAsset: ReferenceLine,
        alignment: LayoutAlignment,
    ): DaoResponse<ReferenceLine> {
        require(alignment.segments.all { it.switchId == null }) {
            "Reference lines cannot have switches: id=${draftAsset.id} referenceLine=$draftAsset"
        }
        val alignmentVersion =
            // If we're creating a new row or starting a draft, we duplicate the alignment to not edit any original
            if (draftAsset.dataType == TEMP || draftAsset.isOfficial) {
                alignmentService.saveAsNew(alignment)
            }
            // Ensure that we update the correct one.
            else if (draftAsset.getAlignmentVersionOrThrow().id != alignment.id) {
                alignmentService.save(
                    alignment.copy(
                        id = draftAsset.getAlignmentVersionOrThrow().id,
                        dataType = STORED,
                    )
                )
            } else {
                alignmentService.save(alignment)
            }
        return saveDraftInternal(branch, draftAsset.copy(alignmentVersion = alignmentVersion))
    }

    private fun updatedAlignmentVersion(line: ReferenceLine) =
        // If we're creating a new row or starting a draft, we duplicate the alignment to not edit any original
        if (line.dataType == TEMP || line.isOfficial) alignmentService.duplicateOrNew(line.alignmentVersion)
        else line.alignmentVersion

    @Transactional
    override fun publish(branch: LayoutBranch, version: ValidationVersion<ReferenceLine>): DaoResponse<ReferenceLine> {
        logger.serviceCall("publish", "branch" to branch, "version" to version)
        val officialVersion = dao.fetchOfficialRowVersionForPublishingInBranch(branch, version.validatedAssetVersion)
        val oldDraft = dao.fetch(version.validatedAssetVersion)
        val oldOfficial = officialVersion?.let(dao::fetch)
        val publishedVersion = publishInternal(branch, VersionPair(officialVersion, version.validatedAssetVersion))
        if (oldOfficial != null && oldDraft.alignmentVersion != oldOfficial.alignmentVersion) {
            // The alignment on the draft overrides the one on official -> delete the original, orphaned alignment
            oldOfficial.alignmentVersion?.id?.let(alignmentDao::delete)
        }
        return publishedVersion
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<ReferenceLine>): DaoResponse<ReferenceLine> {
        val draft = dao.getOrThrow(branch.draft, id)
        val deletedVersion = super.deleteDraft(branch, id)
        draft.alignmentVersion?.id?.let(alignmentDao::delete)
        return deletedVersion
    }

    @Transactional
    fun deleteDraftByTrackNumberId(
        branch: LayoutBranch,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): DaoResponse<ReferenceLine>? {
        logger.serviceCall("deleteDraftByTrackNumberId", "branch" to branch, "trackNumberId" to trackNumberId)
        val referenceLine = requireNotNull(referenceLineDao.getByTrackNumber(branch.draft, trackNumberId)) {
            "Found Track Number without Reference Line $trackNumberId"
        }
        return if (referenceLine.isDraft) deleteDraft(branch, referenceLine.id as IntId) else null
    }

    fun getByTrackNumber(layoutContext: LayoutContext, trackNumberId: IntId<TrackLayoutTrackNumber>): ReferenceLine? {
        logger.serviceCall(
            "getByTrackNumber",
             "layoutContext" to layoutContext,
            "trackNumberId" to trackNumberId,
        )
        return dao.getByTrackNumber(layoutContext, trackNumberId)
    }

    @Transactional(readOnly = true)
    fun getByTrackNumberWithAlignment(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): Pair<ReferenceLine,LayoutAlignment>? {
        logger.serviceCall(
            "getByTrackNumberWithAlignment",
            "layoutContext" to layoutContext,
            "trackNumberId" to trackNumberId,
        )
        return dao.fetchVersionByTrackNumberId(layoutContext, trackNumberId)?.let(::getWithAlignmentInternal)
    }

    @Transactional(readOnly = true)
    fun getWithAlignmentOrThrow(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): Pair<ReferenceLine, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "layoutContext" to layoutContext, "id" to id)
        return getWithAlignmentInternalOrThrow(layoutContext, id)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(layoutContext: LayoutContext, id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment>? {
        logger.serviceCall("getWithAlignment", "layoutContext" to layoutContext, "id" to id)
        return getWithAlignmentInternal(layoutContext, id)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(version: RowVersion<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "version" to version)
        return getWithAlignmentInternal(version)
    }

    @Transactional(readOnly = true)
    fun getManyWithAlignments(
        layoutContext: LayoutContext,
        ids: List<IntId<ReferenceLine>>,
    ): List<Pair<ReferenceLine, LayoutAlignment>> {
        logger.serviceCall("getManyWithAlignments", "layoutContext" to layoutContext, "ids" to ids)
        return dao.getMany(layoutContext, ids).let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun listWithAlignments(
        layoutContext: LayoutContext,
        includeDeleted: Boolean = false,
        boundingBox: BoundingBox? = null,
    ): List<Pair<ReferenceLine, LayoutAlignment>> {
        logger.serviceCall(
            "listWithAlignments",
            "layoutContext" to layoutContext,
            "includeDeleted" to includeDeleted,
        )
        return dao
            .list(layoutContext, includeDeleted)
            .let { list -> filterByBoundingBox(list, boundingBox) }
            .let(::associateWithAlignments)
    }

    private fun getWithAlignmentInternalOrThrow(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): Pair<ReferenceLine, LayoutAlignment> {
        return getWithAlignmentInternal(dao.fetchVersionOrThrow(layoutContext, id))
    }

    private fun getWithAlignmentInternal(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): Pair<ReferenceLine, LayoutAlignment>? {
        return dao.fetchVersion(layoutContext, id)?.let { v -> getWithAlignmentInternal(v) }
    }

    private fun getWithAlignmentInternal(version: RowVersion<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> =
        referenceLineWithAlignment(dao, alignmentDao, version)

    private fun associateWithAlignments(lines: List<ReferenceLine>): List<Pair<ReferenceLine, LayoutAlignment>> {
        // This is a little convoluted to avoid extra passes of transaction annotation handling in alignmentDao.fetch
        val alignments = alignmentDao.fetchMany(lines.map(ReferenceLine::getAlignmentVersionOrThrow))
        return lines.map { line -> line to alignments.getValue(line.getAlignmentVersionOrThrow()) }
    }

    fun listNonLinked(branch: LayoutBranch): List<ReferenceLine> {
        logger.serviceCall("listNonLinked", "branch" to branch)
        return dao.fetchVersionsNonLinked(branch.draft).map(dao::fetch)
    }

    fun listNear(layoutContext: LayoutContext, bbox: BoundingBox): List<ReferenceLine> {
        logger.serviceCall("listNear", "layoutContext" to layoutContext, "bbox" to bbox)
        return dao.fetchVersionsNear(layoutContext, bbox).map(dao::fetch)
    }
}

fun referenceLineWithAlignment(
    referenceLineDao: ReferenceLineDao,
    alignmentDao: LayoutAlignmentDao,
    rowVersion: RowVersion<ReferenceLine>,
) = referenceLineDao.fetch(rowVersion).let { track ->
    track to alignmentDao.fetch(track.getAlignmentVersionOrThrow())
}

fun filterByBoundingBox(list: List<ReferenceLine>, boundingBox: BoundingBox?): List<ReferenceLine> =
    if (boundingBox != null) list.filter { t -> boundingBox.intersects(t.boundingBox) }
    else list
