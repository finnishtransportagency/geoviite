package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.linking.TrackLayoutSwitchSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.Page
import fi.fta.geoviite.infra.util.page
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LayoutSwitchService @Autowired constructor(
    dao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val locationTrackService: LocationTrackService,
) : DraftableObjectService<TrackLayoutSwitch, LayoutSwitchDao>(dao) {

    @Transactional
    fun insertSwitch(request: TrackLayoutSwitchSaveRequest): IntId<TrackLayoutSwitch> {
        logger.serviceCall("insertSwitch", "request" to request)

        val switch = TrackLayoutSwitch(
            name = request.name,
            switchStructureId = request.switchStructureId,
            stateCategory = request.stateCategory,
            joints = listOf(),
            externalId = null,
            sourceId = null,
            trapPoint = request.trapPoint,
            ownerId = request.ownerId,
            source = GeometrySource.GENERATED,
        )
        return saveDraftInternal(switch).id
    }

    @Transactional
    fun updateSwitch(id: IntId<TrackLayoutSwitch>, switch: TrackLayoutSwitchSaveRequest): IntId<TrackLayoutSwitch> {
        logger.serviceCall("updateSwitch", "id" to id, "switch" to switch)
        val layoutSwitch = getInternalOrThrow(DRAFT, id)
        val switchStructureChanged = switch.switchStructureId != layoutSwitch.switchStructureId
        val switchJoints = if (switchStructureChanged) emptyList() else layoutSwitch.joints

        if (switch.stateCategory == LayoutStateCategory.NOT_EXISTING || switchStructureChanged) {
            clearSwitchInformationFromSegments(id)
        }

        val updatedLayoutSwitch = layoutSwitch.copy(
            id = id,
            name = switch.name,
            switchStructureId = switch.switchStructureId,
            stateCategory = switch.stateCategory,
            trapPoint = switch.trapPoint,
            joints = switchJoints,
            ownerId = switch.ownerId,
        )
        return saveDraftInternal(updatedLayoutSwitch).id
    }

    @Transactional
    fun deleteDraftSwitch(switchId: IntId<TrackLayoutSwitch>): IntId<TrackLayoutSwitch> {
        logger.serviceCall("deleteDraftSwitch", "switchId" to switchId)
        clearSwitchInformationFromSegments(switchId)
        return deleteUnpublishedDraft(switchId).id
    }

    @Transactional
    fun clearSwitchInformationFromSegments(layoutSwitchId: IntId<TrackLayoutSwitch>) {
        getLocationTracksLinkedToSwitch(DRAFT, layoutSwitchId).forEach { (locationTrack, alignment) ->
            val (updatedLocationTrack, updatedAlignment) = clearSwitchInformationFromSegments(
                locationTrack,
                alignment,
                layoutSwitchId,
            )
            locationTrackService.saveDraft(updatedLocationTrack, updatedAlignment)
        }
    }

    fun getSegmentSwitchJointConnections(
        publishType: PublishType,
        switchId: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        logger.serviceCall(
            "getSegmentSwitchJointConnections", "publishType" to publishType, "switchId" to switchId
        )
        return dao.fetchSegmentSwitchJointConnections(publishType, switchId)
    }

    fun getPresentationJoint(switch: TrackLayoutSwitch): TrackLayoutSwitchJoint? {
        val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
        return switch.getJoint(structure.presentationJointNumber)
    }

    fun getPresentationJointOrThrow(switch: TrackLayoutSwitch): TrackLayoutSwitchJoint {
        return getPresentationJoint(switch)
            ?: throw IllegalArgumentException("Switch ${switch.id} has no presentation joint")
    }

    @Transactional(readOnly = true)
    fun listWithStructure(
        publishType: PublishType,
        includeDeleted: Boolean = false,
    ): List<Pair<TrackLayoutSwitch, SwitchStructure>> {
        logger.serviceCall("list", "publishType" to publishType)
        return listInternal(publishType, includeDeleted).map(::withStructure)
    }

    override fun sortSearchResult(list: List<TrackLayoutSwitch>) = list.sortedBy(TrackLayoutSwitch::name)

    override fun idMatches(term: String, item: TrackLayoutSwitch) =
        item.externalId.toString() == term || item.id.toString() == term

    override fun contentMatches(term: String, item: TrackLayoutSwitch) =
        item.exists && item.name.toString().replace("  ", " ").contains(term, true)

    @Transactional
    fun updateExternalIdForSwitch(
        id: IntId<TrackLayoutSwitch>,
        oid: Oid<TrackLayoutSwitch>,
    ): DaoResponse<TrackLayoutSwitch> {
        logger.serviceCall("updateExternalIdForSwitch", "id" to id, "oid" to oid)
        val original = getInternalOrThrow(DRAFT, id)
        return saveDraft(original.copy(externalId = oid))
    }

    private fun withStructure(switch: TrackLayoutSwitch): Pair<TrackLayoutSwitch, SwitchStructure> =
        switch to switchLibraryService.getSwitchStructure(switch.switchStructureId)


    override fun createDraft(item: TrackLayoutSwitch) = draft(item)

    override fun createPublished(item: TrackLayoutSwitch) = published(item)

    @Transactional(readOnly = true)
    fun getSwitchJointConnections(
        publishType: PublishType,
        switchId: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        logger.serviceCall(
            "getSwitchJointConnections", "publishType" to publishType, "switchId" to switchId
        )
        val segment = getSegmentSwitchJointConnections(publishType, switchId)
        val topological = getTopologySwitchJointConnections(publishType, switchId)
        return (segment + topological).groupBy { joint -> joint.number }.values.map { jointConnections ->
            jointConnections.reduceRight(TrackLayoutSwitchJointConnection::merge)
        }
    }

    fun duplicateNameExistsForPublicationCandidate(switchId: IntId<TrackLayoutSwitch>): Boolean {
        logger.serviceCall("duplicateNameExistsForPublicationCandidate", "switchId" to switchId)
        return dao.duplicateNameExistsForPublicationCandidate(switchId)
    }

    private fun getTopologySwitchJointConnections(
        publicationState: PublishType,
        layoutSwitchId: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        val layoutSwitch = get(publicationState, layoutSwitchId) ?: return listOf()
        val linkedTracks = getLocationTracksLinkedToSwitch(publicationState, layoutSwitchId)
        return linkedTracks.flatMap { (track, alignment) ->
            getTopologyPoints(layoutSwitchId, track, alignment).mapNotNull { (connection, point) ->
                layoutSwitch.getJoint(connection.jointNumber)?.let { joint ->
                    TrackLayoutSwitchJointConnection(
                        connection.jointNumber,
                        listOf(TrackLayoutSwitchJointMatch(track.id as IntId, point)),
                        joint.locationAccuracy
                    )
                }
            }
        }
    }

    private fun getLocationTracksLinkedToSwitch(
        publicationState: PublishType,
        layoutSwitchId: IntId<TrackLayoutSwitch>,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return dao
            .findLocationTracksLinkedToSwitch(publicationState, layoutSwitchId)
            .map { ids -> locationTrackService.getWithAlignment(ids.rowVersion) }
    }
}

fun pageSwitches(
    switches: List<Pair<TrackLayoutSwitch, SwitchStructure>>,
    offset: Int?,
    limit: Int?,
    comparisonPoint: Point?,
): Page<TrackLayoutSwitch> {
    return if (comparisonPoint != null) {
        val switchesWithDistance: List<Pair<TrackLayoutSwitch, Double?>> =
            switches.map { (switch, structure) -> associateByDistance(switch, structure, comparisonPoint) }
        page(switchesWithDistance, offset ?: 0, limit, ::compareByDistanceNullsFirst).map { (s, _) -> s }
    } else {
        page(switches.map { (s, _) -> s }, offset ?: 0, limit, Comparator.comparing(TrackLayoutSwitch::name))
    }
}

fun associateByDistance(
    switch: TrackLayoutSwitch,
    structure: SwitchStructure,
    comparisonPoint: Point,
): Pair<TrackLayoutSwitch, Double?> {
    val location = switch.getJoint(structure.presentationJointNumber)?.location
    return switch to location?.let { l -> calculateDistance(LAYOUT_SRID, comparisonPoint, l) }
}

fun <T> compareByDistanceNullsFirst(
    itemAndDistance1: Pair<T, Double?>,
    itemAndDistance2: Pair<T, Double?>,
): Int {
    val (_, dist1) = itemAndDistance1
    val (_, dist2) = itemAndDistance2
    return when {
        (dist1 === null && dist2 === null) -> 0
        (dist1 === null) -> -1
        (dist2 === null) -> 1
        else -> compareValues(dist1, dist2)
    }
}

fun clearSwitchInformationFromSegments(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    layoutSwitchId: IntId<TrackLayoutSwitch>,
): Pair<LocationTrack, LayoutAlignment> {
    val newSegments = alignment.segments.map { segment ->
        if (segment.switchId == layoutSwitchId) segment.withoutSwitch()
        else segment
    }
    val newAlignment = alignment.withSegments(newSegments)
    val newLocationTrack = locationTrack.copy(
        topologyStartSwitch = locationTrack.topologyStartSwitch?.takeIf { s -> s.switchId != layoutSwitchId },
        topologyEndSwitch = locationTrack.topologyEndSwitch?.takeIf { s -> s.switchId != layoutSwitchId },
    )
    return newLocationTrack to newAlignment
}

private fun getTopologyPoints(switchId: IntId<TrackLayoutSwitch>, track: LocationTrack, alignment: LayoutAlignment) =
    listOfNotNull(
        topologyPointOrNull(switchId, track.topologyStartSwitch, alignment.start),
        topologyPointOrNull(switchId, track.topologyEndSwitch, alignment.end),
    )

private fun topologyPointOrNull(
    switchId: IntId<TrackLayoutSwitch>,
    topology: TopologyLocationTrackSwitch?,
    location: IPoint?,
) = if (topology?.switchId == switchId && location != null) topology to location.toPoint() else null

fun switchFilter(
    namePart: String? = null,
    exactName: SwitchName? = null,
    switchType: String? = null,
    bbox: BoundingBox? = null,
    includeSwitchesWithNoJoints: Boolean = false,
) = { (switch, structure): Pair<TrackLayoutSwitch, SwitchStructure> ->
    switchMatchesName(switch, namePart, exactName) && structureMatchesType(structure, switchType) && switchMatchesBbox(
        switch, bbox, includeSwitchesWithNoJoints
    )
}

private fun switchMatchesName(switch: TrackLayoutSwitch, partial: String?, exact: SwitchName?) =
    exact?.equalsIgnoreCase(switch.name) ?: partial?.let { n -> switch.name.contains(n, ignoreCase = true) } ?: true

private fun structureMatchesType(structure: SwitchStructure, searchString: String?) = searchString?.let { t ->
    structure.type.typeName.contains(t, ignoreCase = true)
} ?: true

private fun switchMatchesBbox(switch: TrackLayoutSwitch, bbox: BoundingBox?, includeSwitchesWithNoJoints: Boolean) =
    (includeSwitchesWithNoJoints && switch.joints.isEmpty()) || (bbox?.let { bb ->
        (switch.joints.any { joint -> bb.contains(joint.location) })
    } ?: true)
