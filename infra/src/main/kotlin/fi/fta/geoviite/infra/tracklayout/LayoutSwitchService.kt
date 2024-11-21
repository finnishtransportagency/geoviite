package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.linking.TrackLayoutSwitchSaveRequest
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.Page
import fi.fta.geoviite.infra.util.page
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class LayoutSwitchService
@Autowired
constructor(
    dao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val locationTrackService: LocationTrackService,
) : LayoutAssetService<TrackLayoutSwitch, LayoutSwitchDao>(dao) {

    @Transactional
    fun insertSwitch(branch: LayoutBranch, request: TrackLayoutSwitchSaveRequest): IntId<TrackLayoutSwitch> {
        val switch =
            TrackLayoutSwitch(
                name = request.name,
                switchStructureId = request.switchStructureId,
                stateCategory = request.stateCategory,
                joints = listOf(),
                externalId = null,
                sourceId = null,
                trapPoint = request.trapPoint,
                ownerId = request.ownerId,
                source = GeometrySource.GENERATED,
                contextData = LayoutContextData.newDraft(branch, id = null),
            )

        return saveDraftInternal(branch, switch).id
    }

    @Transactional
    fun updateSwitch(
        branch: LayoutBranch,
        id: IntId<TrackLayoutSwitch>,
        switch: TrackLayoutSwitchSaveRequest,
    ): IntId<TrackLayoutSwitch> {
        val layoutSwitch = dao.getOrThrow(branch.draft, id)
        val switchStructureChanged = switch.switchStructureId != layoutSwitch.switchStructureId
        val switchJoints = if (switchStructureChanged) emptyList() else layoutSwitch.joints

        if (switch.stateCategory == LayoutStateCategory.NOT_EXISTING || switchStructureChanged) {
            clearSwitchInformationFromSegments(branch, id)
        }

        val updatedLayoutSwitch =
            layoutSwitch.copy(
                name = switch.name,
                switchStructureId = switch.switchStructureId,
                stateCategory = switch.stateCategory,
                trapPoint = switch.trapPoint,
                joints = switchJoints,
                ownerId = switch.ownerId,
            )
        return saveDraftInternal(branch, updatedLayoutSwitch).id
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<TrackLayoutSwitch>): LayoutRowVersion<TrackLayoutSwitch> {
        val draft = dao.getOrThrow(branch.draft, id)
        // If removal also breaks references, clear them out first
        if (!draft.contextData.hasOfficial) {
            clearSwitchInformationFromSegments(branch, id)
        }
        return super.deleteDraft(branch, id)
    }

    @Transactional
    fun clearSwitchInformationFromSegments(branch: LayoutBranch, layoutSwitchId: IntId<TrackLayoutSwitch>) {
        getLocationTracksLinkedToSwitch(branch.draft, layoutSwitchId).forEach { (locationTrack, alignment) ->
            val (updatedLocationTrack, updatedAlignment) = clearLinksToSwitch(locationTrack, alignment, layoutSwitchId)
            locationTrackService.saveDraft(branch, updatedLocationTrack, updatedAlignment)
        }
    }

    fun getSegmentSwitchJointConnections(
        layoutContext: LayoutContext,
        switchId: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        return dao.fetchSegmentSwitchJointConnections(layoutContext, switchId)
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
        layoutContext: LayoutContext,
        includeDeleted: Boolean = false,
    ): List<Pair<TrackLayoutSwitch, SwitchStructure>> {
        return dao.list(layoutContext, includeDeleted).map(::withStructure)
    }

    override fun idMatches(term: String, item: TrackLayoutSwitch) =
        item.externalId.toString() == term || item.id.toString() == term

    override fun contentMatches(term: String, item: TrackLayoutSwitch) =
        item.exists && item.name.toString().replace("  ", " ").contains(term, true)

    @Transactional
    fun updateExternalIdForSwitch(
        branch: LayoutBranch,
        id: IntId<TrackLayoutSwitch>,
        oid: Oid<TrackLayoutSwitch>,
    ): LayoutRowVersion<TrackLayoutSwitch> {
        val original = dao.getOrThrow(branch.draft, id)
        return saveDraft(branch, original.copy(externalId = oid))
    }

    private fun withStructure(switch: TrackLayoutSwitch): Pair<TrackLayoutSwitch, SwitchStructure> =
        switch to switchLibraryService.getSwitchStructure(switch.switchStructureId)

    @Transactional(readOnly = true)
    fun getSwitchJointConnections(
        layoutContext: LayoutContext,
        switchId: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        val segment = getSegmentSwitchJointConnections(layoutContext, switchId)
        val topological = getTopologySwitchJointConnections(layoutContext, switchId)
        return (segment + topological)
            .groupBy { joint -> joint.number }
            .values
            .map { jointConnections -> jointConnections.reduceRight(TrackLayoutSwitchJointConnection::merge) }
    }

    private fun getTopologySwitchJointConnections(
        layoutContext: LayoutContext,
        layoutSwitchId: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        val layoutSwitch = get(layoutContext, layoutSwitchId) ?: return listOf()
        val linkedTracks = getLocationTracksLinkedToSwitch(layoutContext, layoutSwitchId)
        return linkedTracks.flatMap { (track, alignment) ->
            getTopologyPoints(layoutSwitchId, track, alignment).mapNotNull { (connection, point) ->
                layoutSwitch.getJoint(connection.jointNumber)?.let { joint ->
                    TrackLayoutSwitchJointConnection(
                        connection.jointNumber,
                        listOf(TrackLayoutSwitchJointMatch(track.id as IntId, point)),
                        joint.locationAccuracy,
                    )
                }
            }
        }
    }

    private fun getLocationTracksLinkedToSwitch(
        layoutContext: LayoutContext,
        layoutSwitchId: IntId<TrackLayoutSwitch>,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return dao.findLocationTracksLinkedToSwitch(layoutContext, layoutSwitchId).map { ids ->
            locationTrackService.getWithAlignment(ids.rowVersion)
        }
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

fun <T> compareByDistanceNullsFirst(itemAndDistance1: Pair<T, Double?>, itemAndDistance2: Pair<T, Double?>): Int {
    val (_, dist1) = itemAndDistance1
    val (_, dist2) = itemAndDistance2
    return when {
        (dist1 === null && dist2 === null) -> 0
        (dist1 === null) -> -1
        (dist2 === null) -> 1
        else -> compareValues(dist1, dist2)
    }
}

fun clearLinksToSwitch(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    layoutSwitchId: IntId<TrackLayoutSwitch>,
): Pair<LocationTrack, LayoutAlignment> {
    val newSegments =
        alignment.segments.map { segment ->
            if (segment.switchId == layoutSwitchId) segment.withoutSwitch() else segment
        }
    val newAlignment = alignment.withSegments(newSegments)
    val newLocationTrack =
        locationTrack.copy(
            topologyStartSwitch = locationTrack.topologyStartSwitch?.takeIf { s -> s.switchId != layoutSwitchId },
            topologyEndSwitch = locationTrack.topologyEndSwitch?.takeIf { s -> s.switchId != layoutSwitchId },
        )
    return newLocationTrack to (if (newSegments === alignment.segments) alignment else newAlignment)
}

private fun getTopologyPoints(
    switchId: IntId<TrackLayoutSwitch>,
    track: LocationTrack,
    alignment: LayoutAlignment,
): List<Pair<TopologyLocationTrackSwitch, Point>> =
    listOfNotNull(
        topologyPointOrNull(switchId, track.topologyStartSwitch, alignment.firstSegmentStart),
        topologyPointOrNull(switchId, track.topologyEndSwitch, alignment.lastSegmentEnd),
    )

private fun topologyPointOrNull(
    switchId: IntId<TrackLayoutSwitch>,
    topology: TopologyLocationTrackSwitch?,
    location: IPoint?,
): Pair<TopologyLocationTrackSwitch, Point>? =
    if (topology?.switchId == switchId && location != null) topology to location.toPoint() else null

fun switchFilter(
    namePart: String? = null,
    exactName: SwitchName? = null,
    switchType: String? = null,
    bbox: BoundingBox? = null,
    includeSwitchesWithNoJoints: Boolean = false,
) = { (switch, structure): Pair<TrackLayoutSwitch, SwitchStructure> ->
    switchMatchesName(switch, namePart, exactName) &&
        structureMatchesType(structure, switchType) &&
        switchMatchesBbox(switch, bbox, includeSwitchesWithNoJoints)
}

private fun switchMatchesName(switch: TrackLayoutSwitch, partial: String?, exact: SwitchName?) =
    exact?.equalsIgnoreCase(switch.name) ?: partial?.let { n -> switch.name.contains(n, ignoreCase = true) } ?: true

private fun structureMatchesType(structure: SwitchStructure, searchString: String?) =
    searchString?.let { t -> structure.type.typeName.contains(t, ignoreCase = true) } ?: true

fun switchMatchesBbox(switch: TrackLayoutSwitch, bbox: BoundingBox?, includeSwitchesWithNoJoints: Boolean) =
    (includeSwitchesWithNoJoints && switch.joints.isEmpty()) ||
        (bbox?.let { bb -> (switch.joints.any { joint -> bb.contains(joint.location) }) } ?: true)
