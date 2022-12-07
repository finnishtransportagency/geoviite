package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.linking.LocationTrackEndpoint
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.END_POINT
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.START_POINT
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.util.FreeText
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LocationTrackService(
    dao: LocationTrackDao,
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
) : DraftableObjectService<LocationTrack, LocationTrackDao>(dao) {

    @Transactional
    fun insert(request: LocationTrackSaveRequest): RowVersion<LocationTrack> {
        logger.serviceCall("insert", "request" to request)
        val (alignment, alignmentVersion) = alignmentService.newEmpty()
        val locationTrack = LocationTrack(
            alignmentVersion = alignmentVersion,
            name = request.name,
            description = request.description,
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
    fun update(id: IntId<LocationTrack>, request: LocationTrackSaveRequest): RowVersion<LocationTrack> {
        logger.serviceCall("update", "id" to id, "request" to request)
        val (originalTrack, originalAlignment) = getWithAlignmentInternalOrThrow(DRAFT, id)
        val locationTrack = originalTrack.copy(
            name = request.name,
            description = request.description,
            type = request.type,
            state = request.state,
            trackNumberId = request.trackNumberId,
            duplicateOf = request.duplicateOf,
            topologicalConnectivity = request.topologicalConnectivity,
        )

        return if (locationTrack.state != LayoutState.DELETED) {
            saveDraft(updateTopology(locationTrack, originalAlignment))
        } else {
            val segmentsWithoutSwitch = originalAlignment.segments.map { segment ->
                segment.copy(switchId = null, startJointNumber = null, endJointNumber = null)
            }
            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
            saveDraft(updateTopology(locationTrack, newAlignment), newAlignment)
        }
    }

    @Transactional
    override fun saveDraft(draft: LocationTrack): RowVersion<LocationTrack> = super.saveDraft(
        draft.copy(
            alignmentVersion = updatedAlignmentVersion(draft)
        )
    )

    private fun updatedAlignmentVersion(track: LocationTrack) =
        // If we're creating a new row or starting a draft, we duplicate the alignment to not edit any original
        if (track.dataType == TEMP || track.draft == null) alignmentService.duplicateOrNew(track.alignmentVersion)
        else track.alignmentVersion

    @Transactional
    fun saveDraft(draft: LocationTrack, alignment: LayoutAlignment): RowVersion<LocationTrack> {
        logger.serviceCall("save", "locationTrack" to draft.id, "alignment" to alignment.id)
        val alignmentVersion =
            // If we're creating a new row or starting a draft, we duplicate the alignment to not edit any original
            if (draft.dataType == TEMP || draft.draft == null) {
                alignmentService.saveAsNew(alignment)
            }
            // Otherwise update -> should have alignment already
            else if (draft.alignmentVersion == null) {
                throw IllegalStateException("DB Location track should have an alignment")
            }
            // Ensure that we update the correct one.
            else if (draft.alignmentVersion.id != alignment.id) {
                alignmentService.save(alignment.copy(id = draft.alignmentVersion.id, dataType = STORED))
            } else {
                alignmentService.save(alignment)
            }
        return saveDraftInternal(draft.copy(alignmentVersion = alignmentVersion))
    }

    @Transactional
    fun updateExternalId(id: IntId<LocationTrack>, oid: Oid<LocationTrack>): RowVersion<LocationTrack> {
        logger.serviceCall("updateExternalIdForLocationTrack", "id" to id, "oid" to oid)
        val original = getInternalOrThrow(DRAFT, id)
        return saveDraftInternal(original.copy(
            externalId = oid,
            alignmentVersion = updatedAlignmentVersion(original),
        ))
    }

    @Transactional
    override fun publish(id: IntId<LocationTrack>): RowVersion<LocationTrack> {
        logger.serviceCall("publish", "id" to id)
        val versions = dao.fetchVersionPair(id)
        val oldDraft = versions.draft?.let(dao::fetch) ?: throw IllegalStateException("Draft doesn't exist")
        val oldOfficial = versions.official?.let(dao::fetch)
        val publishedVersion = publishInternal(versions)
        if (oldOfficial != null && oldDraft.alignmentVersion != oldOfficial.alignmentVersion) {
            // The alignment on the draft overrides the one on official -> delete the original, orphaned alignment
            oldOfficial.alignmentVersion?.id?.let(alignmentDao::delete)
        }
        return publishedVersion
    }

    @Transactional
    override fun deleteUnpublishedDraft(id: IntId<LocationTrack>): RowVersion<LocationTrack> {
        val draft = getInternalOrThrow(DRAFT, id)
        val deletedVersion = super.deleteUnpublishedDraft(id)
        draft.alignmentVersion?.id?.let(alignmentDao::delete)
        return deletedVersion
    }

    override fun createDraft(item: LocationTrack) = draft(item)

    override fun createPublished(item: LocationTrack) = published(item)

    fun listNonLinked(): List<LocationTrack> {
        logger.serviceCall("listNonLinked")
        return listInternal(DRAFT).filter { a -> a.segmentCount == 0 }
    }

    fun list(publishType: PublishType, bbox: BoundingBox): List<LocationTrack> {
        logger.serviceCall("list", "publishType" to publishType, "bbox" to bbox)
        return listInternal(publishType).filter { tn -> bbox.intersects(tn.boundingBox) }
    }

    fun list(
        publishType: PublishType,
        searchTerm: FreeText,
        limit: Int?,
    ): List<LocationTrack> {
        logger.serviceCall(
            "list",
            "publishType" to publishType, "searchTerm" to searchTerm, "limit" to limit
        )
        val term = searchTerm.toString()
        return dao.fetchVersions(publishType)
            .map(dao::fetch)
            .filter { locationTrack ->
                locationTrack.externalId.toString() == term ||
                        locationTrack.exists &&
                        (locationTrack.name.contains(term, true)
                        || locationTrack.description.contains(term, true)
                                || locationTrack.id.toString() == term)
            }
            .sortedBy { locationTrack -> locationTrack.name }
            .let { list -> if (limit != null) list.take(limit) else list }
    }

    fun listNear(publishType: PublishType, bbox: BoundingBox): List<LocationTrack> {
        logger.serviceCall("listNear", "publishType" to publishType, "bbox" to bbox)
        return dao.fetchVersionsNear(publishType, bbox).map(dao::fetch).filter(LocationTrack::exists)
    }

    override fun listInternal(publishType: PublishType) =
        dao.fetchVersions(publishType)
            .map(dao::fetch)
            .filter(LocationTrack::exists)

    fun listWithAlignments(publishType: PublishType): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall("listWithAlignments", "publishType" to publishType)
        return dao.fetchVersions(publishType).map(::getWithAlignmentInternal)
    }

    fun listWithAlignments(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        logger.serviceCall(
            "listWithAlignments",
            "publishType" to publishType, "trackNumberId" to trackNumberId
        )
        return dao.fetchVersions(publishType, trackNumberId).map(::getWithAlignmentInternal)
    }

    fun getWithAlignmentOrThrow(publishType: PublishType, id: IntId<LocationTrack>): Pair<LocationTrack, LayoutAlignment> {
        logger.serviceCall("getWithAlignment", "publishType" to publishType, "id" to id)
        return getWithAlignmentInternalOrThrow(publishType, id)
    }

    fun getWithAlignment(publishType: PublishType, id: IntId<LocationTrack>): Pair<LocationTrack, LayoutAlignment>? {
        logger.serviceCall("getWithAlignment", "publishType" to publishType, "id" to id)
        return dao.fetchVersion(id, publishType)?.let(::getWithAlignmentInternal)
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
            "listNearWithAlignments",
            "publishType" to publishType, "bbox" to bbox
        )
        return dao.fetchVersionsNear(publishType, bbox).map(::getWithAlignmentInternal)
    }

    private fun getWithAlignmentInternalOrThrow(publishType: PublishType, id: IntId<LocationTrack>) =
        getWithAlignmentInternal(dao.fetchVersionOrThrow(id, publishType))

    private fun getWithAlignmentInternal(publishType: PublishType, id: IntId<LocationTrack>) =
        dao.fetchVersion(id, publishType)?.let { v -> getWithAlignmentInternal(v) }

    private fun getWithAlignmentInternal(version: RowVersion<LocationTrack>): Pair<LocationTrack, LayoutAlignment> {
        val locationTrack = dao.fetch(version)
        val alignment = alignmentDao.fetch(
            locationTrack.alignmentVersion
                ?: throw IllegalStateException("LocationTrack in DB must have an alignment")
        )
        return locationTrack to alignment
    }

    fun getDuplicates(duplicateOf: IntId<LocationTrack>, publishType: PublishType): List<LocationTrackDuplicate> {
        return dao.fetchDuplicates(duplicateOf, publishType)
    }

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

        val startSwitch =
            if (!track.exists || startPoint == null) null
            else if (startChanged) findBestTopologySwitchMatch(startPoint, track.id, ownSwitches, null)
            else findBestTopologySwitchMatch(startPoint, track.id, ownSwitches, track.topologyStartSwitch)

        val endSwitch =
            if (!track.exists || endPoint == null) null
            else if (endChanged) findBestTopologySwitchMatch(endPoint, track.id, ownSwitches, null)
            else findBestTopologySwitchMatch(endPoint, track.id, ownSwitches, track.topologyEndSwitch)

        return if (track.topologyStartSwitch == startSwitch && track.topologyEndSwitch == endSwitch) {
            track
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
        val nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>> = dao
            .fetchVersionsNear(DRAFT, boundingBoxAroundPoint(target, 1.0))
            .map { version -> getWithAlignmentInternal(version) }
            .filter { (track, alignment) -> alignment.segments.isNotEmpty() && track.id != ownId && track.exists }
        val defaultSwitch =
            if (currentTopologySwitch?.switchId?.let(ownSwitches::contains) != false) null
            else currentTopologySwitch
        return findBestTopologySwitchFromSegments(target, ownSwitches, nearbyTracks)
            ?: defaultSwitch
            ?: findBestTopologySwitchFromOtherTopology(target, ownSwitches, nearbyTracks)
    }

    fun getLocationTrackEndpoints(bbox: BoundingBox, publishType: PublishType): List<LocationTrackEndpoint> {
        logger.serviceCall("getLocationTrackEndpoints", "bbox" to bbox)
        return getLocationTrackEndpoints(listWithAlignments(publishType), bbox)
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
): TopologyLocationTrackSwitch? = nearbyTracks.flatMap { (otherTrack, otherAlignment) -> listOfNotNull(
    pickIfClose(otherTrack.topologyStartSwitch, target, otherAlignment.start, ownSwitches),
    pickIfClose(otherTrack.topologyEndSwitch, target, otherAlignment.end, ownSwitches),
) }.minByOrNull { (_, distance) -> distance }?.first

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
        alignment.start?.takeIf(bbox::contains)?.let{ p ->
            LocationTrackEndpoint(trackId, p.toPoint(), START_POINT)
        },
        alignment.end?.takeIf(bbox::contains)?.let{ p ->
            LocationTrackEndpoint(trackId, p.toPoint(), END_POINT)
        },
    )
}
