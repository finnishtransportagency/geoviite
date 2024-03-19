package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.LocationTrackEndpoint
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.END_POINT
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.START_POINT
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.TopologyLinkFindingSwitch
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.util.FreeText
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

const val TRACK_SEARCH_AREA_SIZE = 2.0

const val BUFFER_TRANSLATION = "Puskin"
const val OWNERSHIP_BOUNDARY_TRANSLATION = "Omistusraja"

@Service
class LocationTrackService(
    locationTrackDao: LocationTrackDao,
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val geocodingService: GeocodingService,
    private val switchDao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val splitDao: SplitDao,
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

        return if (locationTrack.state != LayoutState.DELETED) {
            saveDraft(fetchNearbyTracksAndCalculateLocationTrackTopology(locationTrack, originalAlignment))
        } else {
            clearDuplicateReferences(id)
            val segmentsWithoutSwitch = originalAlignment.segments.map(LayoutSegment::withoutSwitch)
            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
            saveDraft(fetchNearbyTracksAndCalculateLocationTrackTopology(locationTrack, newAlignment), newAlignment)
        }
    }

    @Transactional
    fun updateState(id: IntId<LocationTrack>, state: LayoutState): DaoResponse<LocationTrack> {
        logger.serviceCall("update", "id" to id, "state" to state)
        val (originalTrack, originalAlignment) = getWithAlignmentInternalOrThrow(DRAFT, id)
        val locationTrack = originalTrack.copy(state = state)

        return if (locationTrack.state != LayoutState.DELETED) {
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
    fun fetchDuplicates(publishType: PublishType, id: IntId<LocationTrack>): List<LocationTrack> {
        logger.serviceCall(
            "fetchDuplicates",
            "publishType" to publishType,
            "locationTrackId" to id,
        )
        return dao.fetchDuplicateVersions(id, publishType).map(dao::fetch)
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

    fun list(publishType: PublishType, bbox: BoundingBox): List<LocationTrack> {
        logger.serviceCall("list", "publishType" to publishType, "bbox" to bbox)
        return dao.list(publishType, false).filter { tn -> bbox.intersects(tn.boundingBox) }
    }

    override fun sortSearchResult(list: List<LocationTrack>): List<LocationTrack> = list.sortedBy(LocationTrack::name)

    fun list(
        publicationState: PublishType,
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

    fun listNear(publishType: PublishType, bbox: BoundingBox): List<LocationTrack> {
        logger.serviceCall("listNear", "publishType" to publishType, "bbox" to bbox)
        return dao.listNear(publishType, bbox).filter(LocationTrack::exists)
    }

    @Transactional(readOnly = true)
    fun listWithAlignments(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        includeDeleted: Boolean = false,
        boundingBox: BoundingBox? = null,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall(
            "listWithAlignments",
            "publishType" to publishType,
            "trackNumberId" to trackNumberId,
            "includeDeleted" to includeDeleted,
        )
        return dao
            .list(publishType, includeDeleted, trackNumberId)
            .let { list -> filterByBoundingBox(list, boundingBox) }
            .let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun getManyWithAlignments(
        publishType: PublishType,
        ids: List<IntId<LocationTrack>>,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall("getManyWithAlignments", "publishType" to publishType, "ids" to ids)
        return dao.getMany(publishType, ids).let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun getWithAlignmentOrThrow(
        publishType: PublishType,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "publishType" to publishType, "id" to id)
        return getWithAlignmentInternalOrThrow(publishType, id)
    }

    @Transactional(readOnly = true)
    fun getWithAlignment(publishType: PublishType, id: IntId<LocationTrack>): Pair<LocationTrack, LayoutAlignment>? {
        logger.serviceCall("getWithAlignment", "publishType" to publishType, "id" to id)
        return dao.fetchVersion(id, publishType)?.let(::getWithAlignmentInternal)
    }

    @Transactional(readOnly = true)
    fun getTrackPoint(
        publishType: PublishType,
        locationTrackId: IntId<LocationTrack>,
        address: TrackMeter,
    ): AddressPoint? {
        logger.serviceCall(
            "getTrackPoint",
            "publishType" to publishType, "locationTrackId" to locationTrackId, "address" to address,
        )
        val locationTrackAndAlignment = getWithAlignment(publishType, locationTrackId)
        return locationTrackAndAlignment?.let { (locationTrack, alignment) ->
            geocodingService.getTrackLocation(locationTrack, alignment, address, publishType)
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
        publishType: PublishType,
        bbox: BoundingBox,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall(
            "listNearWithAlignments", "publishType" to publishType, "bbox" to bbox
        )
        return dao.listNear(publishType, bbox).let(::associateWithAlignments)
    }

    @Transactional(readOnly = true)
    fun getLocationTracksNear(
        location: IPoint,
        publishType: PublishType,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall("getLocationTracksNear", "location" to location)
        val searchArea = BoundingBox(
            Point(0.0, 0.0),
            Point(TRACK_SEARCH_AREA_SIZE, TRACK_SEARCH_AREA_SIZE),
        ).centerAt(location)
        return listNearWithAlignments(publishType, searchArea).filter { (_, alignment) ->
            alignment.segments.any { segment ->
                searchArea.intersects(segment.boundingBox) && segment.segmentPoints.any(searchArea::contains)
            }
        }
    }

    @Transactional(readOnly = true)
    fun getMetadataSections(
        locationTrackId: IntId<LocationTrack>,
        publishType: PublishType,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection> {
        logger.serviceCall(
            "getSectionsByPlan",
            "locationTrackId" to locationTrackId,
            "publishType" to publishType,
            "boundingBox" to boundingBox
        )
        val locationTrack = get(publishType, locationTrackId)
        val geocodingContext = locationTrack?.let {
            geocodingService.getGeocodingContext(publishType, locationTrack.trackNumberId)
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
    fun getFullDescription(publishType: PublishType, locationTrack: LocationTrack): FreeText {
        val alignmentVersion = locationTrack.alignmentVersion
        val (startSwitch, endSwitch) = alignmentVersion?.let {
            val alignment = alignmentDao.fetch(alignmentVersion)
            getSwitchIdAtStart(alignment, locationTrack) to getSwitchIdAtEnd(
                alignment, locationTrack
            )
        } ?: (null to null)

        fun getSwitchShortName(switchId: IntId<TrackLayoutSwitch>) =
            switchDao.fetchVersion(switchId, publishType)?.let(switchDao::fetch)?.shortName

        val startSwitchName = startSwitch?.let(::getSwitchShortName)
        val endSwitchName = endSwitch?.let(::getSwitchShortName)

        return when (locationTrack.descriptionSuffix) {
            DescriptionSuffixType.NONE -> locationTrack.descriptionBase
            DescriptionSuffixType.SWITCH_TO_BUFFER ->
                FreeText("${locationTrack.descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - $BUFFER_TRANSLATION")
            DescriptionSuffixType.SWITCH_TO_SWITCH ->
                FreeText("${locationTrack.descriptionBase} ${startSwitchName ?: "???"} - ${endSwitchName ?: "???"}")
            DescriptionSuffixType.SWITCH_TO_OWNERSHIP_BOUNDARY ->
                FreeText("${locationTrack.descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - $OWNERSHIP_BOUNDARY_TRANSLATION")
        }
    }

    private fun getWithAlignmentInternalOrThrow(publishType: PublishType, id: IntId<LocationTrack>) =
        getWithAlignmentInternal(dao.fetchVersionOrThrow(id, publishType))

    private fun getWithAlignmentInternal(version: RowVersion<LocationTrack>): Pair<LocationTrack, LayoutAlignment> =
        locationTrackWithAlignment(dao, alignmentDao, version)

    private fun associateWithAlignments(lines: List<LocationTrack>): List<Pair<LocationTrack, LayoutAlignment>> {
        // This is a little convoluted to avoid extra passes of transaction annotation handling in alignmentDao.fetch
        val alignments = alignmentDao.fetchMany(lines.map(LocationTrack::getAlignmentVersionOrThrow))
        return lines.map { line -> line to alignments.getValue(line.getAlignmentVersionOrThrow()) }
    }

    @Transactional(readOnly = true)
    fun getInfoboxExtras(publishType: PublishType, id: IntId<LocationTrack>): LocationTrackInfoboxExtras? {
        val locationTrackAndAlignment = getWithAlignment(publishType, id)
        return locationTrackAndAlignment?.let { (locationTrack, alignment) ->
            val duplicateOf = getDuplicateOf(locationTrack, publishType)
            val sortedDuplicates = getLocationTrackDuplicates(id, publishType)
            val startSwitch = (alignment.segments.firstOrNull()?.switchId as IntId?
                ?: locationTrack.topologyStartSwitch?.switchId)?.let { id -> fetchSwitchAtEndById(id, publishType) }
            val endSwitch = (alignment.segments.lastOrNull()?.switchId as IntId?
                ?: locationTrack.topologyEndSwitch?.switchId)?.let { id -> fetchSwitchAtEndById(id, publishType) }
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
    fun getRelinkableSwitchesCount(publishType: PublishType, id: IntId<LocationTrack>): Int? {
        val locationTrack = get(publishType, id)
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
            locationTrack.topologyEndSwitch?.switchId
        ) + switchDao.findSwitchesNearAlignment(alignmentVersion)).distinct().size

    private fun getLocationTrackDuplicates(
        id: IntId<LocationTrack>,
        publishType: PublishType,
    ): List<LocationTrackDuplicate> {
        val originalAndAlignment = getWithAlignmentOrThrow(publishType, id)
        val duplicates = dao.fetchDuplicateVersions(id, publishType).map(dao::fetch)

        val duplicateMValues = duplicates.map { duplicate ->
            duplicate.alignmentVersion?.let { alignmentVersion ->
                alignmentDao.fetch(alignmentVersion).firstSegmentStart?.let(
                    originalAndAlignment.second::getClosestPointM
                )?.first
            }
        }
        return duplicates.mapIndexed { index, d -> index to d }.sortedWith { a, b ->
            compareValues(
                duplicateMValues[a.first], duplicateMValues[b.first]
            )
        }.map { (_, track) -> LocationTrackDuplicate(track.id as IntId, track.trackNumberId, track.name, track.externalId) }
    }

    private fun getDuplicateOf(
        locationTrack: LocationTrack,
        publishType: PublishType,
    ) = locationTrack.duplicateOf?.let { duplicateId ->
        get(publishType, duplicateId)?.let { dup ->
            LocationTrackDuplicate(
                duplicateId, dup.trackNumberId, dup.name, dup.externalId
            )
        }
    }

    private fun fetchSwitchAtEndById(id: IntId<TrackLayoutSwitch>, publishType: PublishType) = switchDao.fetchVersion(
        id, publishType
    )?.let(switchDao::fetch)?.let { switch -> LayoutSwitchIdAndName(id, switch.name) }

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
        val nearbyTracksAroundStart =
            (alignment.start?.let(::fetchNearbyLocationTracksWithAlignments)?.filter { (nearbyLocationTrack, _) ->
                nearbyLocationTrack.id != track.id
            } ?: listOf())

        val nearbyTracksAroundEnd =
            (alignment.end?.let(::fetchNearbyLocationTracksWithAlignments)?.filter { (nearbyLocationTrack, _) ->
                nearbyLocationTrack.id != track.id
            } ?: listOf())

        return calculateLocationTrackTopology(
            track, alignment, startChanged, endChanged, NearbyTracks(
                aroundStart = nearbyTracksAroundStart,
                aroundEnd = nearbyTracksAroundEnd,
            )
        )
    }

    @Transactional(readOnly = true)
    fun getLocationTrackEndpoints(bbox: BoundingBox, publishType: PublishType): List<LocationTrackEndpoint> {
        logger.serviceCall("getLocationTrackEndpoints", "bbox" to bbox)
        return getLocationTrackEndpoints(listWithAlignments(publishType), bbox)
    }

    fun getLocationTrackOwners(): List<LocationTrackOwner> {
        logger.serviceCall("getLocationTrackOwners")
        return dao.fetchLocationTrackOwners()
    }

    @Transactional(readOnly = true)
    fun getSplittingInitializationParameters(
        locationTrackId: IntId<LocationTrack>,
        publishType: PublishType,
    ): SplittingInitializationParameters? {
        logger.serviceCall(
            "getSplittingInitializationParameters",
            "locationTrackId" to locationTrackId,
            "publishType" to publishType,
        )
        return getWithAlignment(publishType, locationTrackId)?.let { (locationTrack, alignment) ->
            val switches = getSwitchesForLocationTrack(locationTrackId, publishType)
                .mapNotNull { switchDao.fetchVersion(it, publishType) }
                .map { switchDao.fetch(it) }
                .mapNotNull { switch ->
                    switchLibraryService.getSwitchStructure(switch.switchStructureId).let { structure ->
                        val presentationJointLocation = switch.getJoint(structure.presentationJointNumber)?.location
                        if (presentationJointLocation != null) {
                            switch to presentationJointLocation
                        } else null
                    }
                }
                .map { (switch, location) ->
                    val address = geocodingService
                        .getGeocodingContext(publishType, locationTrack.trackNumberId)
                        ?.getAddressAndM(location)
                    val mAlongAlignment = alignment.getClosestPointM(location)?.first
                    SwitchOnLocationTrack(switch.id as IntId, switch.name, address?.address, location, mAlongAlignment)
                }

            val duplicateTracks = getLocationTrackDuplicates(locationTrackId, publishType).mapNotNull { duplicate ->
                getWithAlignmentOrThrow(publishType, duplicate.id).let { (dupe, alignment) ->
                    geocodingService.getLocationTrackStartAndEnd(publishType, dupe, alignment)
                }?.let { (start, end) ->
                    if (start != null && end != null) {
                        SplitDuplicateTrack(
                            duplicate.id, duplicate.name, start, end
                        )
                    } else null
                }
            }

            SplittingInitializationParameters(
                locationTrackId, switches, duplicateTracks
            )
        }
    }

    @Transactional(readOnly = true)
    fun getSwitchesForLocationTrack(
        locationTrackId: IntId<LocationTrack>,
        publishType: PublishType,
    ): List<IntId<TrackLayoutSwitch>> {
        logger.serviceCall(
            "getSwitchesForLocationTrack", "locationTrackId" to locationTrackId, "publishType" to publishType
        )
        return getWithAlignment(publishType, locationTrackId)?.let { (track, alignment) ->
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
    }
    else findBestTopologySwitchMatch(startPoint, ownSwitches, nearbyTracks.aroundStart, track.topologyStartSwitch, newSwitch)

    val endSwitch = if (!track.exists || endPoint == null) null
    else if (endChanged) {
        findBestTopologySwitchMatch(endPoint, ownSwitches, nearbyTracks.aroundEnd, null, newSwitch)
    }
    else findBestTopologySwitchMatch(endPoint, ownSwitches, nearbyTracks.aroundEnd, track.topologyEndSwitch, newSwitch)

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
    val defaultSwitch = if (currentTopologySwitch?.switchId?.let(ownSwitches::contains) != false) null
    else currentTopologySwitch
    return findBestTopologySwitchFromSegments(target, ownSwitches, nearbyTracksForSearch, newSwitch) ?: defaultSwitch
    ?: findBestTopologySwitchFromOtherTopology(target, ownSwitches, nearbyTracksForSearch)
}

private fun findBestTopologySwitchFromSegments(
    target: IPoint,
    ownSwitches: Set<DomainId<TrackLayoutSwitch>>,
    nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    newSwitch: TopologyLinkFindingSwitch?,
): TopologyLocationTrackSwitch? = nearbyTracks.flatMap { (_, otherAlignment) ->
    otherAlignment.segments.flatMap { segment ->
        if (segment.switchId !is IntId || ownSwitches.contains(segment.switchId) || segment.switchId == newSwitch?.id) listOf()
        else listOfNotNull(
            segment.startJointNumber?.let { number ->
                pickIfClose(
                    segment.switchId, number, target, segment.segmentStart
                )
            },
            segment.endJointNumber?.let { number -> pickIfClose(segment.switchId, number, target, segment.segmentEnd) },
        )
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
) = if (reference != null && topologyMatch != null && !ownSwitches.contains(topologyMatch.switchId)) {
    val distance = lineLength(target, reference)
    if (distance < 1.0) topologyMatch to distance
    else null
} else null

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

fun getConnectedSwitchIds(locationTrack: LocationTrack, alignment: LayoutAlignment): Set<IntId<TrackLayoutSwitch>> {
    val segmentLinks = alignment.segments.mapNotNull { segment -> segment.switchId as IntId? }
    val topologyLinks = listOfNotNull(
        locationTrack.topologyStartSwitch?.switchId,
        locationTrack.topologyEndSwitch?.switchId,
    )
    return (segmentLinks + topologyLinks).toHashSet()
}

fun filterByBoundingBox(list: List<LocationTrack>, boundingBox: BoundingBox?): List<LocationTrack> =
    if (boundingBox != null) list.filter { t -> boundingBox.intersects(t.boundingBox) }
    else list
