package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.PublicationResultVersions
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class ReferenceLineService(
    dao: ReferenceLineDao,
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val referenceLineDao: ReferenceLineDao,
    private val geocodingService: GeocodingService,
) : LayoutAssetService<ReferenceLine, ReferenceLineDao>(dao) {

    @Transactional
    fun addTrackNumberReferenceLine(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
        startAddress: TrackMeter,
    ): LayoutRowVersion<ReferenceLine> {
        return saveDraftInternal(
            branch,
            ReferenceLine(
                trackNumberId = trackNumberId,
                startAddress = startAddress,
                sourceId = null,
                contextData = LayoutContextData.newDraft(branch, dao.createId()),
            ),
            emptyAlignment(),
        )
    }

    @Transactional
    fun updateTrackNumberReferenceLine(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
        startAddress: TrackMeter,
    ): LayoutRowVersion<ReferenceLine>? {
        val originalVersion =
            dao.fetchVersionByTrackNumberId(branch.draft, trackNumberId)
                ?: throw IllegalStateException("Track number should have a reference line")
        val original = dao.fetch(originalVersion)
        return if (original.startAddress != startAddress) {
            saveDraftInternal(
                branch,
                original.copy(startAddress = startAddress, alignmentVersion = updatedAlignmentVersion(original)),
            )
        } else {
            null
        }
    }

    @Transactional
    override fun saveDraft(branch: LayoutBranch, draftAsset: ReferenceLine): LayoutRowVersion<ReferenceLine> =
        super.saveDraft(branch, draftAsset.copy(alignmentVersion = updatedAlignmentVersion(draftAsset)))

    @Transactional
    fun saveDraft(
        branch: LayoutBranch,
        draftAsset: ReferenceLine,
        alignment: LayoutAlignment,
    ): LayoutRowVersion<ReferenceLine> {
        return saveDraftInternal(branch, draftAsset, alignment)
    }

    private fun saveDraftInternal(
        branch: LayoutBranch,
        draftAsset: ReferenceLine,
        alignment: LayoutAlignment,
    ): LayoutRowVersion<ReferenceLine> {
        require(alignment.segments.all { it.switchId == null }) {
            "Reference lines cannot have switches: id=${draftAsset.id} referenceLine=$draftAsset"
        }
        val alignmentVersion =
            // If we're creating a new row or starting a draft, we duplicate the alignment to not
            // edit any original
            if (draftAsset.dataType == TEMP || draftAsset.isOfficial) {
                alignmentService.saveAsNew(alignment)
            }
            // Ensure that we update the correct one.
            else if (draftAsset.getAlignmentVersionOrThrow().id != alignment.id) {
                alignmentService.save(
                    alignment.copy(id = draftAsset.getAlignmentVersionOrThrow().id, dataType = STORED)
                )
            } else {
                alignmentService.save(alignment)
            }
        return saveDraftInternal(branch, draftAsset.copy(alignmentVersion = alignmentVersion))
    }

    private fun updatedAlignmentVersion(line: ReferenceLine) =
        // If we're creating a new row or starting a draft, we duplicate the alignment to not edit
        // any original
        if (line.dataType == TEMP || line.isOfficial) alignmentService.duplicateOrNew(line.alignmentVersion)
        else line.alignmentVersion

    @Transactional
    override fun publish(
        branch: LayoutBranch,
        version: LayoutRowVersion<ReferenceLine>,
    ): PublicationResultVersions<ReferenceLine> {
        val publishedVersion = publishInternal(branch, version)
        // Some of the versions may get deleted in publication -> delete any alignments they left
        // behind
        alignmentDao.deleteOrphanedAlignments()
        return publishedVersion
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<ReferenceLine>): LayoutRowVersion<ReferenceLine> {
        val deletedVersion = super.deleteDraft(branch, id)
        dao.fetch(deletedVersion).alignmentVersion?.id?.let(alignmentDao::delete)
        return deletedVersion
    }

    @Transactional
    fun deleteDraftByTrackNumberId(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): LayoutRowVersion<ReferenceLine>? {
        val referenceLine =
            requireNotNull(referenceLineDao.getByTrackNumber(branch.draft, trackNumberId)) {
                "Found Track Number without Reference Line $trackNumberId"
            }
        return if (referenceLine.isDraft) deleteDraft(branch, referenceLine.id as IntId) else null
    }

    fun getByTrackNumber(layoutContext: LayoutContext, trackNumberId: IntId<LayoutTrackNumber>): ReferenceLine? {
        return dao.getByTrackNumber(layoutContext, trackNumberId)
    }

    fun getByTrackNumberOrThrow(layoutContext: LayoutContext, trackNumberId: IntId<LayoutTrackNumber>): ReferenceLine {
        return getByTrackNumber(layoutContext, trackNumberId)
            ?: throw NoSuchEntityException("No ReferenceLine for TrackNumber", trackNumberId)
    }

    @Transactional(readOnly = true)
    fun getByTrackNumberWithAlignment(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): Pair<ReferenceLine, LayoutAlignment>? {
        return dao.fetchVersionByTrackNumberId(layoutContext, trackNumberId)?.let(::getWithAlignmentInternal)
    }

    @Transactional(readOnly = true)
    fun getWithAlignmentOrThrow(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): Pair<ReferenceLine, LayoutAlignment> {
        return getWithAlignmentInternalOrThrow(layoutContext, id)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): Pair<ReferenceLine, LayoutAlignment>? {
        return getWithAlignmentInternal(layoutContext, id)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(version: LayoutRowVersion<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> {
        return getWithAlignmentInternal(version)
    }

    @Transactional(readOnly = true)
    fun getManyWithAlignments(
        layoutContext: LayoutContext,
        ids: List<IntId<ReferenceLine>>,
    ): List<Pair<ReferenceLine, LayoutAlignment>> {
        return dao.getMany(layoutContext, ids).let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun listWithAlignments(
        layoutContext: LayoutContext,
        includeDeleted: Boolean = false,
        boundingBox: BoundingBox? = null,
    ): List<Pair<ReferenceLine, LayoutAlignment>> {
        return (if (boundingBox == null) {
                dao.list(layoutContext, includeDeleted)
            } else {
                dao.fetchVersionsNear(layoutContext, boundingBox, includeDeleted).map(dao::fetch)
            })
            .let { list -> filterByBoundingBox(list, boundingBox) }
            .let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun getStartAndEnd(context: LayoutContext, id: IntId<ReferenceLine>): AlignmentStartAndEnd<ReferenceLine>? {
        return getWithAlignment(context, id)?.let { (referenceLine, alignment) ->
            AlignmentStartAndEnd.of(
                referenceLine.id as IntId,
                alignment,
                geocodingService.getGeocodingContext(context, referenceLine.trackNumberId),
            )
        }
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

    private fun getWithAlignmentInternal(
        version: LayoutRowVersion<ReferenceLine>
    ): Pair<ReferenceLine, LayoutAlignment> = referenceLineWithAlignment(dao, alignmentDao, version)

    private fun associateWithAlignments(lines: List<ReferenceLine>): List<Pair<ReferenceLine, LayoutAlignment>> {
        // This is a little convoluted to avoid extra passes of transaction annotation handling in
        // alignmentDao.fetch
        val alignments = alignmentDao.fetchMany(lines.map(ReferenceLine::getAlignmentVersionOrThrow))
        return lines.map { line -> line to alignments.getValue(line.getAlignmentVersionOrThrow()) }
    }

    fun listNonLinked(branch: LayoutBranch): List<ReferenceLine> {
        return dao.fetchVersionsNonLinked(branch.draft).map(dao::fetch)
    }

    fun listNear(layoutContext: LayoutContext, bbox: BoundingBox): List<ReferenceLine> {
        return dao.fetchVersionsNear(layoutContext, bbox, includeDeleted = false).map(dao::fetch)
    }

    override fun mergeToMainBranch(
        fromBranch: DesignBranch,
        id: IntId<ReferenceLine>,
    ): LayoutRowVersion<ReferenceLine> {
        val line = fetchAndCheckForMerging(fromBranch, id)
        return dao.save(
            asMainDraft(line.copy(alignmentVersion = alignmentService.duplicate(line.getAlignmentVersionOrThrow())))
        )
    }

    override fun cancelInternal(asset: ReferenceLine, designBranch: DesignBranch) =
        cancelled(
            asset.copy(alignmentVersion = alignmentService.duplicate(asset.getAlignmentVersionOrThrow())),
            designBranch.designId,
        )
}

fun referenceLineWithAlignment(
    referenceLineDao: ReferenceLineDao,
    alignmentDao: LayoutAlignmentDao,
    rowVersion: LayoutRowVersion<ReferenceLine>,
) = referenceLineDao.fetch(rowVersion).let { track -> track to alignmentDao.fetch(track.getAlignmentVersionOrThrow()) }

fun filterByBoundingBox(list: List<ReferenceLine>, boundingBox: BoundingBox?): List<ReferenceLine> =
    if (boundingBox != null) list.filter { t -> boundingBox.intersects(t.boundingBox) } else list
