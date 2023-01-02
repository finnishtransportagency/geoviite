package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.linking.TrackLayoutSwitchSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.pageToList
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
        val layoutSwitch = getDraft(id);
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
            joints = switchJoints
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
            "getSegmentSwitchJointConnections",
            "publishType" to publishType,
            "switchId" to switchId
        )
        return dao.fetchSegmentSwitchJointConnections(publishType, switchId)
    }

    fun getPresentationJoint(switch: TrackLayoutSwitch): TrackLayoutSwitchJoint? {
        val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
        return switch.getJoint(structure.presentationJointNumber)
    }

    fun list(publishType: PublishType, filter: (switch: TrackLayoutSwitch) -> Boolean): List<TrackLayoutSwitch> {
        logger.serviceCall("list", "publishType" to publishType, "filter" to true)
        return listInternal(publishType, false).filter(filter)
    }

    fun list(publishType: PublishType, searchTerm: FreeText, limit: Int?): List<TrackLayoutSwitch> {
        logger.serviceCall("list",
            "publishType" to publishType, "searchTerm" to searchTerm, "limit" to limit)
        return searchTerm
            .toString()
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { term ->
                listInternal(publishType, true)
                    .filter { switch -> idMatches(term, switch) || contentMatches(term, switch) }
                    .sortedBy(TrackLayoutSwitch::name)
                    .let { list -> if (limit != null) list.take(limit) else list }
        } ?: listOf()
    }

    private fun idMatches(term: String, switch: TrackLayoutSwitch) =
        switch.externalId.toString() == term || switch.id.toString() == term

    private fun contentMatches(term: String, switch: TrackLayoutSwitch) =
        switch.exists && switch.name.toString().replace("  ", " ").contains(term, true)

    fun pageSwitches(
        switches: List<TrackLayoutSwitch>,
        offset: Int,
        limit: Int?,
        comparisonPoint: Point?,
    ): List<TrackLayoutSwitch> =
        if (comparisonPoint != null) {
            val switchesWithDistance: List<Pair<TrackLayoutSwitch, Double?>> = switches
                .map { associateByDistance(it, comparisonPoint) { s -> getPresentationJoint(s)?.location } }
            pageToList(switchesWithDistance, offset, limit, ::compareByDistanceNullsFirst)
                .map { (switch, _) -> switch }
        } else {
            pageToList(switches, offset, limit, Comparator.comparing(TrackLayoutSwitch::name))
        }

    fun switchFilter(
        name: String? = null,
        switchType: String? = null,
        bbox: BoundingBox? = null,
        includeSwitchesWithNoJoints: Boolean = false,
    ) = { switch: TrackLayoutSwitch ->
        switchMatchesName(switch, name)
                && switchMatchesType(switch, switchType)
                && switchMatchesBbox(switch, bbox, includeSwitchesWithNoJoints)
    }

    @Transactional
    fun updateExternalIdForSwitch(
        id: IntId<TrackLayoutSwitch>,
        oid: Oid<TrackLayoutSwitch>,
    ): DaoResponse<TrackLayoutSwitch> {
        logger.serviceCall("updateExternalIdForSwitch", "id" to id, "oid" to oid)
        val original = getDraft(id)
        return saveDraft(original.copy(externalId = oid))
    }

    private fun switchMatchesName(switch: TrackLayoutSwitch, name: String?) =
        name?.let { n -> switch.name.contains(n, ignoreCase = true) } ?: true

    private fun switchMatchesType(switch: TrackLayoutSwitch, switchType: String?) =
        switchType?.let { t ->
            switchLibraryService.getSwitchType(switch.switchStructureId).typeName.contains(t, ignoreCase = true)
        } ?: true

    private fun switchMatchesBbox(switch: TrackLayoutSwitch, bbox: BoundingBox?, includeSwitchesWithNoJoints: Boolean) =
        (includeSwitchesWithNoJoints && switch.joints.isEmpty()) ||
                (bbox?.let { bb -> (switch.joints.any { joint -> bb.contains(joint.location) }) } ?: true)

    override fun createDraft(item: TrackLayoutSwitch) = draft(item)

    override fun createPublished(item: TrackLayoutSwitch) = published(item)

    @Transactional(readOnly = true)
    fun getSwitchJointConnections(
        publishType: PublishType,
        switchId: IntId<TrackLayoutSwitch>
    ): List<TrackLayoutSwitchJointConnection> {
        logger.serviceCall(
            "getSwitchJointConnections",
            "publishType" to publishType,
            "switchId" to switchId
        )
        val segment = getSegmentSwitchJointConnections(publishType, switchId)
        val topological = getTopologySwitchJointConnections(publishType, switchId)
        return (segment + topological)
            .groupBy { joint -> joint.number }
            .values.map { jointConnections ->
                jointConnections.reduceRight(TrackLayoutSwitchJointConnection::merge)
            }
    }

    private fun getTopologySwitchJointConnections(
        publicationState: PublishType,
        layoutSwitchId: IntId<TrackLayoutSwitch>
    ): List<TrackLayoutSwitchJointConnection> {
        val layoutSwitch = get(publicationState, layoutSwitchId) ?: return listOf()
        val linkedTracks = getLocationTracksLinkedToSwitch(publicationState, layoutSwitchId)
        return linkedTracks.flatMap { (track, alignment) ->
            getTopologyPoints(layoutSwitchId, track, alignment).mapNotNull { (connection, point) ->
                layoutSwitch.getJoint(connection.jointNumber)?.let { joint ->
                    TrackLayoutSwitchJointConnection(
                        connection.jointNumber,
                        listOf(TrackLayoutSwitchJointMatch(track.id as IntId, point)),
                        listOf(),
                        joint.locationAccuracy
                    )
                }
            }
        }
    }

    private fun getLocationTracksLinkedToSwitch(
        publicationState: PublishType,
        layoutSwitchId: IntId<TrackLayoutSwitch>
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return dao.findLocationTracksLinkedToSwitch(publicationState, layoutSwitchId)
            .map { ids -> locationTrackService.getWithAlignment(ids.rowVersion) }
    }
}

fun <T> associateByDistance(
    item: T,
    comparisonPoint: Point,
    fetchPointFromItem: (item: T) -> Point?,
): Pair<T, Double?> {
    val point = fetchPointFromItem(item)
    return if (point != null) item to calculateDistance(LAYOUT_SRID, point, comparisonPoint) else item to null
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
        if (segment.switchId == layoutSwitchId) {
            segment.copy(
                switchId = null,
                endJointNumber = null,
                startJointNumber = null
            )
        } else {
            segment
        }
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
