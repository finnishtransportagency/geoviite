package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.LocationTrackEndpoint
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.END_POINT
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.START_POINT
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.TopologyLinkFindingSwitch
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.logging.serviceCall
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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

const val TRACK_SEARCH_AREA_SIZE = 2.0
const val OPERATING_POINT_AROUND_SWITCH_SEARCH_AREA_SIZE = 1000.0

@Service
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
) : LayoutAssetService<LocationTrack, LocationTrackDao>(locationTrackDao) {

    @Transactional
    fun insert(request: LocationTrackSaveRequest): DaoResponse<LocationTrack> {
        logger.serviceCall("insert", "request" to request)
        val (alignment, alignmentVersion) = alignmentService.newEmpty()
        val locationTrack = LocationTrack(
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
            contextData = LayoutContextData.newDraft(),
        )
        return saveDraftInternal(locationTrack)
    }

    @Transactional
    fun update(id: IntId<LocationTrack>, request: LocationTrackSaveRequest): DaoResponse<LocationTrack> {
        logger.serviceCall("update", "id" to id, "request" to request)
        val (originalTrack, originalAlignment) = getWithAlignmentInternalOrThrow(DRAFT, id)
        val locationTrack = originalTrack.copy(
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
            saveDraft(fetchNearbyTracksAndCalculateLocationTrackTopology(locationTrack, originalAlignment))
        } else {
            clearDuplicateReferences(id)
            val segmentsWithoutSwitch = originalAlignment.segments.map(LayoutSegment::withoutSwitch)
            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
            saveDraft(fetchNearbyTracksAndCalculateLocationTrackTopology(locationTrack, newAlignment), newAlignment)
        }
    }

    @Transactional
    fun updateState(id: IntId<LocationTrack>, state: LocationTrackState): DaoResponse<LocationTrack> {
        logger.serviceCall("update", "id" to id, "state" to state)
        val (originalTrack, originalAlignment) = getWithAlignmentInternalOrThrow(DRAFT, id)
        val locationTrack = originalTrack.copy(state = state)

        return if (locationTrack.state != LocationTrackState.DELETED) {
            saveDraft(locationTrack)
        } else {
            clearDuplicateReferences(id)
            val segmentsWithoutSwitch = originalAlignment.segments.map(LayoutSegment::withoutSwitch)
            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
            saveDraft(fetchNearbyTracksAndCalculateLocationTrackTopology(locationTrack, newAlignment), newAlignment)
        }
    }

    @Transactional
    override fun saveDraft(draft: LocationTrack): DaoResponse<LocationTrack> =
        super.saveDraft(draft.copy(alignmentVersion = updatedAlignmentVersion(draft)))

    private fun updatedAlignmentVersion(track: LocationTrack): RowVersion<LayoutAlignment>? =
        // If we're creating a new row or starting a draft, we duplicate the alignment to not edit any original
        if (track.dataType == TEMP || track.isOfficial) alignmentService.duplicateOrNew(track.alignmentVersion)
        else track.alignmentVersion

    @Transactional
    fun saveDraft(draft: LocationTrack, alignment: LayoutAlignment): DaoResponse<LocationTrack> {
        logger.serviceCall("save", "locationTrack" to draft, "alignment" to alignment)
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

    @Transactional
    fun updateExternalId(id: IntId<LocationTrack>, oid: Oid<LocationTrack>): DaoResponse<LocationTrack> {
        logger.serviceCall("updateExternalIdForLocationTrack", "id" to id, "oid" to oid)
        val original = dao.getOrThrow(DRAFT, id)
        return saveDraftInternal(
            original.copy(
                externalId = oid,
                alignmentVersion = updatedAlignmentVersion(original),
            )
        )
    }

    @Transactional
    override fun publish(version: ValidationVersion<LocationTrack>): DaoResponse<LocationTrack> {
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
    override fun deleteDraft(id: IntId<LocationTrack>): DaoResponse<LocationTrack> {
        val draft = dao.getOrThrow(DRAFT, id)
        // If removal also breaks references, clear them out first
        if (draft.contextData.officialRowId == null) {
            clearDuplicateReferences(id)
        }
        val deletedVersion = super.deleteDraft(id)
        draft.alignmentVersion?.id?.let(alignmentDao::delete)
        return deletedVersion
    }

    @Transactional
    fun fetchDuplicates(publicationState: PublicationState, id: IntId<LocationTrack>): List<LocationTrack> {
        logger.serviceCall(
            "fetchDuplicates",
            "publicationState" to publicationState,
            "locationTrackId" to id,
        )
        return dao.fetchDuplicateVersions(id, publicationState).map(dao::fetch)
    }

    @Transactional
    fun clearDuplicateReferences(id: IntId<LocationTrack>) = dao
        .fetchDuplicateVersions(id, DRAFT, includeDeleted = true)
        .map(dao::fetch)
        .map(::asMainDraft)
        .forEach { duplicate -> saveDraft(duplicate.copy(duplicateOf = null)) }

    fun listNonLinked(): List<LocationTrack> {
        logger.serviceCall("listNonLinked")
        return dao.list(DRAFT, false).filter { a -> a.segmentCount == 0 }
    }

    fun list(publicationState: PublicationState, bbox: BoundingBox): List<LocationTrack> {
        logger.serviceCall("list", "publicationState" to publicationState, "bbox" to bbox)
        return dao.list(publicationState, false).filter { tn -> bbox.intersects(tn.boundingBox) }
    }

    fun list(
        publicationState: PublicationState,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        names: List<AlignmentName>,
    ): List<LocationTrack> {
        logger.serviceCall(
            "list", "publicationState" to publicationState, "trackNumberId" to trackNumberId, "names" to names
        )
        return dao.list(publicationState, true, trackNumberId, names)
    }

    override fun idMatches(term: String, item: LocationTrack) =
        item.externalId.toString() == term || item.id.toString() == term

    override fun contentMatches(term: String, item: LocationTrack) =
        item.exists && (item.name.contains(term, true) || item.descriptionBase.contains(term, true))

    fun listNear(publicationState: PublicationState, bbox: BoundingBox): List<LocationTrack> {
        logger.serviceCall("listNear", "publicationState" to publicationState, "bbox" to bbox)
        return dao.listNear(publicationState, bbox).filter(LocationTrack::exists)
    }

    @Transactional(readOnly = true)
    fun listWithAlignments(
        publicationState: PublicationState,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        includeDeleted: Boolean = false,
        boundingBox: BoundingBox? = null,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall(
            "listWithAlignments",
            "publicationState" to publicationState,
            "trackNumberId" to trackNumberId,
            "includeDeleted" to includeDeleted,
        )
        return dao
            .list(publicationState, includeDeleted, trackNumberId)
            .let { list -> filterByBoundingBox(list, boundingBox) }
            .let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun getManyWithAlignments(
        publicationState: PublicationState,
        ids: List<IntId<LocationTrack>>,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall("getManyWithAlignments", "publicationState" to publicationState, "ids" to ids)
        return dao.getMany(publicationState, ids).let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun getWithAlignmentOrThrow(
        publicationState: PublicationState,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "publicationState" to publicationState, "id" to id)
        return getWithAlignmentInternalOrThrow(publicationState, id)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(publicationState: PublicationState, id: IntId<LocationTrack>): Pair<LocationTrack, LayoutAlignment>? {
        logger.serviceCall("getWithAlignment", "publicationState" to publicationState, "id" to id)
        return dao.fetchVersion(id, publicationState)?.let(::getWithAlignmentInternal)
    }

    @Transactional(readOnly = true)
    fun getTrackPoint(
        publicationState: PublicationState,
        locationTrackId: IntId<LocationTrack>,
        address: TrackMeter,
    ): AddressPoint? {
        logger.serviceCall(
            "getTrackPoint",
            "publicationState" to publicationState, "locationTrackId" to locationTrackId, "address" to address,
        )
        val locationTrackAndAlignment = getWithAlignment(publicationState, locationTrackId)
        return locationTrackAndAlignment?.let { (locationTrack, alignment) ->
            geocodingService.getTrackLocation(locationTrack, alignment, address, publicationState)
        }
    }

    @Transactional(readOnly = true)
    fun getOfficialWithAlignmentAtMoment(
        id: IntId<LocationTrack>,
        moment: Instant,
    ): Pair<LocationTrack, LayoutAlignment>? {
        logger.serviceCall("getOfficialWithAlignmentAtMoment", "id" to id, "moment" to moment)
        return dao.fetchOfficialVersionAtMoment(id, moment)?.let(::getWithAlignmentInternal)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(version: RowVersion<LocationTrack>): Pair<LocationTrack, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "version" to version)
        return getWithAlignmentInternal(version)
    }

    @Transactional(readOnly = true)
    fun listNearWithAlignments(
        publicationState: PublicationState,
        bbox: BoundingBox,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall(
            "listNearWithAlignments", "publicationState" to publicationState, "bbox" to bbox
        )
        return dao.listNear(publicationState, bbox).let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun getLocationTracksNear(
        location: IPoint,
        publicationState: PublicationState,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall("getLocationTracksNear", "location" to location)
        val searchArea = BoundingBox(
            Point(0.0, 0.0),
            Point(TRACK_SEARCH_AREA_SIZE, TRACK_SEARCH_AREA_SIZE),
        ).centerAt(location)
        return listNearWithAlignments(publicationState, searchArea).filter { (_, alignment) ->
            alignment.segments.any { segment ->
                searchArea.intersects(segment.boundingBox) && segment.segmentPoints.any(searchArea::contains)
            }
        }
    }

    @Transactional(readOnly = true)
    fun getMetadataSections(
        locationTrackId: IntId<LocationTrack>,
        publicationState: PublicationState,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection> {
        logger.serviceCall(
            "getSectionsByPlan",
            "locationTrackId" to locationTrackId,
            "publicationState" to publicationState,
            "boundingBox" to boundingBox
        )
        val locationTrack = get(publicationState, locationTrackId)
        val geocodingContext = locationTrack?.let {
            geocodingService.getGeocodingContext(publicationState, locationTrack.trackNumberId)
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
        if (alignment.segments.firstOrNull()?.startJointNumber == null) locationTrack.topologyStartSwitch?.switchId
        else alignment.segments.firstOrNull()?.switchId as IntId?

    private fun getSwitchIdAtEnd(alignment: LayoutAlignment, locationTrack: LocationTrack) =
        if (alignment.segments.lastOrNull()?.endJointNumber == null) locationTrack.topologyEndSwitch?.switchId
        else alignment.segments.lastOrNull()?.switchId as IntId?

    @Transactional(readOnly = true)
    fun getFullDescription(publicationState: PublicationState, locationTrack: LocationTrack, lang: String): FreeText {
        val alignmentVersion = locationTrack.alignmentVersion
        val (startSwitch, endSwitch) = alignmentVersion?.let {
            val alignment = alignmentDao.fetch(alignmentVersion)
            getSwitchIdAtStart(alignment, locationTrack) to getSwitchIdAtEnd(
                alignment, locationTrack
            )
        } ?: (null to null)

        fun getSwitchShortName(switchId: IntId<TrackLayoutSwitch>) =
            switchDao.fetchVersion(switchId, publicationState)?.let(switchDao::fetch)?.shortName

        val startSwitchName = startSwitch?.let(::getSwitchShortName)
        val endSwitchName = endSwitch?.let(::getSwitchShortName)
        val translation = localizationService.getLocalization(lang)

        return when (locationTrack.descriptionSuffix) {
            DescriptionSuffixType.NONE -> locationTrack.descriptionBase

            DescriptionSuffixType.SWITCH_TO_BUFFER -> FreeText(
                "${locationTrack.descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - ${translation.t("location-track-dialog.buffer")}"
            )

            DescriptionSuffixType.SWITCH_TO_SWITCH -> FreeText(
                "${locationTrack.descriptionBase} ${startSwitchName ?: "???"} - ${endSwitchName ?: "???"}"
            )

            DescriptionSuffixType.SWITCH_TO_OWNERSHIP_BOUNDARY -> FreeText(
                "${locationTrack.descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - ${translation.t("location-track-dialog.ownership-boundary")}"
            )
        }
    }

    private fun getWithAlignmentInternalOrThrow(publicationState: PublicationState, id: IntId<LocationTrack>) =
        getWithAlignmentInternal(dao.fetchVersionOrThrow(id, publicationState))

    private fun getWithAlignmentInternal(version: RowVersion<LocationTrack>): Pair<LocationTrack, LayoutAlignment> =
        locationTrackWithAlignment(dao, alignmentDao, version)

    private fun associateWithAlignments(lines: List<LocationTrack>): List<Pair<LocationTrack, LayoutAlignment>> {
        // This is a little convoluted to avoid extra passes of transaction annotation handling in alignmentDao.fetch
        val alignments = alignmentDao.fetchMany(lines.map(LocationTrack::getAlignmentVersionOrThrow))
        return lines.map { line -> line to alignments.getValue(line.getAlignmentVersionOrThrow()) }
    }

    fun sortDuplicatesByTrackAddress(publicationState: PublicationState, trackNumberId: IntId<TrackLayoutTrackNumber>, duplicates: List<LocationTrackDuplicate>): List<LocationTrackDuplicate> {
        val geocodingContext = geocodingService.getGeocodingContext(publicationState, trackNumberId)
            ?: throw Exception("Failed to create geocoding context for track number $trackNumberId")
        return duplicates.sortedBy { duplicate ->
            val address = duplicate.duplicateStatus.startPoint?.let { start ->
                geocodingContext.getAddress(start)
            }
            address?.first
        }
    }

    @Transactional(readOnly = true)
    fun getInfoboxExtras(publicationState: PublicationState, id: IntId<LocationTrack>): LocationTrackInfoboxExtras? {
        val locationTrackAndAlignment = getWithAlignment(publicationState, id)
        return locationTrackAndAlignment?.let { (locationTrack, alignment) ->
            val duplicateOf = getDuplicateTrackParent(locationTrack, publicationState)
            val sortedDuplicates = sortDuplicatesByTrackAddress(
                publicationState, locationTrack.trackNumberId, getLocationTrackDuplicates(locationTrack, alignment, publicationState)
            )
            val startSwitch = (alignment.segments.firstOrNull()?.switchId as IntId?
                ?: locationTrack.topologyStartSwitch?.switchId)?.let { id -> fetchSwitchAtEndById(id, publicationState) }
            val endSwitch = (alignment.segments.lastOrNull()?.switchId as IntId?
                ?: locationTrack.topologyEndSwitch?.switchId)?.let { id -> fetchSwitchAtEndById(id, publicationState) }
            val partOfUnfinishedSplit = splitDao.locationTracksPartOfAnyUnfinishedSplit(listOf(id)).isNotEmpty()

            LocationTrackInfoboxExtras(
                duplicateOf,
                sortedDuplicates,
                startSwitch,
                endSwitch,
                partOfUnfinishedSplit,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getRelinkableSwitchesCount(publicationState: PublicationState, id: IntId<LocationTrack>): Int? {
        val locationTrack = get(publicationState, id)
        return locationTrack?.alignmentVersion?.let { alignmentVersion ->
            countRelinkableSwitches(locationTrack, alignmentVersion, alignmentDao.fetch(alignmentVersion))
        }
    }

    private fun countRelinkableSwitches(
        locationTrack: LocationTrack,
        alignmentVersion: RowVersion<LayoutAlignment>,
        alignment: LayoutAlignment,
    ): Int =
        (alignment.segments.mapNotNull { it.switchId } + listOfNotNull(
            locationTrack.topologyStartSwitch?.switchId,
            locationTrack.topologyEndSwitch?.switchId,
        ) + switchDao.findSwitchesNearAlignment(alignmentVersion)).distinct().size

    @Transactional(readOnly = true)
    fun getLocationTrackDuplicates(
        track: LocationTrack,
        alignment: LayoutAlignment,
        publicationState: PublicationState,
    ): List<LocationTrackDuplicate> {
        val markedDuplicateVersions = dao.fetchDuplicateVersions(track.id as IntId, publicationState)
        val tracksLinkedThroughSwitch = switchDao
            .findLocationTracksLinkedToSwitches(publicationState, track.switchIds)
            .map(LayoutSwitchDao.LocationTrackIdentifiers::rowVersion)
        val duplicateTracksAndAlignments = (markedDuplicateVersions + tracksLinkedThroughSwitch)
            .distinct()
            .map(::getWithAlignmentInternal)
            .filter { (duplicateTrack, _) -> duplicateTrack.id != track.id && duplicateTrack.id != track.duplicateOf }
        return getLocationTrackDuplicatesByJoint(track, alignment, duplicateTracksAndAlignments)
    }

    private fun getDuplicateTrackParent(
        childTrack: LocationTrack,
        publicationState: PublicationState,
    ): LocationTrackDuplicate? = childTrack.duplicateOf?.let { parentId ->
        getWithAlignment(publicationState, parentId)?.let { (parentTrack, parentTrackAlignment) ->
            val childAlignment = alignmentDao.fetch(childTrack.getAlignmentVersionOrThrow())
            getDuplicateTrackParentStatus(parentTrack, parentTrackAlignment, childTrack, childAlignment)
        }
    }

    private fun fetchSwitchAtEndById(id: IntId<TrackLayoutSwitch>, publicationState: PublicationState) =
        switchDao.fetchVersion(id, publicationState)
            ?.let(switchDao::fetch)?.let { switch -> LayoutSwitchIdAndName(id, switch.name) }

    fun fetchNearbyLocationTracksWithAlignments(
        targetPoint: LayoutPoint,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return dao
            .fetchVersionsNear(DRAFT, boundingBoxAroundPoint(targetPoint, 1.0))
            .map { version -> getWithAlignmentInternal(version) }
            .filter { (track, alignment) ->
                alignment.segments.isNotEmpty() && track.exists
            }
    }

    @Transactional
    fun fetchNearbyTracksAndCalculateLocationTrackTopology(
        track: LocationTrack,
        alignment: LayoutAlignment,
        startChanged: Boolean = false,
        endChanged: Boolean = false,
    ): LocationTrack {
        val nearbyTracksAroundStart = alignment.start
            ?.let(::fetchNearbyLocationTracksWithAlignments)
            ?.filter { (nearbyLocationTrack, _) -> nearbyLocationTrack.id != track.id }
            ?: listOf()

        val nearbyTracksAroundEnd = alignment.end
            ?.let(::fetchNearbyLocationTracksWithAlignments)
            ?.filter { (nearbyLocationTrack, _) -> nearbyLocationTrack.id != track.id }
            ?: listOf()

        return calculateLocationTrackTopology(
            track, alignment, startChanged, endChanged, NearbyTracks(
                aroundStart = nearbyTracksAroundStart,
                aroundEnd = nearbyTracksAroundEnd,
            )
        )
    }

    @Transactional(readOnly = true)
    fun getLocationTrackEndpoints(bbox: BoundingBox, publicationState: PublicationState): List<LocationTrackEndpoint> {
        logger.serviceCall("getLocationTrackEndpoints", "bbox" to bbox)
        return getLocationTrackEndpoints(listWithAlignments(publicationState), bbox)
    }

    fun getLocationTrackOwners(): List<LocationTrackOwner> {
        logger.serviceCall("getLocationTrackOwners")
        return dao.fetchLocationTrackOwners()
    }

    @Transactional(readOnly = true)
    fun getSplittingInitializationParameters(
        trackId: IntId<LocationTrack>,
        publicationState: PublicationState,
    ): SplittingInitializationParameters? {
        logger.serviceCall(
            "getSplittingInitializationParameters",
            "locationTrackId" to trackId,
            "publicationState" to publicationState,
        )
        return getWithAlignment(publicationState, trackId)?.let { (locationTrack, alignment) ->
            val switches = getSwitchesForLocationTrack(trackId, publicationState)
                .mapNotNull { switchDao.fetchVersion(it, publicationState) }
                .map { switchDao.fetch(it) }
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
                    val address = geocodingService
                        .getGeocodingContext(publicationState, locationTrack.trackNumberId)
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

            val duplicateTracks = getLocationTrackDuplicates(locationTrack, alignment, publicationState)
                .mapNotNull { duplicate ->
                    getWithAlignmentOrThrow(publicationState, duplicate.id).let { (dupe, alignment) ->
                        geocodingService.getLocationTrackStartAndEnd(publicationState, dupe, alignment)
                    }?.let { (start, end) ->
                        if (start != null && end != null) {
                            SplitDuplicateTrack(duplicate.id, duplicate.name, start, end, duplicate.duplicateStatus)
                        } else {
                            null
                        }
                    }
                }

            SplittingInitializationParameters(
                trackId, switches, duplicateTracks
            )
        }
    }

    private fun getNearestOperatingPoint(location: Point) = ratkoOperatingPointDao
        .getOperatingPoints(
            boundingBoxAroundPoint(
                location, OPERATING_POINT_AROUND_SWITCH_SEARCH_AREA_SIZE
            )
        )
        .filter { op -> op.type == OperationalPointType.LPO || op.type == OperationalPointType.LP }
        .minByOrNull { operatingPoint -> lineLength(operatingPoint.location, location) }

    @Transactional(readOnly = true)
    fun getSwitchesForLocationTrack(
        locationTrackId: IntId<LocationTrack>,
        publicationState: PublicationState,
    ): List<IntId<TrackLayoutSwitch>> {
        logger.serviceCall(
            "getSwitchesForLocationTrack",
            "locationTrackId" to locationTrackId,
            "publicationState" to publicationState,
        )
        return getWithAlignment(publicationState, locationTrackId)?.let { (track, alignment) ->
            collectAllSwitches(track, alignment)
        } ?: emptyList()
    }

    @Transactional(readOnly = true)
    fun getAlignmentsForTracks(tracks: List<LocationTrack>): List<Pair<LocationTrack, LayoutAlignment>> {
        return tracks.map { track ->
            val alignment = track.alignmentVersion?.let(alignmentDao::fetch)
                ?: error("All location tracks should have an alignment. Alignment was not found for track=${track.id}")

            track to alignment
        }
    }
}

fun collectAllSwitches(locationTrack: LocationTrack, alignment: LayoutAlignment): List<IntId<TrackLayoutSwitch>> {
    val topologySwitches = listOfNotNull(
        locationTrack.topologyStartSwitch?.switchId, locationTrack.topologyEndSwitch?.switchId
    )
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

    val startSwitch = if (!track.exists || startPoint == null) null
    else if (startChanged) {
        findBestTopologySwitchMatch(startPoint, ownSwitches, nearbyTracks.aroundStart, null, newSwitch)
    } else {
        findBestTopologySwitchMatch(startPoint, ownSwitches, nearbyTracks.aroundStart, track.topologyStartSwitch, newSwitch)
    }

    val endSwitch = if (!track.exists || endPoint == null) {
        null
    } else if (endChanged) {
        findBestTopologySwitchMatch(endPoint, ownSwitches, nearbyTracks.aroundEnd, null, newSwitch)
    } else {
        findBestTopologySwitchMatch(endPoint, ownSwitches, nearbyTracks.aroundEnd, track.topologyEndSwitch, newSwitch)
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
    val defaultSwitch = if (currentTopologySwitch?.switchId?.let(ownSwitches::contains) != false) {
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
): TopologyLocationTrackSwitch? = nearbyTracks.flatMap { (_, otherAlignment) ->
    otherAlignment.segments.flatMap { segment ->
        if (segment.switchId !is IntId || ownSwitches.contains(segment.switchId) || segment.switchId == newSwitch?.id) {
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
    } + (newSwitch?.joints?.mapNotNull { sj ->
        pickIfClose(newSwitch.id, sj.number, target, sj.location)
    } ?: listOf())
}.minByOrNull { (_, distance) -> distance }?.first

private fun findBestTopologySwitchFromOtherTopology(
    target: IPoint,
    ownSwitches: Set<DomainId<TrackLayoutSwitch>>,
    nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): TopologyLocationTrackSwitch? = nearbyTracks.flatMap { (otherTrack, otherAlignment) ->
    listOfNotNull(
        pickIfClose(otherTrack.topologyStartSwitch, target, otherAlignment.firstSegmentStart, ownSwitches),
        pickIfClose(otherTrack.topologyEndSwitch, target, otherAlignment.lastSegmentEnd, ownSwitches),
    )
}.minByOrNull { (_, distance) -> distance }?.first

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
    if (reference != null && topologyMatch != null && !ownSwitches.contains(topologyMatch.switchId)) {
        val distance = lineLength(target, reference)
        if (distance < 1.0) topologyMatch to distance else null
    } else {
        null
    }

fun getLocationTrackEndpoints(
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    bbox: BoundingBox,
): List<LocationTrackEndpoint> = locationTracks.flatMap { (locationTrack, alignment) ->
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
    rowVersion: RowVersion<LocationTrack>,
) = locationTrackDao.fetch(rowVersion).let { track ->
    track to alignmentDao.fetch(track.getAlignmentVersionOrThrow())
}

fun filterByBoundingBox(list: List<LocationTrack>, boundingBox: BoundingBox?): List<LocationTrack> =
    if (boundingBox != null) list.filter { t -> boundingBox.intersects(t.boundingBox) }
    else list
