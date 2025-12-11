package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.PublicationResultVersions
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class ReferenceLineService(
    dao: ReferenceLineDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val referenceLineDao: ReferenceLineDao,
    private val geocodingService: GeocodingService,
) : LayoutAssetService<ReferenceLine, NoParams, ReferenceLineDao>(dao) {

    @Transactional
    fun addTrackNumberReferenceLine(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
        startAddress: TrackMeter,
    ): LayoutRowVersion<ReferenceLine> {
        return saveDraft(
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
            requireNotNull(dao.fetchVersionByTrackNumberId(branch.draft, trackNumberId)) {
                "Track number should have a reference line: trackNumber=$trackNumberId branch=$branch"
            }
        val original = dao.fetch(originalVersion)
        return if (original.startAddress != startAddress) {
            saveDraft(
                branch,
                original.copy(startAddress = startAddress, geometryVersion = updatedGeometryVersion(original)),
            )
        } else {
            null
        }
    }

    @Transactional
    fun saveDraft(branch: LayoutBranch, draftAsset: ReferenceLine): LayoutRowVersion<ReferenceLine> =
        saveDraftInternal(branch, draftAsset, NoParams.instance)

    @Transactional
    override fun saveDraftInternal(
        branch: LayoutBranch,
        draftAsset: ReferenceLine,
        params: NoParams,
    ): LayoutRowVersion<ReferenceLine> =
        super.saveDraftInternal(branch, draftAsset.copy(geometryVersion = updatedGeometryVersion(draftAsset)), params)

    @Transactional
    fun saveDraft(
        branch: LayoutBranch,
        draftAsset: ReferenceLine,
        geometry: ReferenceLineGeometry,
    ): LayoutRowVersion<ReferenceLine> {
        val geometryVersion =
            // If we're creating a new row or starting a draft, we duplicate the geometry to not edit any original
            if (draftAsset.dataType == TEMP || draftAsset.isOfficial) {
                saveAsNewGeometry(geometry)
            }
            // Ensure that we update the correct one.
            else if (draftAsset.getGeometryVersionOrThrow().id != geometry.id) {
                saveGeometry(geometry.copy(id = draftAsset.getGeometryVersionOrThrow().id, dataType = STORED))
            } else {
                saveGeometry(geometry)
            }
        return saveDraftInternal(branch, draftAsset.copy(geometryVersion = geometryVersion), NoParams.instance)
    }

    private fun updatedGeometryVersion(line: ReferenceLine): RowVersion<ReferenceLineGeometry>? =
        // If we're creating a new row or starting a draft, we duplicate the geometry to not edit any original
        if (line.dataType == TEMP || line.isOfficial) duplicateOrNewGeometry(line.geometryVersion)
        else line.geometryVersion

    @Transactional
    override fun publish(
        branch: LayoutBranch,
        version: LayoutRowVersion<ReferenceLine>,
    ): PublicationResultVersions<ReferenceLine> {
        val publishedVersion = publishInternal(branch, version)
        // Some of the versions may get deleted in publication -> delete any alignments they left
        // behind
        alignmentDao.deleteOrphanedRerefenceLineGeometries()
        return publishedVersion
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<ReferenceLine>): LayoutRowVersion<ReferenceLine> {
        val deletedVersion = super.deleteDraft(branch, id)
        dao.fetch(deletedVersion).geometryVersion?.id?.let(alignmentDao::delete)
        return deletedVersion
    }

    @Transactional
    fun deleteDraftByTrackNumberId(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): LayoutRowVersion<ReferenceLine>? {
        val referenceLine = referenceLineDao.getByTrackNumber(branch.draft, trackNumberId)
        return if (referenceLine?.isDraft == true) deleteDraft(branch, referenceLine.id as IntId) else null
    }

    fun getByTrackNumber(layoutContext: LayoutContext, trackNumberId: IntId<LayoutTrackNumber>): ReferenceLine? {
        return dao.getByTrackNumber(layoutContext, trackNumberId)
    }

    @Transactional(readOnly = true)
    fun getByTrackNumberWithGeometry(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): Pair<ReferenceLine, ReferenceLineGeometry>? {
        return dao.fetchVersionByTrackNumberId(layoutContext, trackNumberId)?.let(::getWithGeometryInternal)
    }

    @Transactional(readOnly = true)
    fun getWithGeometryOrThrow(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): Pair<ReferenceLine, ReferenceLineGeometry> {
        return getWithGeometryInternalOrThrow(layoutContext, id)
    }

    @Transactional(readOnly = true)
    fun getWithGeometry(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): Pair<ReferenceLine, ReferenceLineGeometry>? {
        return getWithGeometryInternal(layoutContext, id)
    }

    @Transactional(readOnly = true)
    fun getWithGeometry(version: LayoutRowVersion<ReferenceLine>): Pair<ReferenceLine, ReferenceLineGeometry> {
        return getWithGeometryInternal(version)
    }

    @Transactional(readOnly = true)
    fun getManyWithGeometries(
        layoutContext: LayoutContext,
        ids: List<IntId<ReferenceLine>>,
    ): List<Pair<ReferenceLine, ReferenceLineGeometry>> {
        return dao.getMany(layoutContext, ids).let(::associateWithGeometries)
    }

    @Transactional(readOnly = true)
    fun getManyWithGeometries(
        versions: List<LayoutRowVersion<ReferenceLine>>
    ): List<Pair<ReferenceLine, ReferenceLineGeometry>> {
        return dao.fetchMany(versions).let(::associateWithGeometries)
    }

    @Transactional(readOnly = true)
    fun listWithGeometries(
        layoutContext: LayoutContext,
        includeDeleted: Boolean = false,
        boundingBox: BoundingBox? = null,
    ): List<Pair<ReferenceLine, ReferenceLineGeometry>> {
        return (if (boundingBox == null) {
                dao.list(layoutContext, includeDeleted)
            } else {
                dao.fetchVersionsNear(layoutContext, boundingBox, includeDeleted).let(dao::fetchMany)
            })
            .let { list -> filterByBoundingBox(list, boundingBox) }
            .let(::associateWithGeometries)
    }

    @Transactional(readOnly = true)
    fun getStartAndEnd(context: LayoutContext, id: IntId<ReferenceLine>): AlignmentStartAndEnd<ReferenceLine>? {
        return getWithGeometry(context, id)?.let { (referenceLine, geometry) ->
            val geocodingContext = geocodingService.getGeocodingContext(context, referenceLine.trackNumberId)
            AlignmentStartAndEnd.of(id, geometry, geocodingContext)
        }
    }

    private fun getWithGeometryInternalOrThrow(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): Pair<ReferenceLine, ReferenceLineGeometry> {
        return getWithGeometryInternal(dao.fetchVersionOrThrow(layoutContext, id))
    }

    private fun getWithGeometryInternal(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): Pair<ReferenceLine, ReferenceLineGeometry>? {
        return dao.fetchVersion(layoutContext, id)?.let { v -> getWithGeometryInternal(v) }
    }

    private fun getWithGeometryInternal(
        version: LayoutRowVersion<ReferenceLine>
    ): Pair<ReferenceLine, ReferenceLineGeometry> = referenceLineWithGeometry(dao, alignmentDao, version)

    private fun associateWithGeometries(lines: List<ReferenceLine>): List<Pair<ReferenceLine, ReferenceLineGeometry>> {
        // This is a little convoluted to avoid extra passes of transaction annotation handling in alignmentDao.fetch
        val geometries = alignmentDao.fetchMany(lines.map(ReferenceLine::getGeometryVersionOrThrow))
        return lines.map { line -> line to geometries.getValue(line.getGeometryVersionOrThrow()) }
    }

    fun listNonLinked(branch: LayoutBranch): List<ReferenceLine> {
        return dao.fetchVersionsNonLinked(branch.draft).let(dao::fetchMany)
    }

    fun listNear(layoutContext: LayoutContext, bbox: BoundingBox): List<ReferenceLine> {
        return dao.fetchVersionsNear(layoutContext, bbox, includeDeleted = false).let(dao::fetchMany)
    }

    override fun mergeToMainBranch(
        fromBranch: DesignBranch,
        id: IntId<ReferenceLine>,
    ): LayoutRowVersion<ReferenceLine> {
        val line = fetchAndCheckForMerging(fromBranch, id).first
        return dao.save(asMainDraft(line.copy(geometryVersion = duplicateGeometry(line.getGeometryVersionOrThrow()))))
    }

    override fun cancelInternal(asset: ReferenceLine, designBranch: DesignBranch) =
        cancelled(
            asset.copy(geometryVersion = duplicateGeometry(asset.getGeometryVersionOrThrow())),
            designBranch.designId,
        )

    private fun saveAsNewGeometry(geometry: ReferenceLineGeometry): RowVersion<ReferenceLineGeometry> =
        saveGeometry(asNew(geometry))

    private fun duplicateOrNewGeometry(
        geometryVersion: RowVersion<ReferenceLineGeometry>?
    ): RowVersion<ReferenceLineGeometry> = geometryVersion?.let(::duplicateGeometry) ?: newEmptyGeometry().second

    private fun duplicateGeometry(
        geometryVersion: RowVersion<ReferenceLineGeometry>
    ): RowVersion<ReferenceLineGeometry> = saveGeometry(asNew(alignmentDao.fetch(geometryVersion)))

    private fun saveGeometry(geometry: ReferenceLineGeometry): RowVersion<ReferenceLineGeometry> =
        if (geometry.dataType == STORED) alignmentDao.update(geometry) else alignmentDao.insert(geometry)

    private fun newEmptyGeometry(): Pair<ReferenceLineGeometry, RowVersion<ReferenceLineGeometry>> {
        val geometry = emptyAlignment()
        return geometry to alignmentDao.insert(geometry)
    }
}

fun referenceLineWithGeometry(
    referenceLineDao: ReferenceLineDao,
    alignmentDao: LayoutAlignmentDao,
    rowVersion: LayoutRowVersion<ReferenceLine>,
) =
    referenceLineDao.fetch(rowVersion).let { referenceLine ->
        referenceLine to alignmentDao.fetch(referenceLine.getGeometryVersionOrThrow())
    }

fun filterByBoundingBox(list: List<ReferenceLine>, boundingBox: BoundingBox?): List<ReferenceLine> =
    if (boundingBox != null) list.filter { t -> boundingBox.intersects(t.boundingBox) } else list

private fun asNew(geometry: ReferenceLineGeometry): ReferenceLineGeometry =
    if (geometry.dataType == TEMP) geometry else geometry.copy(id = StringId(), dataType = TEMP)
