package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.SplitSourceLocationTrackUpdateException
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.LocationTrackEndpoint
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.END_POINT
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.START_POINT
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.TopologyLinkFindingSwitch
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.ratko.RatkoOperatingPointDao
import fi.fta.geoviite.infra.ratko.model.OperationalPointType
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.util.FreeText
import java.time.Instant
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

const val TRACK_SEARCH_AREA_SIZE = 2.0
const val OPERATING_POINT_AROUND_SWITCH_SEARCH_AREA_SIZE = 1000.0

@GeoviiteService
class LocationTrackService(
    locationTrackDao: LocationTrackDao,
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val geocodingService: GeocodingService,
    private val switchDao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val splitDao: SplitDao,
    private val ratkoOperatingPointDao: RatkoOperatingPointDao,
    private val localizationService: LocalizationService,
    private val transactionTemplate: TransactionTemplate,
) : LayoutAssetService<LocationTrack, LocationTrackDao>(locationTrackDao) {

    @Transactional
    fun insert(
        branch: LayoutBranch,
        request: LocationTrackSaveRequest
    ): LayoutDaoResponse<LocationTrack> {
        val (alignment, alignmentVersion) = alignmentService.newEmpty()
        val locationTrack =
            LocationTrack(
                alignmentVersion = alignmentVersion,
                name = request.name,
                descriptionBase = request.descriptionBase,
                descriptionSuffix = request.descriptionSuffix,
                type = request.type,
                state = request.state,
                externalId = null,
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
                contextData = LayoutContextData.newDraft(branch),
            )
        return saveDraftInternal(branch, locationTrack)
    }

    fun update(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        request: LocationTrackSaveRequest,
    ): LayoutDaoResponse<LocationTrack> {
        try {
            return requireNotNull(
                transactionTemplate.execute { updateLocationTrackTransaction(branch, id, request) })
        } catch (dataIntegrityException: DataIntegrityViolationException) {
            throw if (isSplitSourceReferenceError(dataIntegrityException)) {
                SplitSourceLocationTrackUpdateException(request.name, dataIntegrityException)
            } else {
                dataIntegrityException
            }
        }
    }

    private fun updateLocationTrackTransaction(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        request: LocationTrackSaveRequest,
    ): LayoutDaoResponse<LocationTrack> {
        val (originalTrack, originalAlignment) = getWithAlignmentInternalOrThrow(branch.draft, id)
        val locationTrack =
            originalTrack.copy(
                name = request.name,
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
                fetchNearbyTracksAndCalculateLocationTrackTopology(
                    branch.draft, locationTrack, originalAlignment))
        } else {
            clearDuplicateReferences(branch, id)
            val segmentsWithoutSwitch = originalAlignment.segments.map(LayoutSegment::withoutSwitch)
            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
            val newTrack =
                fetchNearbyTracksAndCalculateLocationTrackTopology(
                    branch.draft, locationTrack, newAlignment)
            saveDraft(branch, newTrack, newAlignment)
        }
    }

    @Transactional
    fun updateState(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        state: LocationTrackState,
    ): LayoutDaoResponse<LocationTrack> {
        val (originalTrack, originalAlignment) = getWithAlignmentInternalOrThrow(branch.draft, id)
        val locationTrack = originalTrack.copy(state = state)

        return if (locationTrack.state != LocationTrackState.DELETED) {
            saveDraft(branch, locationTrack)
        } else {
            clearDuplicateReferences(branch, id)
            val segmentsWithoutSwitch = originalAlignment.segments.map(LayoutSegment::withoutSwitch)
            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
            val newTrack =
                fetchNearbyTracksAndCalculateLocationTrackTopology(
                    branch.draft, locationTrack, newAlignment)
            saveDraft(branch, newTrack, newAlignment)
        }
    }

    @Transactional
    override fun saveDraft(
        branch: LayoutBranch,
        draftAsset: LocationTrack
    ): LayoutDaoResponse<LocationTrack> =
        super.saveDraft(
            branch, draftAsset.copy(alignmentVersion = updatedAlignmentVersion(draftAsset)))

    private fun updatedAlignmentVersion(track: LocationTrack): RowVersion<LayoutAlignment>? =
        // If we're creating a new row or starting a draft, we duplicate the alignment to not edit
        // any original
        if (track.dataType == TEMP || track.isOfficial)
            alignmentService.duplicateOrNew(track.alignmentVersion)
        else track.alignmentVersion

    @Transactional
    fun saveDraft(
        branch: LayoutBranch,
        draftAsset: LocationTrack,
        alignment: LayoutAlignment,
    ): LayoutDaoResponse<LocationTrack> {
        val alignmentVersion =
            // If we're creating a new row or starting a draft, we duplicate the alignment to not
            // edit any original
            if (draftAsset.dataType == TEMP || draftAsset.isOfficial) {
                alignmentService.saveAsNew(alignment)
            }
            // Ensure that we update the correct one.
            else if (draftAsset.getAlignmentVersionOrThrow().id != alignment.id) {
                alignmentService.save(
                    alignment.copy(
                        id = draftAsset.getAlignmentVersionOrThrow().id, dataType = STORED),
                )
            } else {
                alignmentService.save(alignment)
            }
        return saveDraftInternal(branch, draftAsset.copy(alignmentVersion = alignmentVersion))
    }

    @Transactional
    fun updateExternalId(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        oid: Oid<LocationTrack>,
    ): LayoutDaoResponse<LocationTrack> {
        val original = dao.getOrThrow(branch.draft, id)
        return saveDraftInternal(
            branch,
            original.copy(
                externalId = oid,
                alignmentVersion = updatedAlignmentVersion(original),
            ))
    }

    @Transactional
    override fun publish(
        branch: LayoutBranch,
        version: ValidationVersion<LocationTrack>
    ): LayoutDaoResponse<LocationTrack> {
        val publishedVersion = publishInternal(branch, version.validatedAssetVersion)
        // Some of the versions may get deleted in publication -> delete any alignments they left
        // behind
        alignmentDao.deleteOrphanedAlignments()
        return publishedVersion
    }

    @Transactional
    override fun deleteDraft(
        branch: LayoutBranch,
        id: IntId<LocationTrack>
    ): LayoutDaoResponse<LocationTrack> {
        val draft = dao.getOrThrow(branch.draft, id)
        // If removal also breaks references, clear them out first
        if (draft.contextData.officialRowId == null) {
            clearDuplicateReferences(branch, id)
        }
        val deletedVersion = super.deleteDraft(branch, id)
        draft.alignmentVersion?.id?.let(alignmentDao::delete)
        return deletedVersion
    }

    @Transactional
    fun fetchDuplicates(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>
    ): List<LocationTrack> {
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
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        names: List<AlignmentName>,
    ): List<LocationTrack> {
        return dao.list(layoutContext, true, trackNumberId, names)
    }

    override fun idMatches(term: String, item: LocationTrack) =
        item.externalId.toString() == term || item.id.toString() == term

    override fun contentMatches(term: String, item: LocationTrack) =
        item.exists && (item.name.contains(term, true) || item.descriptionBase.contains(term, true))

    fun listNear(layoutContext: LayoutContext, bbox: BoundingBox): List<LocationTrack> {
        return dao.listNear(layoutContext, bbox).filter(LocationTrack::exists)
    }

    @Transactional(readOnly = true)
    fun listWithAlignments(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        includeDeleted: Boolean = false,
        boundingBox: BoundingBox? = null,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return dao.list(layoutContext, includeDeleted, trackNumberId)
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
    fun getWithAlignment(
        version: LayoutDaoResponse<LocationTrack>
    ): Pair<LocationTrack, LayoutAlignment> {
        return getWithAlignmentInternal(version.rowVersion)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(
        version: LayoutRowVersion<LocationTrack>
    ): Pair<LocationTrack, LayoutAlignment> {
        return getWithAlignmentInternal(version)
    }

    @Transactional(readOnly = true)
    fun listNearWithAlignments(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return dao.listNear(layoutContext, bbox).let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun getLocationTracksNear(
        layoutContext: LayoutContext,
        location: IPoint,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        val searchArea =
            BoundingBox(
                    Point(0.0, 0.0),
                    Point(TRACK_SEARCH_AREA_SIZE, TRACK_SEARCH_AREA_SIZE),
                )
                .centerAt(location)
        return listNearWithAlignments(layoutContext, searchArea).filter { (_, alignment) ->
            alignment.segments.any { segment ->
                searchArea.intersects(segment.boundingBox) &&
                    segment.segmentPoints.any(searchArea::contains)
            }
        }
    }

    @Transactional(readOnly = true)
    fun getMetadataSections(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection> {
        val locationTrack = get(layoutContext, locationTrackId)
        val geocodingContext =
            locationTrack?.let {
                geocodingService.getGeocodingContext(layoutContext, locationTrack.trackNumberId)
            }

        return if (geocodingContext != null && locationTrack.alignmentVersion != null) {
            alignmentService.getGeometryMetadataSections(
                locationTrack.alignmentVersion,
                locationTrack.externalId,
                boundingBox,
                geocodingContext,
            )
        } else listOf()
    }

    private fun getSwitchIdAtStart(alignment: LayoutAlignment, locationTrack: LocationTrack) =
        if (alignment.segments.firstOrNull()?.startJointNumber == null)
            locationTrack.topologyStartSwitch?.switchId
        else alignment.segments.firstOrNull()?.switchId as IntId?

    private fun getSwitchIdAtEnd(alignment: LayoutAlignment, locationTrack: LocationTrack) =
        if (alignment.segments.lastOrNull()?.endJointNumber == null)
            locationTrack.topologyEndSwitch?.switchId
        else alignment.segments.lastOrNull()?.switchId as IntId?

    @Transactional(readOnly = true)
    fun getFullDescription(
        layoutContext: LayoutContext,
        locationTrack: LocationTrack,
        lang: LocalizationLanguage
    ): FreeText {
        val alignmentVersion = locationTrack.alignmentVersion
        val (startSwitch, endSwitch) =
            alignmentVersion?.let {
                val alignment = alignmentDao.fetch(alignmentVersion)
                getSwitchIdAtStart(alignment, locationTrack) to
                    getSwitchIdAtEnd(alignment, locationTrack)
            } ?: (null to null)

        fun getSwitchShortName(switchId: IntId<TrackLayoutSwitch>) =
            switchDao.get(layoutContext, switchId)?.shortName

        val startSwitchName = startSwitch?.let(::getSwitchShortName)
        val endSwitchName = endSwitch?.let(::getSwitchShortName)
        val translation = localizationService.getLocalization(lang)

        return when (locationTrack.descriptionSuffix) {
            DescriptionSuffixType.NONE -> locationTrack.descriptionBase

            DescriptionSuffixType.SWITCH_TO_BUFFER ->
                FreeText(
                    "${locationTrack.descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - ${translation.t("location-track-dialog.buffer")}")

            DescriptionSuffixType.SWITCH_TO_SWITCH ->
                FreeText(
                    "${locationTrack.descriptionBase} ${startSwitchName ?: "???"} - ${endSwitchName ?: "???"}")

            DescriptionSuffixType.SWITCH_TO_OWNERSHIP_BOUNDARY ->
                FreeText(
                    "${locationTrack.descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - ${translation.t("location-track-dialog.ownership-boundary")}")
        }
    }

    private fun getWithAlignmentInternalOrThrow(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, LayoutAlignment> {
        return getWithAlignmentInternal(dao.fetchVersionOrThrow(layoutContext, id))
    }

    private fun getWithAlignmentInternal(
        version: LayoutRowVersion<LocationTrack>
    ): Pair<LocationTrack, LayoutAlignment> = locationTrackWithAlignment(dao, alignmentDao, version)

    private fun associateWithAlignments(
        lines: List<LocationTrack>
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        // This is a little convoluted to avoid extra passes of transaction annotation handling in
        // alignmentDao.fetch
        val alignments =
            alignmentDao.fetchMany(lines.map(LocationTrack::getAlignmentVersionOrThrow))
        return lines.map { line -> line to alignments.getValue(line.getAlignmentVersionOrThrow()) }
    }

    fun fillTrackAddress(splitPoint: SplitPoint, geocodingContext: GeocodingContext): SplitPoint {
        val address = geocodingContext.getAddress(splitPoint.location)?.first
        return when (splitPoint) {
            is SwitchSplitPoint -> splitPoint.copy(address = address)
            is EndpointSplitPoint -> splitPoint.copy(address = address)
        }
    }

    fun fillTrackAddresses(
        duplicates: List<LocationTrackDuplicate>,
        geocodingContext: GeocodingContext
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
                            }))
        }
    }

    @Transactional(readOnly = true)
    fun getInfoboxExtras(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>
    ): LocationTrackInfoboxExtras? {
        return getWithAlignment(layoutContext, id)?.let { (locationTrack, alignment) ->
            val start = alignment.start ?: return null
            val end = alignment.end ?: return null
            val geocodingContext =
                geocodingService.getGeocodingContext(layoutContext, locationTrack.trackNumberId)
                    ?: return null

            val duplicateOf = getDuplicateTrackParent(layoutContext, locationTrack)
            val duplicates =
                fillTrackAddresses(
                    getLocationTrackDuplicates(layoutContext, locationTrack, alignment),
                    geocodingContext)
            val sortedDuplicates =
                duplicates.sortedBy { duplicate ->
                    duplicate.duplicateStatus.startSplitPoint?.address
                }

            val startAddress = geocodingContext?.getAddress(start)?.first
            val startSwitchId =
                alignment.segments.firstOrNull()?.switchId as IntId?
                    ?: locationTrack.topologyStartSwitch?.switchId
            val startSplitPoint =
                if (startSwitchId != null)
                    SwitchSplitPoint(start, startAddress, startSwitchId, JointNumber(0))
                else EndpointSplitPoint(start, startAddress, DuplicateEndPointType.START)

            val endAddress = geocodingContext?.getAddress(end)?.first
            val endSwitchId =
                alignment.segments.lastOrNull()?.switchId as IntId?
                    ?: locationTrack.topologyEndSwitch?.switchId
            val endSplitPoint =
                if (endSwitchId != null)
                    SwitchSplitPoint(end, endAddress, endSwitchId, JointNumber(0))
                else EndpointSplitPoint(end, endAddress, DuplicateEndPointType.END)

            val startSwitch =
                (alignment.segments.firstOrNull()?.switchId as IntId?
                        ?: locationTrack.topologyStartSwitch?.switchId)
                    ?.let { id -> fetchSwitchAtEndById(layoutContext, id) }
            val endSwitch =
                (alignment.segments.lastOrNull()?.switchId as IntId?
                        ?: locationTrack.topologyEndSwitch?.switchId)
                    ?.let { id -> fetchSwitchAtEndById(layoutContext, id) }
            val partOfUnfinishedSplit =
                splitDao
                    .locationTracksPartOfAnyUnfinishedSplit(layoutContext.branch, listOf(id))
                    .isNotEmpty()

            LocationTrackInfoboxExtras(
                duplicateOf,
                sortedDuplicates,
                startSwitch,
                endSwitch,
                partOfUnfinishedSplit,
                startSplitPoint,
                endSplitPoint)
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
                listOfNotNull(
                    locationTrack.topologyStartSwitch?.switchId,
                    locationTrack.topologyEndSwitch?.switchId,
                ) +
                switchDao.findSwitchesNearAlignment(
                    branch, locationTrack.getAlignmentVersionOrThrow()))
            .distinct()
            .size

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
                .map(LayoutSwitchDao.LocationTrackIdentifiers::rowVersion)
        val duplicateTracksAndAlignments =
            (markedDuplicateVersions + tracksLinkedThroughSwitch)
                .distinct()
                .map(::getWithAlignmentInternal)
                .filter { (duplicateTrack, _) ->
                    duplicateTrack.id != track.id && duplicateTrack.id != track.duplicateOf
                }
        return getLocationTrackDuplicatesBySplitPoints(
            track, alignment, duplicateTracksAndAlignments)
    }

    private fun getDuplicateTrackParent(
        layoutContext: LayoutContext,
        childTrack: LocationTrack,
    ): LocationTrackDuplicate? =
        childTrack.duplicateOf?.let { parentId ->
            getWithAlignment(layoutContext, parentId)?.let { (parentTrack, parentTrackAlignment) ->
                val childAlignment = alignmentDao.fetch(childTrack.getAlignmentVersionOrThrow())
                getDuplicateTrackParentStatus(
                    parentTrack, parentTrackAlignment, childTrack, childAlignment)
            }
        }

    private fun fetchSwitchAtEndById(
        layoutContext: LayoutContext,
        id: IntId<TrackLayoutSwitch>
    ): LayoutSwitchIdAndName? =
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
                ?.filter { (nearbyLocationTrack, _) -> nearbyLocationTrack.id != track.id }
                ?: listOf()

        val nearbyTracksAroundEnd =
            alignment.end
                ?.let { point -> fetchNearbyLocationTracksWithAlignments(layoutContext, point) }
                ?.filter { (nearbyLocationTrack, _) -> nearbyLocationTrack.id != track.id }
                ?: listOf()

        return calculateLocationTrackTopology(
            track = track,
            alignment = alignment,
            startChanged = startChanged,
            endChanged = endChanged,
            nearbyTracks =
                NearbyTracks(
                    aroundStart = nearbyTracksAroundStart,
                    aroundEnd = nearbyTracksAroundEnd,
                ),
        )
    }

    @Transactional(readOnly = true)
    fun getLocationTrackEndpoints(
        layoutContext: LayoutContext,
        bbox: BoundingBox
    ): List<LocationTrackEndpoint> {
        return getLocationTrackEndpoints(listWithAlignments(layoutContext), bbox)
    }

    fun getLocationTrackOwners(): List<LocationTrackOwner> {
        return dao.fetchLocationTrackOwners()
    }

    @Transactional(readOnly = true)
    fun getSplittingInitializationParameters(
        layoutContext: LayoutContext,
        trackId: IntId<LocationTrack>,
    ): SplittingInitializationParameters? {
        return getWithAlignment(layoutContext, trackId)?.let { (locationTrack, alignment) ->
            val switches =
                getSwitchesForLocationTrack(layoutContext, trackId)
                    .mapNotNull { id -> switchDao.get(layoutContext, id) }
                    .mapNotNull { switch ->
                        switchLibraryService.getSwitchStructure(switch.switchStructureId).let {
                            structure ->
                            val presentationJointLocation =
                                switch.getJoint(structure.presentationJointNumber)?.location
                            if (presentationJointLocation != null) {
                                switch to presentationJointLocation
                            } else {
                                null
                            }
                        }
                    }
                    .map { (switch, location) ->
                        val address =
                            geocodingService
                                .getGeocodingContext(layoutContext, locationTrack.trackNumberId)
                                ?.getAddressAndM(location)
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
                getLocationTrackDuplicates(layoutContext, locationTrack, alignment).mapNotNull {
                    duplicate ->
                    getWithAlignmentOrThrow(layoutContext, duplicate.id)
                        .let { (dupe, alignment) ->
                            geocodingService.getLocationTrackStartAndEnd(
                                layoutContext, dupe, alignment)
                        }
                        ?.let { (start, end) ->
                            if (start != null && end != null) {
                                SplitDuplicateTrack(
                                    duplicate.id,
                                    duplicate.name,
                                    start,
                                    end,
                                    duplicate.duplicateStatus)
                            } else {
                                null
                            }
                        }
                }

            SplittingInitializationParameters(trackId, switches, duplicateTracks)
        }
    }

    private fun getNearestOperatingPoint(location: Point) =
        ratkoOperatingPointDao
            .getOperatingPoints(
                boundingBoxAroundPoint(location, OPERATING_POINT_AROUND_SWITCH_SEARCH_AREA_SIZE))
            .filter { op ->
                op.type == OperationalPointType.LPO || op.type == OperationalPointType.LP
            }
            .minByOrNull { operatingPoint -> lineLength(operatingPoint.location, location) }

    @Transactional(readOnly = true)
    fun getSwitchesForLocationTrack(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
    ): List<IntId<TrackLayoutSwitch>> {
        return getWithAlignment(layoutContext, locationTrackId)?.let { (track, alignment) ->
            collectAllSwitches(track, alignment)
        } ?: emptyList()
    }

    @Transactional(readOnly = true)
    fun getAlignmentsForTracks(
        tracks: List<LocationTrack>
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return tracks.map { track ->
            val alignment =
                track.alignmentVersion?.let(alignmentDao::fetch)
                    ?: error(
                        "All location tracks should have an alignment. Alignment was not found for track=${track.id}")
            track to alignment
        }
    }

    override fun mergeToMainBranch(
        fromBranch: DesignBranch,
        id: IntId<LocationTrack>
    ): LayoutDaoResponse<LocationTrack> {
        val (versions, track) = fetchAndCheckVersionsForMerging(fromBranch, id)
        return mergeToMainBranchInternal(
            versions,
            track.copy(
                alignmentVersion = alignmentService.duplicate(track.getAlignmentVersionOrThrow())),
        )
    }
}

fun collectAllSwitches(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment
): List<IntId<TrackLayoutSwitch>> {
    val topologySwitches =
        listOfNotNull(
            locationTrack.topologyStartSwitch?.switchId, locationTrack.topologyEndSwitch?.switchId)
    val segmentSwitches = alignment.segments.mapNotNull { segment -> segment.switchId as IntId? }
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
            findBestTopologySwitchMatch(
                startPoint, ownSwitches, nearbyTracks.aroundStart, null, newSwitch)
        } else {
            findBestTopologySwitchMatch(
                startPoint,
                ownSwitches,
                nearbyTracks.aroundStart,
                track.topologyStartSwitch,
                newSwitch)
        }

    val endSwitch =
        if (!track.exists || endPoint == null) {
            null
        } else if (endChanged) {
            findBestTopologySwitchMatch(
                endPoint, ownSwitches, nearbyTracks.aroundEnd, null, newSwitch)
        } else {
            findBestTopologySwitchMatch(
                endPoint, ownSwitches, nearbyTracks.aroundEnd, track.topologyEndSwitch, newSwitch)
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
    ownSwitches: Set<DomainId<TrackLayoutSwitch>>,
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
    ownSwitches: Set<DomainId<TrackLayoutSwitch>>,
    nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    newSwitch: TopologyLinkFindingSwitch?,
): TopologyLocationTrackSwitch? =
    nearbyTracks
        .flatMap { (_, otherAlignment) ->
            otherAlignment.segments.flatMap { segment ->
                if (segment.switchId !is IntId ||
                    ownSwitches.contains(segment.switchId) ||
                    segment.switchId == newSwitch?.id) {
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
                (newSwitch?.joints?.mapNotNull { sj ->
                    pickIfClose(newSwitch.id, sj.number, target, sj.location)
                } ?: listOf())
        }
        .minByOrNull { (_, distance) -> distance }
        ?.first

private fun findBestTopologySwitchFromOtherTopology(
    target: IPoint,
    ownSwitches: Set<DomainId<TrackLayoutSwitch>>,
    nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): TopologyLocationTrackSwitch? =
    nearbyTracks
        .flatMap { (otherTrack, otherAlignment) ->
            listOfNotNull(
                pickIfClose(
                    otherTrack.topologyStartSwitch,
                    target,
                    otherAlignment.firstSegmentStart,
                    ownSwitches),
                pickIfClose(
                    otherTrack.topologyEndSwitch,
                    target,
                    otherAlignment.lastSegmentEnd,
                    ownSwitches),
            )
        }
        .minByOrNull { (_, distance) -> distance }
        ?.first

private fun pickIfClose(
    switchId: IntId<TrackLayoutSwitch>,
    number: JointNumber,
    target: IPoint,
    reference: IPoint?,
) = pickIfClose(TopologyLocationTrackSwitch(switchId, number), target, reference, setOf())

private fun pickIfClose(
    topologyMatch: TopologyLocationTrackSwitch?,
    target: IPoint,
    reference: IPoint?,
    ownSwitches: Set<DomainId<TrackLayoutSwitch>>,
): Pair<TopologyLocationTrackSwitch, Double>? =
    if (reference != null &&
        topologyMatch != null &&
        !ownSwitches.contains(topologyMatch.switchId)) {
        val distance = lineLength(target, reference)
        if (distance < 1.0) topologyMatch to distance else null
    } else {
        null
    }

fun getLocationTrackEndpoints(
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    bbox: BoundingBox,
): List<LocationTrackEndpoint> =
    locationTracks.flatMap { (locationTrack, alignment) ->
        val trackId = locationTrack.id as IntId
        listOfNotNull(
            alignment.firstSegmentStart?.takeIf(bbox::contains)?.let { p ->
                LocationTrackEndpoint(trackId, p.toPoint(), START_POINT)
            },
            alignment.lastSegmentEnd?.takeIf(bbox::contains)?.let { p ->
                LocationTrackEndpoint(trackId, p.toPoint(), END_POINT)
            },
        )
    }

fun locationTrackWithAlignment(
    locationTrackDao: LocationTrackDao,
    alignmentDao: LayoutAlignmentDao,
    rowVersion: LayoutRowVersion<LocationTrack>,
) =
    locationTrackDao.fetch(rowVersion).let { track ->
        track to alignmentDao.fetch(track.getAlignmentVersionOrThrow())
    }

fun filterByBoundingBox(list: List<LocationTrack>, boundingBox: BoundingBox?): List<LocationTrack> =
    if (boundingBox != null) list.filter { t -> boundingBox.intersects(t.boundingBox) } else list

fun isSplitSourceReferenceError(exception: DataIntegrityViolationException): Boolean {
    val constraint = "split_source_location_track_fkey"
    val trackIsSplitSourceTrackError = "is still referenced from table \"split\""

    return (exception.cause as? PSQLException)?.serverErrorMessage.let { msg ->
        when {
            msg == null -> false
            msg.constraint == constraint &&
                msg.detail?.contains(trackIsSplitSourceTrackError) == true -> true

            else -> false
        }
    }
}
