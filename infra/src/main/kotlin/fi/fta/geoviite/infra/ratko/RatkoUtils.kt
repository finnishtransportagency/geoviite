package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.*

private fun sameNodeAddress(node1: RatkoNode?, node2: RatkoNode?): Boolean {
    if (node1 == null || node2 == null) {
        return false
    }

    val geometry1 = node1.point.geometry
    val geometry2 = node2.point.geometry

    return node2.point.kmM.isSame(node1.point.kmM) &&
            if (geometry1 == null) geometry2 == null
            else if (geometry2 == null) false
            else isSame(
                geometry1.coordinates[0],
                geometry2.coordinates[0],
                LAYOUT_COORDINATE_DELTA,
            ) && isSame(
                geometry1.coordinates[1],
                geometry2.coordinates[1],
                LAYOUT_COORDINATE_DELTA,
            )

}

fun getEndPointNodeCollection(
    alignmentAddresses: AlignmentAddresses,
    changedKmNumbers: Set<KmNumber>,
    existingStartNode: RatkoNode? = null,
    existingEndNode: RatkoNode? = null,
): RatkoNodes? {
    val layoutStartNode = convertToRatkoNode(
        addressPoint = alignmentAddresses.startPoint,
        nodeType = RatkoNodeType.START_POINT,
        state = RatkoPointStates.VALID,
    )

    val layoutEndNode = convertToRatkoNode(
        addressPoint = alignmentAddresses.endPoint,
        nodeType = RatkoNodeType.END_POINT,
        state = RatkoPointStates.VALID,
    )

    val startHasChanged = changedKmNumbers.contains(alignmentAddresses.startPoint.address.kmNumber)
    val endHasChanged = changedKmNumbers.contains(alignmentAddresses.endPoint.address.kmNumber)

    return if (startHasChanged || endHasChanged) {
        val newStartNode = if (startHasChanged || existingStartNode == null) layoutStartNode
        else existingStartNode.withoutGeometry()

        val newEndNode = if (endHasChanged || existingEndNode == null) layoutEndNode
        else existingEndNode.withoutGeometry()

        return convertToRatkoNodeCollection(listOf(newStartNode, newEndNode))
    } else null
}

fun getNotInUseEndPointNodeCollection(alignmentAddresses: AlignmentAddresses): RatkoNodes {
    val layoutStartNode = convertToRatkoNode(
        addressPoint = alignmentAddresses.startPoint,
        nodeType = RatkoNodeType.START_POINT,
        state = RatkoPointStates.NOT_IN_USE,
    )

    val layoutEndNode = convertToRatkoNode(
        addressPoint = alignmentAddresses.endPoint,
        nodeType = RatkoNodeType.END_POINT,
        state = RatkoPointStates.NOT_IN_USE,
    )

    return convertToRatkoNodeCollection(listOf(layoutStartNode, layoutEndNode))
}

fun asSwitchTypeString(switchType: SwitchType): String {
    val radius = switchType.parts.curveRadius
    val spread = switchType.parts.spread ?: ""
    val curveRadius = radius
        .mapIndexed { i, r ->
            //Adds N/P version to the first curve radius
            if (i == 0) "$r$spread"
            else "$r"
        }.let { radii ->
            if (radii.isEmpty()) ""
            else "-${radii.joinToString("/")}"
        }

    return "${switchType.parts.baseType}${switchType.parts.railWeight}${curveRadius}-${switchType.parts.ratio}"
}

fun sortByDeletedStateFirst(layoutState: LayoutState) = if (layoutState == LayoutState.DELETED) 0 else 1
fun sortByNullDuplicateOfFirst(duplicateOf: IntId<LocationTrack>?) = if (duplicateOf == null) 0 else 1

fun sortByDeletedStateFirst(layoutStateCategory: LayoutStateCategory) =
    if (layoutStateCategory == LayoutStateCategory.NOT_EXISTING) 0 else 1

fun toRatkoPointsGroupedByKm(addressPoints: List<AddressPoint>) =
    addressPoints
        .groupBy { point -> point.address.kmNumber }
        .map { (_, addressPointsForKm) ->
            addressPointsForKm
                .map(::convertToRatkoPoint)
                .distinctBy { it.kmM.meters } //track meters 0000+0000.010 and 0000+0000.01 are considered the same
        }
        .filter { ratkoPoints -> ratkoPoints.isNotEmpty() }
