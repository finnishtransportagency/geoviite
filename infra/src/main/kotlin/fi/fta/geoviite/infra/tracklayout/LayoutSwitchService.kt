package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.util.RowVersion
import fi.fta.geoviite.infra.util.pageToList
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class LayoutSwitchService @Autowired constructor(
    dao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
) : DraftableObjectService<TrackLayoutSwitch, LayoutSwitchDao>(dao) {

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

    override fun listInternal(publishType: PublishType) =
        dao.fetchVersions(publishType)
            .map(dao::fetch)
            .filter(TrackLayoutSwitch::exists)

    fun list(
        publishType: PublishType,
        filter: (switch: TrackLayoutSwitch) -> Boolean,
    ): List<TrackLayoutSwitch> {
        logger.serviceCall("list", "publishType" to publishType, "filter" to true)
        return listInternal(publishType).filter { s -> s.exists && filter(s) }
    }

    fun list(
        publishType: PublishType,
        searchTerm: String,
        limit: Int?,
    ): List<TrackLayoutSwitch> {
        logger.serviceCall(
            "list",
            "publishType" to publishType, "searchTerm" to searchTerm, "limit" to limit
        )
        return dao.fetchVersions(publishType)
            .map(dao::fetch)
            .filter { switch ->
                switch.externalId.toString() == searchTerm ||
                        switch.exists &&
                        switch.name.contains(searchTerm, true)
            }
            .sortedBy { switch -> switch.name }
            .let { list -> if (limit != null) list.take(limit) else list }
    }

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

    fun updateExternalIdForSwitch(
        id: IntId<TrackLayoutSwitch>,
        oid: Oid<TrackLayoutSwitch>,
    ): RowVersion<TrackLayoutSwitch> {
        logger.serviceCall("updateExternalIdForSwitch", "id" to id, "oid" to oid)
        val original = getDraft(id)
        return saveDraft(
            original.copy(
                externalId = oid,
            )
        )
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
