package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.DesignRatkoExternalId
import fi.fta.geoviite.infra.common.FullRatkoExternalId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainBranchRatkoExternalId
import fi.fta.geoviite.infra.common.RatkoExternalId
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.switchLibrary.SwitchHand
import fi.fta.geoviite.infra.switchLibrary.SwitchNationality
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackState

fun <T : LayoutAsset<T>> getFullExtIdAndManagePlanItem(
    branch: PushableLayoutBranch,
    id: IntId<T>,
    designAssetState: DesignAssetState?,
    ratkoClient: RatkoClient,
    fetchSavedExternalId: (branch: LayoutBranch, id: IntId<T>) -> RatkoExternalId<T>?,
    insertPlanItemId: (id: IntId<T>, branch: DesignBranch, planItemId: RatkoPlanItemId) -> Unit,
): FullRatkoExternalId<T>? {
    val branchExtId = requireNotNull(fetchSavedExternalId(branch.branch, id))

    return when (branch) {
        is PushableMainBranch -> MainBranchRatkoExternalId(branchExtId.oid)
        is PushableDesignBranch -> {
            managePlanItem(
                branch,
                id,
                // TODO GVT-2984 handle inherited and same-km-switch changes' design asset states
                designAssetState ?: DesignAssetState.OPEN,
                branchExtId,
                ratkoClient,
                fetchSavedExternalId,
                insertPlanItemId,
            )
        }
    }
}

private fun <T : LayoutAsset<T>> managePlanItem(
    branch: PushableDesignBranch,
    id: IntId<T>,
    designAssetState: DesignAssetState,
    extId: RatkoExternalId<T>,
    ratkoClient: RatkoClient,
    fetchSavedExternalId: (branch: LayoutBranch, id: IntId<T>) -> RatkoExternalId<T>?,
    insertPlanItemId: (id: IntId<T>, branch: DesignBranch, planItemId: RatkoPlanItemId) -> Unit,
): DesignRatkoExternalId<T> {
    val realOid = fetchSavedExternalId(LayoutBranch.main, id)?.oid
    val planItemId =
        if (extId.planItemId == null) {
            ratkoClient.createPlanItem(branch.planId, realOid).also { planItemId ->
                insertPlanItemId(id, branch.branch, planItemId)
            }
        } else {
            ratkoClient.updatePlanItem(
                RatkoPlanItem(
                    extId.planItemId,
                    branch.planId,
                    realOid,
                    mapDesignAssetStateToRatkoPlanState(designAssetState),
                )
            )
            extId.planItemId
        }
    return DesignRatkoExternalId(extId.oid, planItemId)
}

fun getEndPointNodeCollection(
    alignmentAddresses: AlignmentAddresses<*>,
    changedKmNumbers: Collection<KmNumber>,
    existingStartNode: RatkoNode? = null,
    existingEndNode: RatkoNode? = null,
): RatkoNodes? {
    val layoutStartNode =
        convertToRatkoNode(
            addressPoint = alignmentAddresses.startPoint,
            nodeType = RatkoNodeType.START_POINT,
            state = RatkoPointStates.VALID,
        )

    val layoutEndNode =
        convertToRatkoNode(
            addressPoint = alignmentAddresses.endPoint,
            nodeType = RatkoNodeType.END_POINT,
            state = RatkoPointStates.VALID,
        )

    val startHasChanged = changedKmNumbers.contains(alignmentAddresses.startPoint.address.kmNumber)
    val endHasChanged = changedKmNumbers.contains(alignmentAddresses.endPoint.address.kmNumber)

    return if (startHasChanged || endHasChanged) {
        val newStartNode =
            if (startHasChanged || existingStartNode == null) layoutStartNode else existingStartNode.withoutGeometry()

        val newEndNode =
            if (endHasChanged || existingEndNode == null) layoutEndNode else existingEndNode.withoutGeometry()

        return convertToRatkoNodeCollection(listOf(newStartNode, newEndNode))
    } else null
}

fun asSwitchTypeString(switchType: SwitchType): String {
    if (switchType.parts.baseType.nationality == SwitchNationality.SWEDISH) {
        // Drop handedness of Swedish switches, otherwise leave them as they are
        return if (switchType.parts.hand == SwitchHand.NONE) {
            switchType.toString()
        } else {
            switchType.toString().dropLast(2)
        }
    }

    val radius = switchType.parts.curveRadius
    val spread = switchType.parts.spread ?: ""
    val curveRadius =
        radius
            .mapIndexed { i, r ->
                // Adds N/P version to the first curve radius
                if (i == 0) "$r$spread" else "$r"
            }
            .let { radii -> if (radii.isEmpty()) "" else "-${radii.joinToString("/")}" }

    return "${switchType.parts.baseType}${switchType.parts.railWeight}${curveRadius}-${switchType.parts.ratio}"
}

fun sortByDeletedStateFirst(layoutState: LayoutState) = if (layoutState == LayoutState.DELETED) 0 else 1

fun sortByDeletedStateFirst(locationTrackState: LocationTrackState) =
    if (locationTrackState == LocationTrackState.DELETED) 0 else 1

fun sortByNullDuplicateOfFirst(duplicateOf: IntId<LocationTrack>?) = if (duplicateOf == null) 0 else 1

fun sortByDeletedStateFirst(layoutStateCategory: LayoutStateCategory) =
    if (layoutStateCategory == LayoutStateCategory.NOT_EXISTING) 0 else 1

fun toRatkoPointsGroupedByKm(addressPoints: Collection<AddressPoint<*>>) =
    addressPoints
        .groupBy { point -> point.address.kmNumber }
        .map { (_, addressPointsForKm) ->
            addressPointsForKm
                .map(::convertToRatkoPoint)
                .distinctBy { it.kmM.meters } // track meters 0000+0000.010 and 0000+0000.01 are considered the same
                .sortedBy { it.kmM }
        }
        .filter { ratkoPoints -> ratkoPoints.isNotEmpty() }

fun toNodeCollectionMarkingEndpointsNotInUse(ratkoNodes: RatkoNodes): RatkoNodes =
    convertToRatkoNodeCollection(
        listOfNotNull(
            ratkoNodes
                .getStartNode()
                ?.point
                ?.withoutGeometry()
                ?.copy(state = RatkoPointState(RatkoPointStates.NOT_IN_USE))
                ?.let { point -> RatkoNode(RatkoNodeType.START_POINT, point) },
            ratkoNodes
                .getEndNode()
                ?.point
                ?.withoutGeometry()
                ?.copy(state = RatkoPointState(RatkoPointStates.NOT_IN_USE))
                ?.let { point -> RatkoNode(RatkoNodeType.END_POINT, point) },
        )
    )
