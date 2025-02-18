package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.linking.switches.GeoviiteSwitchOidPresence
import fi.fta.geoviite.infra.linking.switches.LayoutSwitchSaveRequest
import fi.fta.geoviite.infra.linking.switches.SwitchOidPresence
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.RatkoClient
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.Page
import fi.fta.geoviite.infra.util.mapNonNullValues
import fi.fta.geoviite.infra.util.page
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class LayoutSwitchService
@Autowired
constructor(
    dao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val locationTrackService: LocationTrackService,
    private val ratkoClient: RatkoClient?,
) : LayoutAssetService<LayoutSwitch, LayoutSwitchDao>(dao) {

    @Transactional
    fun insertSwitch(branch: LayoutBranch, request: LayoutSwitchSaveRequest): IntId<LayoutSwitch> {
        val switch =
            LayoutSwitch(
                name = request.name,
                switchStructureId = request.switchStructureId,
                stateCategory = request.stateCategory,
                joints = listOf(),
                sourceId = null,
                trapPoint = request.trapPoint,
                ownerId = request.ownerId,
                source = GeometrySource.GENERATED,
                contextData = LayoutContextData.newDraft(branch, id = null),
                draftOid = request.draftOid,
            )

        return saveDraftInternal(branch, switch).id
    }

    @Transactional
    fun updateSwitch(
        branch: LayoutBranch,
        id: IntId<LayoutSwitch>,
        switch: LayoutSwitchSaveRequest,
    ): IntId<LayoutSwitch> {
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
                draftOid = switch.draftOid,
            )
        return saveDraftInternal(branch, updatedLayoutSwitch).id
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<LayoutSwitch>): LayoutRowVersion<LayoutSwitch> {
        // If removal also breaks references, clear them out first
        if (dao.fetchVersion(branch.official, id) == null) {
            clearSwitchInformationFromSegments(branch, id)
        }
        return super.deleteDraft(branch, id)
    }

    @Transactional
    fun clearSwitchInformationFromSegments(branch: LayoutBranch, layoutSwitchId: IntId<LayoutSwitch>) {
        getLocationTracksLinkedToSwitch(branch.draft, layoutSwitchId).forEach { (locationTrack, alignment) ->
            val (updatedLocationTrack, updatedAlignment) = clearLinksToSwitch(locationTrack, alignment, layoutSwitchId)
            locationTrackService.saveDraft(branch, updatedLocationTrack, updatedAlignment)
        }
    }

    fun getSegmentSwitchJointConnections(
        layoutContext: LayoutContext,
        switchId: IntId<LayoutSwitch>,
    ): List<LayoutSwitchJointConnection> {
        return dao.fetchSegmentSwitchJointConnections(layoutContext, switchId)
    }

    @Transactional(readOnly = true)
    fun listWithStructure(
        layoutContext: LayoutContext,
        includeDeleted: Boolean = false,
    ): List<Pair<LayoutSwitch, SwitchStructure>> {
        return dao.list(layoutContext, includeDeleted).map(::withStructure)
    }

    fun checkOidPresence(oid: Oid<LayoutSwitch>) =
        SwitchOidPresence(
            existsInRatko = checkRatkoOidPresence(oid),
            existsInGeoviiteAs =
                dao.lookupByExternalId(oid)?.let { rowByOid ->
                    dao.get(rowByOid.context, rowByOid.id)?.let { existingSwitch ->
                        GeoviiteSwitchOidPresence(rowByOid.id, existingSwitch.stateCategory, existingSwitch.name)
                    }
                },
        )

    private fun checkRatkoOidPresence(oid: Oid<LayoutSwitch>): Boolean? {
        return try {
            ratkoClient?.getSwitchAsset(RatkoOid(oid.toString())) != null
        } catch (ex: Exception) {
            logger.warn("checkRatkoOidPresence exception: $ex")
            null
        }
    }

    fun idMatches(
        layoutContext: LayoutContext,
        possibleIds: List<IntId<LayoutSwitch>>? = null,
    ): ((term: String, item: LayoutSwitch) -> Boolean) =
        dao.fetchExternalIds(layoutContext.branch, possibleIds).let { externalIds ->
            { term, item -> externalIds[item.id]?.oid?.toString() == term || item.id.toString() == term }
        }

    override fun contentMatches(term: String, item: LayoutSwitch) =
        item.exists && item.name.toString().replace("  ", " ").contains(term, true)

    @Transactional
    fun insertExternalIdForSwitch(branch: LayoutBranch, id: IntId<LayoutSwitch>, oid: Oid<LayoutSwitch>) =
        dao.insertExternalId(id, branch, oid)

    private fun withStructure(switch: LayoutSwitch): Pair<LayoutSwitch, SwitchStructure> =
        switch to switchLibraryService.getSwitchStructure(switch.switchStructureId)

    @Transactional(readOnly = true)
    fun getSwitchJointConnections(
        layoutContext: LayoutContext,
        switchId: IntId<LayoutSwitch>,
    ): List<LayoutSwitchJointConnection> {
        val segment = getSegmentSwitchJointConnections(layoutContext, switchId)
        val topological = getTopologySwitchJointConnections(layoutContext, switchId)
        return (segment + topological)
            .groupBy { joint -> joint.number }
            .values
            .map { jointConnections -> jointConnections.reduceRight(LayoutSwitchJointConnection::merge) }
    }

    private fun getTopologySwitchJointConnections(
        layoutContext: LayoutContext,
        layoutSwitchId: IntId<LayoutSwitch>,
    ): List<LayoutSwitchJointConnection> {
        val layoutSwitch = get(layoutContext, layoutSwitchId) ?: return listOf()
        val linkedTracks = getLocationTracksLinkedToSwitch(layoutContext, layoutSwitchId)
        return linkedTracks.flatMap { (track, alignment) ->
            getTopologyPoints(layoutSwitchId, track, alignment).mapNotNull { (connection, point) ->
                layoutSwitch.getJoint(connection.jointNumber)?.let { joint ->
                    LayoutSwitchJointConnection(
                        connection.jointNumber,
                        listOf(LayoutSwitchJointMatch(track.id as IntId, point)),
                        joint.locationAccuracy,
                    )
                }
            }
        }
    }

    private fun getLocationTracksLinkedToSwitch(
        layoutContext: LayoutContext,
        layoutSwitchId: IntId<LayoutSwitch>,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        return dao.findLocationTracksLinkedToSwitch(layoutContext, layoutSwitchId).map { ids ->
            locationTrackService.getWithAlignment(ids.rowVersion)
        }
    }

    fun getExternalIdChangeTime(): Instant = dao.getExternalIdChangeTime()

    @Transactional(readOnly = true)
    fun getExternalIdsByBranch(id: IntId<LayoutSwitch>): Map<LayoutBranch, Oid<LayoutSwitch>> =
        mapNonNullValues(dao.fetchExternalIdsByBranch(id)) { (_, v) -> v.oid }
}

fun pageSwitches(
    switches: List<Pair<LayoutSwitch, SwitchStructure>>,
    offset: Int?,
    limit: Int?,
    comparisonPoint: Point?,
): Page<LayoutSwitch> {
    return if (comparisonPoint != null) {
        val switchesWithDistance: List<Pair<LayoutSwitch, Double?>> =
            switches.map { (switch, structure) -> associateByDistance(switch, structure, comparisonPoint) }
        page(switchesWithDistance, offset ?: 0, limit, ::compareByDistanceNullsFirst).map { (s, _) -> s }
    } else {
        page(switches.map { (s, _) -> s }, offset ?: 0, limit, Comparator.comparing(LayoutSwitch::name))
    }
}

fun associateByDistance(
    switch: LayoutSwitch,
    structure: SwitchStructure,
    comparisonPoint: Point,
): Pair<LayoutSwitch, Double?> {
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
    layoutSwitchId: IntId<LayoutSwitch>,
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
    switchId: IntId<LayoutSwitch>,
    track: LocationTrack,
    alignment: LayoutAlignment,
): List<Pair<TopologyLocationTrackSwitch, Point>> =
    listOfNotNull(
        topologyPointOrNull(switchId, track.topologyStartSwitch, alignment.firstSegmentStart),
        topologyPointOrNull(switchId, track.topologyEndSwitch, alignment.lastSegmentEnd),
    )

private fun topologyPointOrNull(
    switchId: IntId<LayoutSwitch>,
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
) = { (switch, structure): Pair<LayoutSwitch, SwitchStructure> ->
    switchMatchesName(switch, namePart, exactName) &&
        structureMatchesType(structure, switchType) &&
        switchMatchesBbox(switch, bbox, includeSwitchesWithNoJoints)
}

private fun switchMatchesName(switch: LayoutSwitch, partial: String?, exact: SwitchName?) =
    exact?.equalsIgnoreCase(switch.name) ?: partial?.let { n -> switch.name.contains(n, ignoreCase = true) } ?: true

private fun structureMatchesType(structure: SwitchStructure, searchString: String?) =
    searchString?.let { t -> structure.type.typeName.contains(t, ignoreCase = true) } ?: true

fun switchMatchesBbox(switch: LayoutSwitch, bbox: BoundingBox?, includeSwitchesWithNoJoints: Boolean) =
    (includeSwitchesWithNoJoints && switch.joints.isEmpty()) ||
        (bbox?.let { bb -> (switch.joints.any { joint -> bb.contains(joint.location) }) } ?: true)
