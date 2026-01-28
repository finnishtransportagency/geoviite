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
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.RatkoClient
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.FreeText
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
) : LayoutAssetService<LayoutSwitch, NoParams, LayoutSwitchDao>(dao) {

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
                operationalPointId = null,
            )

        return saveDraft(branch, switch).id
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

        if (switchStructureChanged) {
            clearSwitchInformationFromTracks(branch, id)
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
        return saveDraft(branch, updatedLayoutSwitch).id
    }

    @Transactional
    fun linkToOperationalPoint(
        branch: LayoutBranch,
        ids: List<IntId<LayoutSwitch>>,
        operationalPointId: IntId<OperationalPoint>,
    ): List<IntId<LayoutSwitch>> =
        ids.map { id ->
            saveDraft(branch, dao.getOrThrow(branch.draft, id).copy(operationalPointId = operationalPointId)).id
        }

    @Transactional
    fun unlinkFromOperationalPoint(branch: LayoutBranch, ids: List<IntId<LayoutSwitch>>): List<IntId<LayoutSwitch>> =
        ids.map { id -> saveDraft(branch, dao.getOrThrow(branch.draft, id).copy(operationalPointId = null)).id }

    fun findSwitchesRelatedToOperationalPoint(
        context: LayoutContext,
        operationalPointId: IntId<OperationalPoint>,
    ): List<SwitchWithOperationalPointPolygonInclusions> =
        dao.findSwitchesRelatedToOperationalPoint(context, operationalPointId)

    @Transactional
    fun clearSwitchInformationFromTracks(branch: LayoutBranch, layoutSwitchId: IntId<LayoutSwitch>) {
        getLocationTracksLinkedToSwitch(branch.draft, layoutSwitchId).forEach { (track, geometry) ->
            locationTrackService.saveDraft(branch, track, geometry.withoutSwitch(layoutSwitchId))
        }
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
        if (ratkoClient != null)
            return try {
                ratkoClient.getSwitchAsset(RatkoOid(oid.toString())) != null
            } catch (ex: Exception) {
                logger.warn("checkRatkoOidPresence exception: $ex")
                null
            }
        else return null
    }

    fun idMatches(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        onlyIds: Collection<IntId<LayoutSwitch>>? = null,
    ): ((term: String, item: LayoutSwitch) -> Boolean) = idMatches(dao, layoutContext, searchTerm, onlyIds)

    override fun contentMatches(term: String, item: LayoutSwitch, includeDeleted: Boolean) =
        (includeDeleted || item.exists) && item.name.toString().replace("  ", " ").contains(term, true)

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
        return dao.fetchSwitchJointConnections(layoutContext, switchId)
            .groupBy { joint -> joint.number }
            .values
            .map { jointConnections -> jointConnections.reduceRight(LayoutSwitchJointConnection::merge) }
    }

    fun getLocationTracksLinkedToSwitch(
        layoutContext: LayoutContext,
        switchId: IntId<LayoutSwitch>,
    ): List<Pair<LocationTrack, LocationTrackGeometry>> {
        return dao.findLocationTracksLinkedToSwitch(layoutContext, switchId)
            .map { ids -> ids.rowVersion }
            .let(locationTrackService::getManyWithGeometries)
    }

    fun getExternalIdChangeTime(): Instant = dao.getExternalIdChangeTime()

    @Transactional(readOnly = true)
    fun getExternalIdsByBranch(id: IntId<LayoutSwitch>): Map<LayoutBranch, Oid<LayoutSwitch>> =
        mapNonNullValues(dao.fetchExternalIdsByBranch(id)) { (_, v) -> v.oid }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<LayoutSwitch>): LayoutRowVersion<LayoutSwitch> =
        deleteDraft(branch, id, noUpdateLocationTracks = setOf())

    @Transactional
    fun deleteDraft(
        branch: LayoutBranch,
        id: IntId<LayoutSwitch>,
        noUpdateLocationTracks: Set<IntId<LocationTrack>>,
    ): LayoutRowVersion<LayoutSwitch> {
        // If removal also breaks references, clear them out first
        if (dao.fetchVersion(branch.official, id) == null) {
            clearSwitchInformationFromTracks(branch, id)
        }
        return super.deleteDraft(branch, id).also { v ->
            locationTrackService.updateDependencies(branch, noUpdateLocationTracks, switchId = v.id)
        }
    }

    @Transactional
    fun saveDraft(branch: LayoutBranch, draftAsset: LayoutSwitch): LayoutRowVersion<LayoutSwitch> =
        saveDraftInternal(branch, draftAsset, NoParams.instance)

    @Transactional
    override fun saveDraftInternal(
        branch: LayoutBranch,
        draftAsset: LayoutSwitch,
        params: NoParams,
    ): LayoutRowVersion<LayoutSwitch> =
        super.saveDraftInternal(branch, draftAsset, params).also { v ->
            locationTrackService.updateDependencies(branch, switchId = v.id, noUpdateLocationTracks = setOf())
        }
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

data class SwitchWithOperationalPointPolygonInclusions(
    val switchId: IntId<LayoutSwitch>,
    val withinPolygon: List<IntId<OperationalPoint>>,
)
