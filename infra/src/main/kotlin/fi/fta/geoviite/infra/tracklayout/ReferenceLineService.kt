package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
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
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        startAddress: TrackMeter,
    ): DaoResponse<ReferenceLine> {
        logger.serviceCall(
            "insertTrackNumberReferenceLine",
            "trackNumberId" to trackNumberId,
            "startAddress" to startAddress,
        )
        return saveDraftInternal(
            ReferenceLine(
                trackNumberId = trackNumberId,
                startAddress = startAddress,
                sourceId = null,
                // TODO: GVT-2398
                contextData = LayoutContextData.newDraft(LayoutBranch.main),
            ),
            emptyAlignment(),
        )
    }

    @Transactional
    fun updateTrackNumberReferenceLine(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        startAddress: TrackMeter,
    ): DaoResponse<ReferenceLine>? {
        logger.serviceCall("updateTrackNumberStart",
            "trackNumberId" to trackNumberId, "startAddress" to startAddress)
        val originalVersion = dao.fetchVersionByTrackNumberId(DRAFT, trackNumberId)
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
        logger.serviceCall("save", "locationTrack" to draft, "alignment" to alignment)
        return saveDraftInternal(draft, alignment)
    }

    private fun saveDraftInternal(draft: ReferenceLine, alignment: LayoutAlignment): DaoResponse<ReferenceLine> {
        require(alignment.segments.all { it.switchId == null }) {
            "Reference line cannot have switches, id=${draft.id} trackNumberId=${draft.trackNumberId}"
        }
        val alignmentVersion =
            // If we're creating a new row or starting a draft, we duplicate the alignment to not edit any original
            if (draft.dataType == TEMP || draft.isOfficial) {
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
        if (line.dataType == TEMP || line.isOfficial) alignmentService.duplicateOrNew(line.alignmentVersion)
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
    override fun deleteDraft(id: IntId<ReferenceLine>): DaoResponse<ReferenceLine> {
        val draft = dao.getOrThrow(DRAFT, id)
        val deletedVersion = super.deleteDraft(id)
        draft.alignmentVersion?.id?.let(alignmentDao::delete)
        return deletedVersion
    }

    @Transactional
    fun deleteDraftByTrackNumberId(trackNumberId: IntId<TrackLayoutTrackNumber>): DaoResponse<ReferenceLine>? {
        logger.serviceCall("deleteDraftByTrackNumberId", "trackNumberId" to trackNumberId)
        val referenceLine = requireNotNull(referenceLineDao.getByTrackNumber(DRAFT, trackNumberId)) {
            "Found Track Number without Reference Line $trackNumberId"
        }
        return if (referenceLine.isDraft) deleteDraft(referenceLine.id as IntId) else null
    }

    fun getByTrackNumber(publicationState: PublicationState, trackNumberId: IntId<TrackLayoutTrackNumber>): ReferenceLine? {
        logger.serviceCall("getByTrackNumber",
            "publicationState" to publicationState, "trackNumberId" to trackNumberId)
        return dao.getByTrackNumber(publicationState, trackNumberId)
    }

    @Transactional(readOnly = true)
    fun getByTrackNumberWithAlignment(
        publicationState: PublicationState,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): Pair<ReferenceLine,LayoutAlignment>? {
        logger.serviceCall("getByTrackNumberWithAlignment",
            "publicationState" to publicationState, "trackNumberId" to trackNumberId)
        return dao.fetchVersionByTrackNumberId(publicationState, trackNumberId)?.let(::getWithAlignmentInternal)
    }

    @Transactional(readOnly = true)
    fun getWithAlignmentOrThrow(publicationState: PublicationState, id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "publicationState" to publicationState, "id" to id)
        return getWithAlignmentInternalOrThrow(publicationState, id)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(publicationState: PublicationState, id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment>? {
        logger.serviceCall("getWithAlignment", "publicationState" to publicationState, "id" to id)
        return getWithAlignmentInternal(publicationState, id)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(version: RowVersion<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "version" to version)
        return getWithAlignmentInternal(version)
    }

    @Transactional(readOnly = true)
    fun getManyWithAlignments(
        publicationState: PublicationState,
        ids: List<IntId<ReferenceLine>>,
    ): List<Pair<ReferenceLine, LayoutAlignment>> {
        logger.serviceCall("getManyWithAlignments", "publicationState" to publicationState, "ids" to ids)
        return dao.getMany(publicationState, ids).let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun listWithAlignments(
        publicationState: PublicationState,
        includeDeleted: Boolean = false,
        boundingBox: BoundingBox? = null,
    ): List<Pair<ReferenceLine, LayoutAlignment>> {
        logger.serviceCall(
            "listWithAlignments",
            "publicationState" to publicationState,
            "includeDeleted" to includeDeleted,
        )
        return dao
            .list(publicationState, includeDeleted)
            .let { list -> filterByBoundingBox(list, boundingBox) }
            .let(::associateWithAlignments)
    }

    private fun getWithAlignmentInternalOrThrow(publicationState: PublicationState, id: IntId<ReferenceLine>) =
        getWithAlignmentInternal(dao.fetchVersionOrThrow(id, publicationState))

    private fun getWithAlignmentInternal(publicationState: PublicationState, id: IntId<ReferenceLine>) =
        dao.fetchVersion(id, publicationState)?.let { v -> getWithAlignmentInternal(v) }

    private fun getWithAlignmentInternal(version: RowVersion<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> =
        referenceLineWithAlignment(dao, alignmentDao, version)

    private fun associateWithAlignments(lines: List<ReferenceLine>): List<Pair<ReferenceLine, LayoutAlignment>> {
        // This is a little convoluted to avoid extra passes of transaction annotation handling in alignmentDao.fetch
        val alignments = alignmentDao.fetchMany(lines.map(ReferenceLine::getAlignmentVersionOrThrow))
        return lines.map { line -> line to alignments.getValue(line.getAlignmentVersionOrThrow()) }
    }

    fun listNonLinked(): List<ReferenceLine> {
        logger.serviceCall("listNonLinked")
        return dao.fetchVersionsNonLinked(DRAFT).map(dao::fetch)
    }

    fun listNear(publicationState: PublicationState, bbox: BoundingBox): List<ReferenceLine> {
        logger.serviceCall("listNear", "publicationState" to publicationState, "bbox" to bbox)
        return dao.fetchVersionsNear(publicationState, bbox).map(dao::fetch)
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
