package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.LocationTrackEndpoint
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.END_POINT
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.START_POINT
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.util.FreeText
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class LocationTrackService(
    dao: LocationTrackDao,
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val geocodingService: GeocodingService,
    private val switchDao: LayoutSwitchDao,
) : DraftableObjectService<LocationTrack, LocationTrackDao>(dao) {

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
        )

        return if (locationTrack.state != LayoutState.DELETED) {
            saveDraft(updateTopology(locationTrack, originalAlignment))
        } else {
            val segmentsWithoutSwitch = originalAlignment.segments.map(LayoutSegment::withoutSwitch)
            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
            saveDraft(updateTopology(locationTrack, newAlignment), newAlignment)
        }
    }

    @Transactional
    override fun saveDraft(draft: LocationTrack): DaoResponse<LocationTrack> =
        super.saveDraft(draft.copy(alignmentVersion = updatedAlignmentVersion(draft)))

    private fun updatedAlignmentVersion(track: LocationTrack) =
        // If we're creating a new row or starting a draft, we duplicate the alignment to not edit any original
        if (track.dataType == TEMP || track.draft == null) alignmentService.duplicateOrNew(track.alignmentVersion)
        else track.alignmentVersion

    @Transactional
    fun saveDraft(draft: LocationTrack, alignment: LayoutAlignment): DaoResponse<LocationTrack> {
        logger.serviceCall("save", "locationTrack" to draft.id, "alignment" to alignment.id)
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

    @Transactional
    fun updateExternalId(id: IntId<LocationTrack>, oid: Oid<LocationTrack>): DaoResponse<LocationTrack> {
        logger.serviceCall("updateExternalIdForLocationTrack", "id" to id, "oid" to oid)
        val original = getInternalOrThrow(DRAFT, id)
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
    override fun deleteUnpublishedDraft(id: IntId<LocationTrack>): DaoResponse<LocationTrack> {
        val draft = getInternalOrThrow(DRAFT, id)
        val deletedVersion = super.deleteUnpublishedDraft(id)
        draft.alignmentVersion?.id?.let(alignmentDao::delete)
        return deletedVersion
    }

    override fun createDraft(item: LocationTrack) = draft(item)

    override fun createPublished(item: LocationTrack) = published(item)

    fun listNonLinked(): List<LocationTrack> {
        logger.serviceCall("listNonLinked")
        return listInternal(DRAFT, false).filter { a -> a.segmentCount == 0 }
    }

    fun list(publishType: PublishType, bbox: BoundingBox): List<LocationTrack> {
        logger.serviceCall("list", "publishType" to publishType, "bbox" to bbox)
        return listInternal(publishType, false).filter { tn -> bbox.intersects(tn.boundingBox) }
    }

    override fun sortSearchResult(list: List<LocationTrack>) = list.sortedBy (LocationTrack::name)

    override fun idMatches(term: String, item: LocationTrack) =
        item.externalId.toString() == term || item.id.toString() == term

    override fun contentMatches(term: String, item: LocationTrack) =
        item.exists && (item.name.contains(term, true) || item.descriptionBase.contains(term, true))

    fun listNear(publishType: PublishType, bbox: BoundingBox): List<LocationTrack> {
        logger.serviceCall("listNear", "publishType" to publishType, "bbox" to bbox)
        return dao.fetchVersionsNear(publishType, bbox).map(dao::fetch).filter(LocationTrack::exists)
    }

    fun listWithAlignments(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall(
            "listWithAlignments", "publishType" to publishType, "trackNumberId" to trackNumberId
        )
        return dao.fetchVersions(publishType, false, trackNumberId).map(::getWithAlignmentInternal)
    }

    fun getWithAlignmentOrThrow(
        publishType: PublishType,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "publishType" to publishType, "id" to id)
        return getWithAlignmentInternalOrThrow(publishType, id)
    }

    fun getWithAlignment(publishType: PublishType, id: IntId<LocationTrack>): Pair<LocationTrack, LayoutAlignment>? {
        logger.serviceCall("getWithAlignment", "publishType" to publishType, "id" to id)
        return dao.fetchVersion(id, publishType)?.let(::getWithAlignmentInternal)
    }

    fun getOfficialWithAlignmentAtMoment(
        id: IntId<LocationTrack>,
        moment: Instant,
    ): Pair<LocationTrack, LayoutAlignment>? {
        logger.serviceCall("getOfficialWithAlignmentAtMoment", "id" to id, "moment" to moment)
        return dao.fetchOfficialVersionAtMoment(id, moment)?.let(::getWithAlignmentInternal)
    }

    fun getWithAlignment(version: RowVersion<LocationTrack>): Pair<LocationTrack, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "version" to version)
        return getWithAlignmentInternal(version)
    }

    fun listNearWithAlignments(
        publishType: PublishType,
        bbox: BoundingBox,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall(
            "listNearWithAlignments", "publishType" to publishType, "bbox" to bbox
        )
        return dao.fetchVersionsNear(publishType, bbox).map(::getWithAlignmentInternal)
    }

    @Transactional(readOnly = true)
    fun getLocationTracksNear(
        location: IPoint,
        publishType: PublishType,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall("getLocationTracksNear", "location" to location)
        val alignmentSearchAreaSize = 2.0
        val alignmentSearchArea = BoundingBox(
            Point(0.0, 0.0), Point(alignmentSearchAreaSize, alignmentSearchAreaSize)
        ).centerAt(location)
        val nearbyLocationTracks = listNearWithAlignments(publishType, alignmentSearchArea).filter { (_, alignment) ->
            alignment.segments.any { segment ->
                alignmentSearchArea.intersects(segment.boundingBox) && segment.points.any { point ->
                    alignmentSearchArea.contains(
                        point
                    )
                }
            }
        }

        return nearbyLocationTracks
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
        val locationTrack = getOrThrow(publishType, locationTrackId)
        val geocodingContext = geocodingService.getGeocodingContext(publishType, locationTrack.trackNumberId)
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

    private val BUFFER_TRANSLATION = "Puskin"

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
            DescriptionSuffixType.SWITCH_TO_BUFFER -> FreeText("${locationTrack.descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - $BUFFER_TRANSLATION")
            DescriptionSuffixType.SWITCH_TO_SWITCH -> FreeText("${locationTrack.descriptionBase} ${startSwitchName ?: "???"} - ${endSwitchName ?: "???"}")
        }
    }

    private fun getWithAlignmentInternalOrThrow(publishType: PublishType, id: IntId<LocationTrack>) =
        getWithAlignmentInternal(dao.fetchVersionOrThrow(id, publishType))

    private fun getWithAlignmentInternal(version: RowVersion<LocationTrack>): Pair<LocationTrack, LayoutAlignment> =
        locationTrackWithAlignment(dao, alignmentDao, version)

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
            LocationTrackInfoboxExtras(duplicateOf, sortedDuplicates, startSwitch, endSwitch)
        }
    }

    private fun getLocationTrackDuplicates(
        id: IntId<LocationTrack>,
        publishType: PublishType,
    ): List<LocationTrackDuplicate> {
        val duplicates = dao.fetchDuplicates(id, publishType).map(dao::fetch)

        val duplicateStartAddresses = duplicates.map { duplicate ->
            duplicate.alignmentVersion?.let { alignmentVersion ->
                alignmentDao.fetch(alignmentVersion).start?.let { startPoint ->
                    geocodingService.getGeocodingContext(publishType, duplicate.trackNumberId)
                        ?.getAddress(startPoint)?.first
                }
            }
        }
        return duplicates.mapIndexed { index, d -> index to d }.sortedWith { a, b ->
            compareValues(
                duplicateStartAddresses[a.first], duplicateStartAddresses[b.first]
            )
        }.map { (_, track) -> LocationTrackDuplicate(track.id as IntId, track.name, track.externalId) }
    }

    private fun getDuplicateOf(
        locationTrack: LocationTrack,
        publishType: PublishType,
    ) = locationTrack.duplicateOf?.let { duplicateId ->
        get(publishType, duplicateId)?.let { dup ->
            LocationTrackDuplicate(
                duplicateId, dup.name, dup.externalId
            )
        }
    }

    private fun fetchSwitchAtEndById(id: IntId<TrackLayoutSwitch>, publishType: PublishType) = switchDao.fetchVersion(
        id, publishType
    )?.let(switchDao::fetch)?.let { switch -> LayoutSwitchIdAndName(id, switch.name) }

    @Transactional
    fun updateTopology(
        track: LocationTrack,
        alignment: LayoutAlignment,
        startChanged: Boolean = false,
        endChanged: Boolean = false,
    ): LocationTrack {
        val startPoint = alignment.start
        val endPoint = alignment.end
        val ownSwitches = alignment.segments.mapNotNull { segment -> segment.switchId }.toSet()

        val startSwitch = if (!track.exists || startPoint == null) null
        else if (startChanged) findBestTopologySwitchMatch(startPoint, track.id, ownSwitches, null)
        else findBestTopologySwitchMatch(startPoint, track.id, ownSwitches, track.topologyStartSwitch)

        val endSwitch = if (!track.exists || endPoint == null) null
        else if (endChanged) findBestTopologySwitchMatch(endPoint, track.id, ownSwitches, null)
        else findBestTopologySwitchMatch(endPoint, track.id, ownSwitches, track.topologyEndSwitch)

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

    private fun findBestTopologySwitchMatch(
        target: IPoint,
        ownId: DomainId<LocationTrack>,
        ownSwitches: Set<DomainId<TrackLayoutSwitch>>,
        currentTopologySwitch: TopologyLocationTrackSwitch?,
    ): TopologyLocationTrackSwitch? {
        val nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>> =
            dao.fetchVersionsNear(DRAFT, boundingBoxAroundPoint(target, 1.0))
                .map { version -> getWithAlignmentInternal(version) }
                .filter { (track, alignment) -> alignment.segments.isNotEmpty() && track.id != ownId && track.exists }
        val defaultSwitch = if (currentTopologySwitch?.switchId?.let(ownSwitches::contains) != false) null
        else currentTopologySwitch
        return findBestTopologySwitchFromSegments(target, ownSwitches, nearbyTracks) ?: defaultSwitch
        ?: findBestTopologySwitchFromOtherTopology(target, ownSwitches, nearbyTracks)
    }

    fun getLocationTrackEndpoints(bbox: BoundingBox, publishType: PublishType): List<LocationTrackEndpoint> {
        logger.serviceCall("getLocationTrackEndpoints", "bbox" to bbox)
        return getLocationTrackEndpoints(listWithAlignments(publishType), bbox)
    }

    fun duplicateNameExistsFor(locationTrackId: IntId<LocationTrack>): Boolean {
        logger.serviceCall("duplicateNameExistsFor", "locationTrackId" to locationTrackId)
        return dao.duplicateNameExistsForPublicationCandidate(locationTrackId)
    }

    fun getSwitchesForLocationTrack(
        locationTrackId: IntId<LocationTrack>,
        publishType: PublishType,
    ): List<IntId<TrackLayoutSwitch>> {
        logger.serviceCall(
            "getSwitchesForLocationTrack", "locationTrackId" to locationTrackId, "publishType" to publishType
        )
        val ltAndAlignment = getWithAlignment(publishType, locationTrackId)
        val topologySwitches = listOfNotNull(
            ltAndAlignment?.first?.topologyStartSwitch?.switchId, ltAndAlignment?.first?.topologyEndSwitch?.switchId
        )
        val segmentSwitches =
            ltAndAlignment?.second?.segments?.mapNotNull { segment -> segment.switchId as IntId? } ?: emptyList()
        return topologySwitches + segmentSwitches
    }
}

private fun findBestTopologySwitchFromSegments(
    target: IPoint,
    ownSwitches: Set<DomainId<TrackLayoutSwitch>>,
    nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): TopologyLocationTrackSwitch? = nearbyTracks.flatMap { (_, otherAlignment) ->
    otherAlignment.segments.flatMap { segment ->
        if (segment.switchId !is IntId || ownSwitches.contains(segment.switchId)) listOf()
        else listOfNotNull(
            segment.startJointNumber?.let { number ->
                pickIfClose(segment.switchId, number, target, segment.points.first())
            },
            segment.endJointNumber?.let { number ->
                pickIfClose(segment.switchId, number, target, segment.points.last())
            },
        )
    }
}.minByOrNull { (_, distance) -> distance }?.first

private fun findBestTopologySwitchFromOtherTopology(
    target: IPoint,
    ownSwitches: Set<DomainId<TrackLayoutSwitch>>,
    nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): TopologyLocationTrackSwitch? = nearbyTracks.flatMap { (otherTrack, otherAlignment) ->
    listOfNotNull(
        pickIfClose(otherTrack.topologyStartSwitch, target, otherAlignment.start, ownSwitches),
        pickIfClose(otherTrack.topologyEndSwitch, target, otherAlignment.end, ownSwitches),
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
        alignment.start?.takeIf(bbox::contains)?.let { p ->
            LocationTrackEndpoint(trackId, p.toPoint(), START_POINT)
        },
        alignment.end?.takeIf(bbox::contains)?.let { p ->
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
