package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.error.SplitSourceLocationTrackUpdateException
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geometry.GeometryPlanHeader
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.switches.TopologyLinkFindingSwitch
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.PublicationResultVersions
import fi.fta.geoviite.infra.ratko.RatkoOperatingPointDao
import fi.fta.geoviite.infra.ratko.model.OperationalPointType
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitDuplicateTrack
import fi.fta.geoviite.infra.split.SplittingInitializationParameters
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.mapNonNullValues
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

const val TRACK_SEARCH_AREA_SIZE = 2.0
const val OPERATING_POINT_AROUND_SWITCH_SEARCH_AREA_SIZE = 1000.0

@GeoviiteService
class LocationTrackService(
    val locationTrackDao: LocationTrackDao,
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val geocodingService: GeocodingService,
    private val switchDao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val splitDao: SplitDao,
    private val ratkoOperatingPointDao: RatkoOperatingPointDao,
    private val trackNumberService: LayoutTrackNumberService,
    private val localizationService: LocalizationService,
    private val transactionTemplate: TransactionTemplate,
) : LayoutAssetService<LocationTrack, LocationTrackDao>(locationTrackDao) {

    @Transactional
    fun insert(branch: LayoutBranch, request: LocationTrackSaveRequest): LayoutRowVersion<LocationTrack> {
        val (alignment, alignmentVersion) = alignmentService.newEmpty()
        val locationTrack =
            LocationTrack(
                alignmentVersion = alignmentVersion,
                namingScheme = request.namingScheme,
                nameFreeText = request.nameFreeText,
                nameSpecifier = request.nameSpecifier,
                descriptionBase = request.descriptionBase,
                descriptionSuffix = request.descriptionSuffix,
                type = request.type,
                state = request.state,
                trackNumberId = request.trackNumberId,
                sourceId = null,
                length = alignment.length,
                segmentCount = alignment.segments.size,
                boundingBox = alignment.boundingBox,
                duplicateOf = request.duplicateOf,
                topologicalConnectivity = request.topologicalConnectivity,
                topologyStartSwitch = null,
                topologyEndSwitch = null,
                ownerId = request.ownerId,
                contextData = LayoutContextData.newDraft(branch, dao.createId()),
            )
        return saveDraftInternal(branch, locationTrack)
    }

    fun update(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        request: LocationTrackSaveRequest,
    ): LayoutRowVersion<LocationTrack> {
        try {
            return requireNotNull(transactionTemplate.execute { updateLocationTrackTransaction(branch, id, request) })
        } catch (dataIntegrityException: DataIntegrityViolationException) {
            throw if (isSplitSourceReferenceError(dataIntegrityException)) {
                SplitSourceLocationTrackUpdateException(getNameOrThrow(branch.draft, id).name, dataIntegrityException)
            } else {
                dataIntegrityException
            }
        }
    }

    private fun updateLocationTrackTransaction(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        request: LocationTrackSaveRequest,
    ): LayoutRowVersion<LocationTrack> {
        val (originalTrack, originalAlignment) = getWithAlignmentInternalOrThrow(branch.draft, id)
        val locationTrack =
            originalTrack.copy(
                namingScheme = request.namingScheme,
                nameFreeText = request.nameFreeText,
                nameSpecifier = request.nameSpecifier,
                descriptionBase = request.descriptionBase,
                descriptionSuffix = request.descriptionSuffix,
                type = request.type,
                state = request.state,
                trackNumberId = request.trackNumberId,
                duplicateOf = request.duplicateOf,
                topologicalConnectivity = request.topologicalConnectivity,
                ownerId = request.ownerId,
            )

        return if (locationTrack.state != LocationTrackState.DELETED) {
            saveDraft(
                branch,
                fetchNearbyTracksAndCalculateLocationTrackTopology(branch.draft, locationTrack, originalAlignment),
            )
        } else {
            clearDuplicateReferences(branch, id)
            val segmentsWithoutSwitch = originalAlignment.segments.map(LayoutSegment::withoutSwitch)
            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
            val newTrack = fetchNearbyTracksAndCalculateLocationTrackTopology(branch.draft, locationTrack, newAlignment)
            saveDraft(branch, newTrack, newAlignment)
        }
    }

    @Transactional
    fun updateState(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        state: LocationTrackState,
    ): LayoutRowVersion<LocationTrack> {
        val (originalTrack, originalAlignment) = getWithAlignmentInternalOrThrow(branch.draft, id)
        val locationTrack = originalTrack.copy(state = state)

        return if (locationTrack.state != LocationTrackState.DELETED) {
            saveDraft(branch, locationTrack)
        } else {
            clearDuplicateReferences(branch, id)
            val segmentsWithoutSwitch = originalAlignment.segments.map(LayoutSegment::withoutSwitch)
            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
            val newTrack = fetchNearbyTracksAndCalculateLocationTrackTopology(branch.draft, locationTrack, newAlignment)
            saveDraft(branch, newTrack, newAlignment)
        }
    }

    @Transactional(readOnly = true)
    fun getStartAndEnd(
        context: LayoutContext,
        ids: List<IntId<LocationTrack>>,
    ): List<AlignmentStartAndEnd<LocationTrack>> {
        val tracksAndAlignments = getManyWithAlignments(context, ids)
        val getGeocodingContext = geocodingService.getLazyGeocodingContexts(context)
        return tracksAndAlignments.map { (track, alignment) ->
            AlignmentStartAndEnd.of(track.id as IntId, alignment, getGeocodingContext(track.trackNumberId))
        }
    }

    @Transactional
    override fun saveDraft(branch: LayoutBranch, draftAsset: LocationTrack): LayoutRowVersion<LocationTrack> =
        super.saveDraft(branch, draftAsset.copy(alignmentVersion = updatedAlignmentVersion(draftAsset)))

    private fun updatedAlignmentVersion(track: LocationTrack): RowVersion<LayoutAlignment>? =
        // If we're creating a new row or starting a draft, we duplicate the alignment to not edit
        // any original
        if (track.dataType == TEMP || track.isOfficial) alignmentService.duplicateOrNew(track.alignmentVersion)
        else track.alignmentVersion

    @Transactional
    fun saveDraft(
        branch: LayoutBranch,
        draftAsset: LocationTrack,
        alignment: LayoutAlignment,
    ): LayoutRowVersion<LocationTrack> {
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

    @Transactional
    fun insertExternalId(branch: LayoutBranch, id: IntId<LocationTrack>, oid: Oid<LocationTrack>) =
        dao.insertExternalId(id, branch, oid)

    @Transactional
    override fun publish(
        branch: LayoutBranch,
        version: LayoutRowVersion<LocationTrack>,
    ): PublicationResultVersions<LocationTrack> {
        val publishedVersion = publishInternal(branch, version)
        // Some of the versions may get deleted in publication -> delete any alignments they left
        // behind
        alignmentDao.deleteOrphanedAlignments()
        return publishedVersion
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<LocationTrack>): LayoutRowVersion<LocationTrack> {
        // If removal also breaks references, clear them out first
        if (dao.fetchVersion(branch.official, id) == null) {
            clearDuplicateReferences(branch, id)
        }
        val deletedVersion = super.deleteDraft(branch, id)
        dao.fetch(deletedVersion).alignmentVersion?.id?.let(alignmentDao::delete)
        return deletedVersion
    }

    @Transactional
    fun fetchDuplicates(layoutContext: LayoutContext, id: IntId<LocationTrack>): List<LocationTrack> {
        return dao.fetchDuplicateVersions(layoutContext, id).map(dao::fetch)
    }

    @Transactional
    fun clearDuplicateReferences(branch: LayoutBranch, id: IntId<LocationTrack>) =
        dao.fetchDuplicateVersions(branch.draft, id, includeDeleted = true)
            .map(dao::fetch)
            .map { dup -> asDraft(branch, dup) }
            .forEach { duplicate -> saveDraft(branch, duplicate.copy(duplicateOf = null)) }

    fun listNonLinked(branch: LayoutBranch): List<LocationTrack> {
        return dao.list(branch.draft, false).filter { a -> a.segmentCount == 0 }
    }

    fun list(layoutContext: LayoutContext, bbox: BoundingBox): List<LocationTrack> {
        return dao.list(layoutContext, false).filter { tn -> bbox.intersects(tn.boundingBox) }
    }

    fun list(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): List<LocationTrack> {
        return dao.list(layoutContext, includeDeleted, trackNumberId)
    }

    fun idMatches(
        layoutContext: LayoutContext,
        possibleIds: List<IntId<LocationTrack>>? = null,
    ): ((term: String, item: LocationTrack) -> Boolean) =
        dao.fetchExternalIds(layoutContext.branch, possibleIds).let { externalIds ->
            return { term, item -> externalIds[item.id]?.oid?.toString() == term || item.id.toString() == term }
        }

    override fun contentMatches(term: String, item: AugLocationTrack) =
        item.exists && ((item.name ?: "").contains(term, true) || item.description.contains(term, true))

    fun listNear(layoutContext: LayoutContext, bbox: BoundingBox): List<AugLocationTrack> {
        return dao.listNear(layoutContext, bbox).filter(AugLocationTrack::exists)
    }

    @Transactional(readOnly = true)
    fun listWithAlignments(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        includeDeleted: Boolean = false,
        boundingBox: BoundingBox? = null,
        minLength: Double? = null,
        locationTrackIds: Set<IntId<LocationTrack>>? = null,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return if (boundingBox == null) {
                dao.list(layoutContext, includeDeleted, trackNumberId)
            } else {
                dao.fetchVersionsNear(layoutContext, boundingBox, includeDeleted, trackNumberId, minLength)
                    .map(dao::fetch)
            }
            .let { list ->
                if (locationTrackIds == null) list
                else
                    list.filter { locationTrack -> locationTrackIds.contains(locationTrack.id as IntId<LocationTrack>) }
            }
            .let { list -> filterByBoundingBox(list, boundingBox) }
            .let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun getManyWithAlignments(
        layoutContext: LayoutContext,
        ids: List<IntId<LocationTrack>>,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return dao.getMany(layoutContext, ids).let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun getWithAlignmentOrThrow(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, LayoutAlignment> {
        return getWithAlignmentInternalOrThrow(layoutContext, id)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, LayoutAlignment>? {
        return dao.fetchVersion(layoutContext, id)?.let(::getWithAlignmentInternal)
    }

    @Transactional(readOnly = true)
    fun getTrackPoint(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        address: TrackMeter,
    ): AddressPoint? {
        val locationTrackAndAlignment = getWithAlignment(layoutContext, locationTrackId)
        return locationTrackAndAlignment?.let { (locationTrack, alignment) ->
            geocodingService.getTrackLocation(layoutContext, locationTrack, alignment, address)
        }
    }

    @Transactional(readOnly = true)
    fun getOfficialWithAlignmentAtMoment(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        moment: Instant,
    ): Pair<LocationTrack, LayoutAlignment>? {
        return dao.fetchOfficialVersionAtMoment(branch, id, moment)?.let(::getWithAlignmentInternal)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(version: LayoutRowVersion<LocationTrack>): Pair<LocationTrack, LayoutAlignment> {
        return getWithAlignmentInternal(version)
    }

    @Transactional(readOnly = true)
    fun listNearWithAlignments(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
    ): List<Pair<LocationTrack, LayoutAlignment>> =
        dao.listNear(layoutContext, bbox).let(::associateWithAlignments).filter { (_, alignment) ->
            alignment.segments.any { segment ->
                bbox.intersects(segment.boundingBox) && segment.segmentPoints.any(bbox::contains)
            }
        }

    @Transactional(readOnly = true)
    fun getLocationTracksNear(
        layoutContext: LayoutContext,
        location: IPoint,
    ): List<Pair<LocationTrack, LayoutAlignment>> =
        listNearWithAlignments(
            layoutContext,
            BoundingBox(Point(0.0, 0.0), Point(TRACK_SEARCH_AREA_SIZE, TRACK_SEARCH_AREA_SIZE)).centerAt(location),
        )

    @Transactional(readOnly = true)
    fun getMetadataSections(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection> {
        val locationTrack = get(layoutContext, locationTrackId)
        val geocodingContext =
            locationTrack?.let { geocodingService.getGeocodingContext(layoutContext, locationTrack.trackNumberId) }

        return if (geocodingContext != null && locationTrack.alignmentVersion != null) {
            alignmentService.getGeometryMetadataSections(
                locationTrack.alignmentVersion,
                dao.fetchExternalId(layoutContext.branch, locationTrackId)?.oid,
                boundingBox,
                geocodingContext,
            )
        } else listOf()
    }

    @Transactional(readOnly = true)
    fun getOverlappingPlanHeaders(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        polygonBufferSize: Double,
        cropStart: KmNumber?,
        cropEnd: KmNumber?,
    ): List<GeometryPlanHeader> =
        get(layoutContext, locationTrackId)?.let { locationTrack ->
            geocodingService.getGeocodingContext(layoutContext, locationTrack.trackNumberId)?.let { context ->
                val alignment = alignmentDao.fetch(requireNotNull(locationTrack.alignmentVersion))

                val startAndEnd = getStartAndEnd(layoutContext, listOf(locationTrackId)).first()
                val trackStart = startAndEnd.start?.address
                val trackEnd = startAndEnd.end?.address

                if (trackStart == null || trackEnd == null || !cropIsWithinReferenceLine(cropStart, cropEnd, context)) {
                    emptyList()
                } else if (cropStart == null && cropEnd == null) {
                    alignmentService.getOverlappingPlanHeaders(
                        alignment,
                        polygonBufferSize,
                        cropStartM = null,
                        cropEndM = null,
                    )
                } else {
                    getPlanHeadersOverlappingCroppedLocationTrack(
                        alignment,
                        context,
                        polygonBufferSize,
                        trackStart,
                        trackEnd,
                        cropStart,
                        cropEnd,
                    )
                }
            }
        } ?: emptyList()

    private fun getPlanHeadersOverlappingCroppedLocationTrack(
        alignment: LayoutAlignment,
        geocodingContext: GeocodingContext,
        polygonBufferSize: Double,
        trackStart: TrackMeter,
        trackEnd: TrackMeter,
        cropStartKm: KmNumber?,
        cropEndKm: KmNumber?,
    ): List<GeometryPlanHeader> {
        val cropStart = cropStartKm?.let { TrackMeter(cropStartKm, 0) } ?: trackStart
        val cropEnd = getCropEndForPlanHeaderSearch(cropEndKm, trackEnd, geocodingContext)

        return if (cropEnd == null || cropStart > trackEnd || cropEnd < trackStart) {
            emptyList()
        } else {
            val cropStartM =
                geocodingContext.getTrackLocation(alignment, cropStart.coerceIn(trackStart, trackEnd))?.point?.m
            val cropEndM =
                geocodingContext.getTrackLocation(alignment, cropEnd.coerceIn(trackStart, trackEnd))?.point?.m

            if (cropStartM == null || cropEndM == null) listOf()
            else {
                alignmentService.getOverlappingPlanHeaders(alignment, polygonBufferSize, cropStartM, cropEndM)
            }
        }
    }

    private fun getCropEndForPlanHeaderSearch(
        cropEndKm: KmNumber?,
        trackEnd: TrackMeter,
        geocodingContext: GeocodingContext,
    ): TrackMeter? =
        if (cropEndKm == null) trackEnd
        else
            geocodingContext.referencePoints
                .indexOfFirst { it.kmNumber == cropEndKm }
                .let { endKmIndex ->
                    if (endKmIndex == -1) null
                    else if (endKmIndex == geocodingContext.referencePoints.lastIndex) trackEnd
                    else TrackMeter(geocodingContext.referencePoints[endKmIndex + 1].kmNumber, 0)
                }

    private fun getSwitchIdAtStart(alignment: LayoutAlignment, locationTrack: LocationTrack) =
        if (alignment.segments.firstOrNull()?.startJointNumber == null) locationTrack.topologyStartSwitch?.switchId
        else alignment.segments.firstOrNull()?.switchId

    private fun getSwitchIdAtEnd(alignment: LayoutAlignment, locationTrack: LocationTrack) =
        if (alignment.segments.lastOrNull()?.endJointNumber == null) locationTrack.topologyEndSwitch?.switchId
        else alignment.segments.lastOrNull()?.switchId

    @Transactional(readOnly = true)
    fun getFullDescriptions(
        layoutContext: LayoutContext,
        locationTracks: List<LocationTrack>,
        lang: LocalizationLanguage,
    ): List<FreeText> {
        val startAndEndSwitchIds =
            locationTracks.map { locationTrack ->
                locationTrack.alignmentVersion?.let { alignmentVersion ->
                    val alignment = alignmentDao.fetch(alignmentVersion)
                    getSwitchIdAtStart(alignment, locationTrack) to getSwitchIdAtEnd(alignment, locationTrack)
                } ?: (null to null)
            }
        val switches =
            switchDao
                .getMany(layoutContext, startAndEndSwitchIds.flatMap { listOfNotNull(it.first, it.second) })
                .associateBy { switch -> switch.id }

        fun getSwitchShortName(switchId: IntId<LayoutSwitch>) = switches[switchId]?.shortName
        val translation = localizationService.getLocalization(lang)

        return locationTracks.zip(startAndEndSwitchIds) { locationTrack, startAndEndSwitch ->
            val startSwitchName = startAndEndSwitch.first?.let(::getSwitchShortName)
            val endSwitchName = startAndEndSwitch.second?.let(::getSwitchShortName)
            val descriptionBase = locationTrack.descriptionBase.toString()

            when (locationTrack.descriptionSuffix) {
                LocationTrackDescriptionSuffix.NONE -> FreeText(descriptionBase)

                LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER ->
                    FreeText(
                        "${descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - ${translation.t("location-track-dialog.buffer")}"
                    )

                LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH ->
                    FreeText("${descriptionBase} ${startSwitchName ?: "???"} - ${endSwitchName ?: "???"}")

                LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY ->
                    FreeText(
                        "${descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - ${translation.t("location-track-dialog.ownership-boundary")}"
                    )
            }
        }
    }

    fun getFullDescription(
        layoutContext: LayoutContext,
        locationTrack: LocationTrack,
        lang: LocalizationLanguage,
    ): FreeText = getFullDescriptions(layoutContext, listOf(locationTrack), lang).first()

    private fun getWithAlignmentInternalOrThrow(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, LayoutAlignment> {
        return getWithAlignmentInternal(dao.fetchVersionOrThrow(layoutContext, id))
    }

    private fun getWithAlignmentInternal(
        version: LayoutRowVersion<LocationTrack>
    ): Pair<LocationTrack, LayoutAlignment> = locationTrackWithAlignment(dao, alignmentDao, version)

    private fun associateWithAlignments(lines: List<LocationTrack>): List<Pair<LocationTrack, LayoutAlignment>> {
        // This is a little convoluted to avoid extra passes of transaction annotation handling in
        // alignmentDao.fetch
        val alignments = alignmentDao.fetchMany(lines.map(LocationTrack::getAlignmentVersionOrThrow))
        return lines.map { line -> line to alignments.getValue(line.getAlignmentVersionOrThrow()) }
    }

    fun fillTrackAddress(splitPoint: SplitPoint, geocodingContext: GeocodingContext): SplitPoint {
        val address = geocodingContext.getAddress(splitPoint.location)?.first
        return when (splitPoint) {
            is SwitchSplitPoint -> splitPoint.copy(address = address)
            is EndpointSplitPoint -> splitPoint.copy(address = address)
        }
    }

    fun getAugLocationTrack(id: IntId<LocationTrack>, layoutLocation: LayoutContext): AugLocationTrack? = TODO()

    fun listAugLocationTracks(
        layoutLocation: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        boundingBox: BoundingBox? = null,
    ): List<AugLocationTrack> = TODO()

    fun fillTrackAddresses(
        duplicates: List<LocationTrackDuplicate>,
        geocodingContext: GeocodingContext,
    ): List<LocationTrackDuplicate> {
        return duplicates.map { duplicate ->
            duplicate.copy(
                duplicateStatus =
                    duplicate.duplicateStatus.copy(
                        startSplitPoint =
                            duplicate.duplicateStatus.startSplitPoint?.let { splitPoint ->
                                fillTrackAddress(splitPoint, geocodingContext)
                            },
                        endSplitPoint =
                            duplicate.duplicateStatus.endSplitPoint?.let { splitPoint ->
                                fillTrackAddress(splitPoint, geocodingContext)
                            },
                    )
            )
        }
    }

    @Transactional(readOnly = true)
    fun getInfoboxExtras(layoutContext: LayoutContext, id: IntId<LocationTrack>): LocationTrackInfoboxExtras? {
        return getWithAlignment(layoutContext, id)?.let { (track, alignment) ->
            val geocodingContext = geocodingService.getGeocodingContext(layoutContext, track.trackNumberId)
            val start = alignment.start
            val end = alignment.end

            val duplicateOf = getDuplicateTrackParent(layoutContext, track)
            val duplicates =
                getLocationTrackDuplicates(layoutContext, track, alignment)
                    .let { dups -> geocodingContext?.let { gc -> fillTrackAddresses(dups, gc) } ?: dups }
                    .sortedBy { dup -> dup.duplicateStatus.startSplitPoint?.address }

            val endPointSwitchInfos =
                getEndPointSwitchInfos(track, alignment, createFunIsPresentationJointNumberInContext(layoutContext))

            val startSplitPoint =
                createSplitPoint(
                    start,
                    endPointSwitchInfos.start?.switchId,
                    DuplicateEndPointType.START,
                    geocodingContext,
                )
            val endSplitPoint =
                createSplitPoint(end, endPointSwitchInfos.end?.switchId, DuplicateEndPointType.END, geocodingContext)

            val startSwitch = endPointSwitchInfos.start?.switchId?.let { id -> fetchSwitchAtEndById(layoutContext, id) }
            val endSwitch = endPointSwitchInfos.end?.switchId?.let { id -> fetchSwitchAtEndById(layoutContext, id) }
            val partOfUnfinishedSplit =
                splitDao.locationTracksPartOfAnyUnfinishedSplit(layoutContext.branch, listOf(id)).isNotEmpty()

            LocationTrackInfoboxExtras(
                duplicateOf,
                duplicates,
                startSwitch,
                endSwitch,
                partOfUnfinishedSplit,
                startSplitPoint,
                endSplitPoint,
            )
        }
    }

    private fun createSplitPoint(
        point: AlignmentPoint?,
        switchId: IntId<LayoutSwitch>?,
        endPointType: DuplicateEndPointType,
        geocodingContext: GeocodingContext?,
    ): SplitPoint? {
        val address = point?.let { p -> geocodingContext?.getAddress(p)?.first }
        return when {
            (point == null || address == null) -> null
            (switchId != null) -> SwitchSplitPoint(point, address, switchId, JointNumber(0))
            else -> EndpointSplitPoint(point, address, endPointType)
        }
    }

    @Transactional(readOnly = true)
    fun getRelinkableSwitchesCount(layoutContext: LayoutContext, id: IntId<LocationTrack>): Int? =
        getWithAlignment(layoutContext, id)?.let { (track, alignment) ->
            countRelinkableSwitches(layoutContext.branch, track, alignment)
        }

    private fun countRelinkableSwitches(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
    ): Int =
        (alignment.segments.mapNotNull { it.switchId } +
                listOfNotNull(locationTrack.topologyStartSwitch?.switchId, locationTrack.topologyEndSwitch?.switchId) +
                switchDao.findSwitchesNearAlignment(branch, locationTrack.getAlignmentVersionOrThrow()))
            .distinct()
            .size

    private fun isPresentationJointNumber(
        layoutContext: LayoutContext,
        switchId: IntId<LayoutSwitch>,
        jointNumber: JointNumber,
    ): Boolean {
        return switchDao.get(layoutContext, switchId)?.let { switch ->
            switchLibraryService.getPresentationJointNumber(switch.switchStructureId) == jointNumber
        } ?: false
    }

    private fun createFunIsPresentationJointNumberInContext(
        layoutContext: LayoutContext
    ): (IntId<LayoutSwitch>, JointNumber) -> Boolean {
        return { switchId: IntId<LayoutSwitch>, jointNumber: JointNumber ->
            isPresentationJointNumber(layoutContext, switchId, jointNumber)
        }
    }

    @Transactional(readOnly = true)
    fun getLocationTrackDuplicates(
        layoutContext: LayoutContext,
        track: LocationTrack,
        alignment: LayoutAlignment,
    ): List<LocationTrackDuplicate> {
        val markedDuplicateVersions = dao.fetchDuplicateVersions(layoutContext, track.id as IntId)
        val tracksLinkedThroughSwitch =
            switchDao
                .findLocationTracksLinkedToSwitches(layoutContext, track.switchIds)
                .values
                .flatten()
                .map(LayoutSwitchDao.LocationTrackIdentifiers::rowVersion)
        val duplicateTracksAndAlignments =
            (markedDuplicateVersions + tracksLinkedThroughSwitch).distinct().map(::getWithAlignmentInternal).filter {
                (duplicateTrack, _) ->
                duplicateTrack.id != track.id && duplicateTrack.id != track.duplicateOf
            }
        return getLocationTrackDuplicatesBySplitPoints(
            track,
            alignment,
            duplicateTracksAndAlignments,
            createFunIsPresentationJointNumberInContext(layoutContext),
        )
    }

    private fun getDuplicateTrackParent(
        layoutContext: LayoutContext,
        childTrack: LocationTrack,
    ): LocationTrackDuplicate? =
        childTrack.duplicateOf?.let { parentId ->
            getWithAlignment(layoutContext, parentId)?.let { (parentTrack, parentTrackAlignment) ->
                val childAlignment = alignmentDao.fetch(childTrack.getAlignmentVersionOrThrow())
                getDuplicateTrackParentStatus(
                    parentTrack,
                    parentTrackAlignment,
                    childTrack,
                    childAlignment,
                    createFunIsPresentationJointNumberInContext(layoutContext),
                )
            }
        }

    private fun fetchSwitchAtEndById(layoutContext: LayoutContext, id: IntId<LayoutSwitch>): LayoutSwitchIdAndName? =
        switchDao.get(layoutContext, id)?.let { switch -> LayoutSwitchIdAndName(id, switch.name) }

    fun fetchNearbyLocationTracksWithAlignments(
        layoutContext: LayoutContext,
        targetPoint: LayoutPoint,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return dao.fetchVersionsNear(layoutContext, boundingBoxAroundPoint(targetPoint, 1.0))
            .map { version -> getWithAlignmentInternal(version) }
            .filter { (track, alignment) -> alignment.segments.isNotEmpty() && track.exists }
    }

    @Transactional
    fun fetchNearbyTracksAndCalculateLocationTrackTopology(
        layoutContext: LayoutContext,
        track: LocationTrack,
        alignment: LayoutAlignment,
        startChanged: Boolean = false,
        endChanged: Boolean = false,
    ): LocationTrack {
        val nearbyTracksAroundStart =
            alignment.start
                ?.let { point -> fetchNearbyLocationTracksWithAlignments(layoutContext, point) }
                ?.filter { (nearbyLocationTrack, _) -> nearbyLocationTrack.id != track.id } ?: listOf()

        val nearbyTracksAroundEnd =
            alignment.end
                ?.let { point -> fetchNearbyLocationTracksWithAlignments(layoutContext, point) }
                ?.filter { (nearbyLocationTrack, _) -> nearbyLocationTrack.id != track.id } ?: listOf()

        return calculateLocationTrackTopology(
            track = track,
            alignment = alignment,
            startChanged = startChanged,
            endChanged = endChanged,
            nearbyTracks = NearbyTracks(aroundStart = nearbyTracksAroundStart, aroundEnd = nearbyTracksAroundEnd),
        )
    }

    fun getLocationTrackOwners(): List<LocationTrackOwner> {
        return dao.fetchLocationTrackOwners()
    }

    fun getLocationTrackOwner(id: IntId<LocationTrackOwner>): LocationTrackOwner {
        return getLocationTrackOwners().find { owner -> owner.id == id }
            ?: throw NoSuchEntityException(LocationTrackOwner::class, id)
    }

    @Transactional(readOnly = true)
    fun getSplittingInitializationParameters(
        layoutContext: LayoutContext,
        trackId: IntId<LocationTrack>,
    ): SplittingInitializationParameters? {
        val getGeocodingContext = geocodingService.getLazyGeocodingContexts(layoutContext)
        return getWithAlignment(layoutContext, trackId)?.let { (locationTrack, alignment) ->
            val switches =
                getSwitchesForLocationTrack(layoutContext, trackId)
                    .mapNotNull { id -> switchDao.get(layoutContext, id) }
                    .mapNotNull { switch ->
                        switchLibraryService.getSwitchStructure(switch.switchStructureId).let { structure ->
                            val presentationJointLocation = switch.getJoint(structure.presentationJointNumber)?.location
                            if (presentationJointLocation != null) {
                                switch to presentationJointLocation
                            } else {
                                null
                            }
                        }
                    }
                    .map { (switch, location) ->
                        val address = getGeocodingContext(locationTrack.trackNumberId)?.getAddressAndM(location)
                        val mAlongAlignment = alignment.getClosestPointM(location)?.first
                        SwitchOnLocationTrack(
                            switch.id as IntId,
                            switch.name,
                            address?.address,
                            location,
                            mAlongAlignment,
                            getNearestOperatingPoint(location),
                        )
                    }

            val duplicateTracks =
                getLocationTrackDuplicates(layoutContext, locationTrack, alignment).map { duplicate ->
                    SplitDuplicateTrack(
                        duplicate.id,
                        duplicate.namingScheme,
                        duplicate.nameFreeText,
                        duplicate.nameSpecifier,
                        duplicate.length,
                        duplicate.duplicateStatus,
                    )
                }

            SplittingInitializationParameters(trackId, switches, duplicateTracks)
        }
    }

    private fun getNearestOperatingPoint(location: Point) =
        ratkoOperatingPointDao
            .getOperatingPoints(boundingBoxAroundPoint(location, OPERATING_POINT_AROUND_SWITCH_SEARCH_AREA_SIZE))
            .filter { op -> op.type == OperationalPointType.LPO || op.type == OperationalPointType.LP }
            .minByOrNull { operatingPoint -> lineLength(operatingPoint.location, location) }

    @Transactional(readOnly = true)
    fun getSwitchesForLocationTrack(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
    ): List<IntId<LayoutSwitch>> {
        return getWithAlignment(layoutContext, locationTrackId)?.let { (track, alignment) ->
            collectAllSwitches(track, alignment)
        } ?: emptyList()
    }

    @Transactional(readOnly = true)
    fun getAlignmentsForTracks(tracks: List<LocationTrack>): List<Pair<LocationTrack, LayoutAlignment>> {
        return tracks.map { track ->
            val alignment =
                track.alignmentVersion?.let(alignmentDao::fetch)
                    ?: error(
                        "All location tracks should have an alignment. Alignment was not found for track=${track.id}"
                    )
            track to alignment
        }
    }

    override fun mergeToMainBranch(
        fromBranch: DesignBranch,
        id: IntId<LocationTrack>,
    ): LayoutRowVersion<LocationTrack> {
        val track = fetchAndCheckForMerging(fromBranch, id)
        return dao.save(
            asMainDraft(track.copy(alignmentVersion = alignmentService.duplicate(track.getAlignmentVersionOrThrow())))
        )
    }

    override fun cancelInternal(asset: LocationTrack, designBranch: DesignBranch) =
        cancelled(
            asset.copy(alignmentVersion = alignmentService.duplicate(asset.getAlignmentVersionOrThrow())),
            designBranch.designId,
        )

    fun getExternalIdChangeTime(): Instant = dao.getExternalIdChangeTime()

    @Transactional(readOnly = true)
    fun getExternalIdsByBranch(id: IntId<LocationTrack>): Map<LayoutBranch, Oid<LocationTrack>> {
        return mapNonNullValues(locationTrackDao.fetchExternalIdsByBranch(id)) { (_, v) -> v.oid }
    }

    @Transactional(readOnly = true)
    fun getNames(layoutContext: LayoutContext, ids: List<IntId<LocationTrack>>): List<LocationTrackName> {
        return locationTrackDao.getMany(layoutContext, ids).map { lt ->
            LocationTrackName(
                lt.id as IntId<LocationTrack>,
                getLocationTrackName(layoutContext, lt).let(::AlignmentName),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getNameOrThrow(layoutContext: LayoutContext, id: IntId<LocationTrack>): LocationTrackName {
        return locationTrackDao.getOrThrow(layoutContext, id).let { lt ->
            LocationTrackName(
                lt.id as IntId<LocationTrack>,
                getLocationTrackName(layoutContext, lt).let(::AlignmentName),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getNameAtMoment(branch: LayoutBranch, id: IntId<LocationTrack>, moment: Instant): LocationTrackName? {
        return locationTrackDao.getOfficialAtMoment(branch, id, moment)?.let { lt ->
            LocationTrackName(
                lt.id as IntId<LocationTrack>,
                getOfficialNameAtMoment(branch, lt, moment).let(::AlignmentName),
            )
        }
    }

    private fun getLocationTrackName(layoutContext: LayoutContext, locationTrack: LocationTrack) =
        when (locationTrack.namingScheme) {
            LocationTrackNamingScheme.WITHIN_OPERATING_POINT -> "${locationTrack.nameFreeText}"
            LocationTrackNamingScheme.UNDEFINED -> "${locationTrack.nameFreeText}"
            LocationTrackNamingScheme.TRACK_NUMBER_TRACK ->
                locationTrack.trackNumberId.let { trackNumberId ->
                    val trackNumber = trackNumberService.get(layoutContext, trackNumberId)?.number
                    "${trackNumber} ${locationTrack.nameSpecifier} ${locationTrack.nameFreeText}"
                }
            LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS ->
                locationTrack.let {
                    val alignment = alignmentDao.fetch(locationTrack.getAlignmentVersionOrThrow())
                    val switchAtStart =
                        getSwitchIdAtStart(alignment, locationTrack)?.let { id ->
                            switchDao.getOrThrow(layoutContext, id)
                        }
                    val switchAtEnd =
                        getSwitchIdAtEnd(alignment, locationTrack)?.let { id ->
                            switchDao.getOrThrow(layoutContext, id)
                        }

                    "${locationTrack.nameSpecifier} ${switchAtStart?.name}-${switchAtEnd?.name}"
                }
            LocationTrackNamingScheme.CHORD ->
                locationTrack.let {
                    val alignment = alignmentDao.fetch(locationTrack.getAlignmentVersionOrThrow())
                    val switchAtStart =
                        getSwitchIdAtStart(alignment, locationTrack)?.let { id ->
                            switchDao.getOrThrow(layoutContext, id)
                        }
                    val switchAtEnd =
                        getSwitchIdAtEnd(alignment, locationTrack)?.let { id ->
                            switchDao.getOrThrow(layoutContext, id)
                        }

                    "${switchAtStart?.name}-${switchAtEnd?.name}"
                }
        }

    private fun getOfficialNameAtMoment(branch: LayoutBranch, locationTrack: LocationTrack, moment: Instant) =
        when (locationTrack.namingScheme) {
            LocationTrackNamingScheme.WITHIN_OPERATING_POINT -> "${locationTrack.nameFreeText}"
            LocationTrackNamingScheme.UNDEFINED -> "${locationTrack.nameFreeText}"
            LocationTrackNamingScheme.TRACK_NUMBER_TRACK ->
                locationTrack.trackNumberId.let { trackNumberId ->
                    val trackNumber = trackNumberService.getOfficialAtMoment(branch, trackNumberId, moment)?.number
                    "${trackNumber} ${locationTrack.nameSpecifier} ${locationTrack.nameFreeText}"
                }
            LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS ->
                locationTrack.let {
                    val alignment = alignmentDao.fetch(locationTrack.getAlignmentVersionOrThrow())
                    val switchAtStart =
                        getSwitchIdAtStart(alignment, locationTrack)?.let { id ->
                            switchDao.getOfficialAtMoment(branch, id, moment)
                        }
                    val switchAtEnd =
                        getSwitchIdAtEnd(alignment, locationTrack)?.let { id ->
                            switchDao.getOfficialAtMoment(branch, id, moment)
                        }

                    "${locationTrack.nameSpecifier} ${switchAtStart?.name}-${switchAtEnd?.name}"
                }
            LocationTrackNamingScheme.CHORD ->
                locationTrack.let {
                    val alignment = alignmentDao.fetch(locationTrack.getAlignmentVersionOrThrow())
                    val switchAtStart =
                        getSwitchIdAtStart(alignment, locationTrack)?.let { id ->
                            switchDao.getOfficialAtMoment(branch, id, moment)
                        }
                    val switchAtEnd =
                        getSwitchIdAtEnd(alignment, locationTrack)?.let { id ->
                            switchDao.getOfficialAtMoment(branch, id, moment)
                        }

                    "${switchAtStart?.name}-${switchAtEnd?.name}"
                }
        }
}

fun collectAllSwitches(locationTrack: LocationTrack, alignment: LayoutAlignment): List<IntId<LayoutSwitch>> {
    val topologySwitches =
        listOfNotNull(locationTrack.topologyStartSwitch?.switchId, locationTrack.topologyEndSwitch?.switchId)
    val segmentSwitches = alignment.segments.mapNotNull { segment -> segment.switchId }
    return (topologySwitches + segmentSwitches).distinct()
}

data class NearbyTracks(
    val aroundStart: List<Pair<LocationTrack, LayoutAlignment>>,
    val aroundEnd: List<Pair<LocationTrack, LayoutAlignment>>,
)

fun calculateLocationTrackTopology(
    track: LocationTrack,
    alignment: LayoutAlignment,
    startChanged: Boolean = false,
    endChanged: Boolean = false,
    nearbyTracks: NearbyTracks,
    newSwitch: TopologyLinkFindingSwitch? = null,
): LocationTrack {
    val startPoint = alignment.firstSegmentStart
    val endPoint = alignment.lastSegmentEnd
    val ownSwitches = alignment.segments.mapNotNull { segment -> segment.switchId }.toSet()

    val startSwitch =
        if (!track.exists || startPoint == null) null
        else if (startChanged) {
            findBestTopologySwitchMatch(startPoint, ownSwitches, nearbyTracks.aroundStart, null, newSwitch)
        } else {
            findBestTopologySwitchMatch(
                startPoint,
                ownSwitches,
                nearbyTracks.aroundStart,
                track.topologyStartSwitch,
                newSwitch,
            )
        }

    val endSwitch =
        if (!track.exists || endPoint == null) {
            null
        } else if (endChanged) {
            findBestTopologySwitchMatch(endPoint, ownSwitches, nearbyTracks.aroundEnd, null, newSwitch)
        } else {
            findBestTopologySwitchMatch(
                endPoint,
                ownSwitches,
                nearbyTracks.aroundEnd,
                track.topologyEndSwitch,
                newSwitch,
            )
        }

    return if (track.topologyStartSwitch == startSwitch && track.topologyEndSwitch == endSwitch) {
        track
    } else if (startSwitch?.switchId != null && startSwitch.switchId == endSwitch?.switchId) {
        // Remove topology links if both ends would connect to the same switch.
        // In this case, the alignment should be part of the internal switch geometry
        track.copy(topologyStartSwitch = null, topologyEndSwitch = null)
    } else {
        track.copy(topologyStartSwitch = startSwitch, topologyEndSwitch = endSwitch)
    }
}

fun findBestTopologySwitchMatch(
    target: IPoint,
    ownSwitches: Set<DomainId<LayoutSwitch>>,
    nearbyTracksForSearch: List<Pair<LocationTrack, LayoutAlignment>>,
    currentTopologySwitch: TopologyLocationTrackSwitch?,
    newSwitch: TopologyLinkFindingSwitch?,
): TopologyLocationTrackSwitch? {
    val defaultSwitch =
        if (currentTopologySwitch?.switchId?.let(ownSwitches::contains) != false) {
            null
        } else {
            currentTopologySwitch
        }
    return findBestTopologySwitchFromSegments(target, ownSwitches, nearbyTracksForSearch, newSwitch)
        ?: defaultSwitch
        ?: findBestTopologySwitchFromOtherTopology(target, ownSwitches, nearbyTracksForSearch)
}

private fun findBestTopologySwitchFromSegments(
    target: IPoint,
    ownSwitches: Set<DomainId<LayoutSwitch>>,
    nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    newSwitch: TopologyLinkFindingSwitch?,
): TopologyLocationTrackSwitch? =
    nearbyTracks
        .flatMap { (_, otherAlignment) ->
            otherAlignment.segments.flatMap { segment ->
                if (
                    segment.switchId !is IntId ||
                        ownSwitches.contains(segment.switchId) ||
                        segment.switchId == newSwitch?.id
                ) {
                    listOf()
                } else {
                    listOfNotNull(
                        segment.startJointNumber?.let { number ->
                            pickIfClose(segment.switchId, number, target, segment.segmentStart)
                        },
                        segment.endJointNumber?.let { number ->
                            pickIfClose(segment.switchId, number, target, segment.segmentEnd)
                        },
                    )
                }
            } +
                (newSwitch?.joints?.mapNotNull { sj -> pickIfClose(newSwitch.id, sj.number, target, sj.location) }
                    ?: listOf())
        }
        .minByOrNull { (_, distance) -> distance }
        ?.first

private fun findBestTopologySwitchFromOtherTopology(
    target: IPoint,
    ownSwitches: Set<DomainId<LayoutSwitch>>,
    nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): TopologyLocationTrackSwitch? =
    nearbyTracks
        .flatMap { (otherTrack, otherAlignment) ->
            listOfNotNull(
                pickIfClose(otherTrack.topologyStartSwitch, target, otherAlignment.firstSegmentStart, ownSwitches),
                pickIfClose(otherTrack.topologyEndSwitch, target, otherAlignment.lastSegmentEnd, ownSwitches),
            )
        }
        .minByOrNull { (_, distance) -> distance }
        ?.first

private fun pickIfClose(switchId: IntId<LayoutSwitch>, number: JointNumber, target: IPoint, reference: IPoint?) =
    pickIfClose(TopologyLocationTrackSwitch(switchId, number), target, reference, setOf())

private fun pickIfClose(
    topologyMatch: TopologyLocationTrackSwitch?,
    target: IPoint,
    reference: IPoint?,
    ownSwitches: Set<DomainId<LayoutSwitch>>,
): Pair<TopologyLocationTrackSwitch, Double>? =
    if (reference != null && topologyMatch != null && !ownSwitches.contains(topologyMatch.switchId)) {
        val distance = lineLength(target, reference)
        if (distance < 1.0) topologyMatch to distance else null
    } else {
        null
    }

fun locationTrackWithAlignment(
    locationTrackDao: LocationTrackDao,
    alignmentDao: LayoutAlignmentDao,
    rowVersion: LayoutRowVersion<LocationTrack>,
) = locationTrackDao.fetch(rowVersion).let { track -> track to alignmentDao.fetch(track.getAlignmentVersionOrThrow()) }

fun filterByBoundingBox(list: List<LocationTrack>, boundingBox: BoundingBox?): List<LocationTrack> =
    if (boundingBox != null) list.filter { t -> boundingBox.intersects(t.boundingBox) } else list

fun isSplitSourceReferenceError(exception: DataIntegrityViolationException): Boolean {
    val constraint = "split_source_location_track_fkey"
    val trackIsSplitSourceTrackError = "is still referenced from table \"split\""

    return (exception.cause as? PSQLException)?.serverErrorMessage.let { msg ->
        when {
            msg == null -> false
            msg.constraint == constraint && msg.detail?.contains(trackIsSplitSourceTrackError) == true -> true

            else -> false
        }
    }
}
