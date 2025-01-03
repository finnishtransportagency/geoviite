package fi.fta.geoviite.infra.ratko.model

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.ratko.asSwitchTypeString
import fi.fta.geoviite.infra.switchLibrary.SwitchBaseType
import fi.fta.geoviite.infra.switchLibrary.SwitchHand
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val ratkoDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

fun mapToRatkoLocationTrackState(layoutState: LocationTrackState) =
    when (layoutState) {
        LocationTrackState.BUILT -> RatkoLocationTrackState.BUILT
        LocationTrackState.DELETED -> RatkoLocationTrackState.DELETED
        LocationTrackState.NOT_IN_USE -> RatkoLocationTrackState.NOT_IN_USE
        LocationTrackState.IN_USE -> RatkoLocationTrackState.IN_USE
    }

fun mapToRatkoRouteNumberState(layoutState: LayoutState) =
    when (layoutState) {
        LayoutState.NOT_IN_USE -> RatkoRouteNumberState(RatkoRouteNumberStateType.NOT_IN_USE)
        LayoutState.IN_USE -> RatkoRouteNumberState(RatkoRouteNumberStateType.VALID)
        // There is no DELETED or corresponding state in Ratko, so it will be NOT_VALID
        LayoutState.DELETED -> RatkoRouteNumberState(RatkoRouteNumberStateType.NOT_VALID)
    }

fun mapToRatkoLocationTrackType(trackType: LocationTrackType?) =
    when (trackType) {
        LocationTrackType.MAIN -> RatkoLocationTrackType.MAIN
        LocationTrackType.TRAP -> RatkoLocationTrackType.TRAP
        LocationTrackType.CHORD -> RatkoLocationTrackType.CHORD
        LocationTrackType.SIDE -> RatkoLocationTrackType.SIDE
        null -> RatkoLocationTrackType.NULL
    }

fun mapToRatkoTopologicalConnectivityType(type: TopologicalConnectivityType) =
    when (type) {
        TopologicalConnectivityType.NONE -> RatkoTopologicalConnectivityType.NONE
        TopologicalConnectivityType.START -> RatkoTopologicalConnectivityType.START
        TopologicalConnectivityType.END -> RatkoTopologicalConnectivityType.END
        TopologicalConnectivityType.START_AND_END -> RatkoTopologicalConnectivityType.START_AND_END
    }

fun mapToRatkoMeasurementMethod(layoutMeasurementMethod: MeasurementMethod?) =
    when (layoutMeasurementMethod) {
        MeasurementMethod.VERIFIED_DESIGNED_GEOMETRY -> RatkoMeasurementMethod.VERIFIED_DESIGNED_GEOMETRY
        MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY -> RatkoMeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY
        MeasurementMethod.TRACK_INSPECTION -> RatkoMeasurementMethod.TRACK_INSPECTION
        MeasurementMethod.DIGITIZED_AERIAL_IMAGE -> RatkoMeasurementMethod.DIGITALIZED_AERIAL_IMAGE
        MeasurementMethod.UNVERIFIED_DESIGNED_GEOMETRY -> RatkoMeasurementMethod.UNVERIFIED_DESIGNED_GEOMETRY
        null -> RatkoMeasurementMethod.UNKNOWN
    }

fun mapToRatkoSwitchState(layoutStateCategory: LayoutStateCategory, ratkoSwitchState: RatkoAssetState?) =
    if (ratkoSwitchState == null || layoutStateCategory != ratkoSwitchState.category)
        when (layoutStateCategory) {
            LayoutStateCategory.EXISTING -> RatkoAssetState.IN_USE
            LayoutStateCategory.NOT_EXISTING -> RatkoAssetState.DELETED
        }
    else ratkoSwitchState

fun convertToRatkoAssetGeometries(joints: Collection<LayoutSwitchJoint>, switchType: SwitchBaseType) =
    joints.map { joint ->
        RatkoAssetGeometry(
            geometry = RatkoGeometry(joint.location),
            geomType = mapJointNumberToGeometryType(joint.number, switchType),
            assetGeomAccuracyType = mapLocationAccuracyToRatkoLocationAccuracy(joint.locationAccuracy),
        )
    }

fun mapLocationAccuracyToRatkoLocationAccuracy(locationAccuracy: LocationAccuracy?) =
    when (locationAccuracy) {
        LocationAccuracy.DESIGNED_GEOLOCATION -> RatkoAssetGeomAccuracyType.DESIGNED_GEOLOCATION
        LocationAccuracy.OFFICIALLY_MEASURED_GEODETICALLY -> RatkoAssetGeomAccuracyType.OFFICIALLY_MEASURED_GEODETICALLY
        LocationAccuracy.MEASURED_GEODETICALLY -> RatkoAssetGeomAccuracyType.MEASURED_GEODETICALLY
        LocationAccuracy.DIGITIZED_AERIAL_IMAGE -> RatkoAssetGeomAccuracyType.DIGITALIZED_AERIAL_IMAGE
        LocationAccuracy.GEOMETRY_CALCULATED -> RatkoAssetGeomAccuracyType.GEOMETRY_CALCULATED
        else -> RatkoAssetGeomAccuracyType.UNKNOWN
    }

fun mapGeometryTypeToNodeType(geometryType: RatkoAssetGeometryType) =
    when (geometryType) {
        RatkoAssetGeometryType.JOINT_A -> RatkoNodeType.JOINT_A
        RatkoAssetGeometryType.JOINT_B -> RatkoNodeType.JOINT_B
        RatkoAssetGeometryType.JOINT_C -> RatkoNodeType.JOINT_C
        RatkoAssetGeometryType.JOINT_D -> RatkoNodeType.JOINT_D
        else -> null
    }

fun mapJointNumberToGeometryType(number: JointNumber, baseType: SwitchBaseType): RatkoAssetGeometryType {
    val geometryType =
        if (baseType === SwitchBaseType.YV || baseType === SwitchBaseType.TYV || baseType === SwitchBaseType.EV) {
            when (number.intValue) {
                1 -> RatkoAssetGeometryType.JOINT_A
                2 -> RatkoAssetGeometryType.JOINT_B
                3 -> RatkoAssetGeometryType.JOINT_C
                5 -> RatkoAssetGeometryType.MATH_POINT
                else -> null
            }
        } else if (baseType === SwitchBaseType.KV) {
            when (number.intValue) {
                1 -> RatkoAssetGeometryType.JOINT_A
                2 -> RatkoAssetGeometryType.JOINT_B
                3 -> RatkoAssetGeometryType.JOINT_C
                4 -> RatkoAssetGeometryType.JOINT_D
                5 -> RatkoAssetGeometryType.MATH_POINT_AC
                6 -> RatkoAssetGeometryType.MATH_POINT_AD
                else -> null
            }
        } else if (
            baseType === SwitchBaseType.KRV || baseType === SwitchBaseType.YRV || baseType === SwitchBaseType.RR
        ) {
            when (number.intValue) {
                1 -> RatkoAssetGeometryType.JOINT_A
                2 -> RatkoAssetGeometryType.JOINT_B
                3 -> RatkoAssetGeometryType.JOINT_C
                4 -> RatkoAssetGeometryType.JOINT_D
                5 -> RatkoAssetGeometryType.MATH_POINT
                else -> null
            }
        } else if (baseType === SwitchBaseType.UKV) {
            when (number.intValue) {
                1 -> RatkoAssetGeometryType.JOINT_A
                7 -> RatkoAssetGeometryType.MATH_POINT_AC
                8 -> RatkoAssetGeometryType.MATH_POINT_AB
                10 -> RatkoAssetGeometryType.JOINT_C
                11 -> RatkoAssetGeometryType.JOINT_B
                else -> null
            }
        } else if (baseType === SwitchBaseType.SKV) {
            when (number.intValue) {
                1 -> RatkoAssetGeometryType.JOINT_A
                13 -> RatkoAssetGeometryType.MATH_POINT_AC
                16 -> RatkoAssetGeometryType.JOINT_B
                17 -> RatkoAssetGeometryType.JOINT_C
                else -> null
            }
        } else if (baseType === SwitchBaseType.SRR) {
            when (number.intValue) {
                1 -> RatkoAssetGeometryType.JOINT_A
                2 -> RatkoAssetGeometryType.JOINT_B
                3 -> RatkoAssetGeometryType.JOINT_C
                4 -> RatkoAssetGeometryType.JOINT_D
                5 -> RatkoAssetGeometryType.MATH_POINT
                else -> null
            }
        } else null

    return checkNotNull(geometryType) { "Unknown switch joint number ${number.intValue} for switch type $baseType" }
}

fun convertToRatkoLocationTrack(
    locationTrack: LocationTrack,
    locationTrackExternalId: FullRatkoExternalId<LocationTrack>?,
    trackNumberOid: Oid<LayoutTrackNumber>?,
    nodeCollection: RatkoNodes? = null,
    duplicateOfOid: Oid<LocationTrack>?,
    descriptionGetter: (LocationTrack) -> FreeText,
    owner: LocationTrackOwner,
) =
    RatkoLocationTrack(
        id = locationTrackExternalId?.oid?.toString(),
        name = locationTrack.name.toString(),
        routenumber = trackNumberOid?.let(::RatkoOid),
        description = descriptionGetter(locationTrack).toString(),
        state = mapToRatkoLocationTrackState(locationTrack.state),
        type = mapToRatkoLocationTrackType(locationTrack.type),
        nodecollection = nodeCollection,
        duplicateOf = duplicateOfOid?.toString(),
        topologicalConnectivity = mapToRatkoTopologicalConnectivityType(locationTrack.topologicalConnectivity),
        owner = owner.name.toString(),
        isPlanContext = locationTrackExternalId is DesignRatkoExternalId,
        planItemIds = asPlanItemIdsList(locationTrackExternalId),
    )

fun convertToRatkoRouteNumber(
    trackNumber: LayoutTrackNumber,
    trackNumberExternalId: FullRatkoExternalId<LayoutTrackNumber>?,
    nodeCollection: RatkoNodes? = null,
) =
    RatkoRouteNumber(
        id = trackNumberExternalId?.oid?.toString(),
        name = trackNumber.number.toString(),
        description = trackNumber.description.toString(),
        state = mapToRatkoRouteNumberState(trackNumber.state),
        nodecollection = nodeCollection,
        isPlanContext = trackNumberExternalId is DesignRatkoExternalId,
        planItemIds = asPlanItemIdsList(trackNumberExternalId),
    )

fun convertToRatkoPoint(point: AddressPoint, state: RatkoPointStates = RatkoPointStates.VALID) =
    RatkoPoint(
        state = RatkoPointState(state),
        kmM = RatkoTrackMeter(point.address),
        geometry = RatkoGeometry(point.point),
    )

fun convertToRatkoNodeCollection(addresses: AlignmentAddresses) =
    convertToRatkoNodeCollection(
        listOf(
            convertToRatkoNode(addresses.startPoint, RatkoNodeType.START_POINT),
            convertToRatkoNode(addresses.endPoint, RatkoNodeType.END_POINT),
        )
    )

fun convertToRatkoNodeCollection(nodes: Collection<RatkoNode>) = RatkoNodes(nodes, RatkoNodesType.START_AND_END)

fun convertToRatkoNode(
    addressPoint: AddressPoint,
    nodeType: RatkoNodeType,
    state: RatkoPointStates = RatkoPointStates.VALID,
) = RatkoNode(nodeType, convertToRatkoPoint(addressPoint, state))

fun convertToRatkoMetadataAsset(
    trackNumberOid: Oid<LayoutTrackNumber>,
    locationTrackExternalId: FullRatkoExternalId<LocationTrack>,
    segmentMetadata: LayoutSegmentMetadata,
    startTrackMeter: TrackMeter,
    endTrackMeter: TrackMeter,
) =
    RatkoMetadataAsset(
        properties =
            listOf(
                RatkoAssetProperty(name = "filename", stringValue = segmentMetadata.fileName?.toString() ?: ""),
                RatkoAssetProperty(
                    name = "measurement_method",
                    enumValue = mapToRatkoMeasurementMethod(segmentMetadata.measurementMethod).value,
                ),
                RatkoAssetProperty(
                    name = "created_year",
                    integerValue = segmentMetadata.planTime?.atZone(ZoneId.systemDefault())?.year ?: 1800,
                ),
                RatkoAssetProperty(name = "original_crs", stringValue = segmentMetadata.originalSrid?.toString() ?: ""),
                RatkoAssetProperty(name = "alignment", stringValue = segmentMetadata.alignmentName?.toString() ?: ""),
            ),
        locations =
            listOf(
                RatkoAssetLocation(
                    priority = 1,
                    accuracyType = RatkoAccuracyType.GEOMETRY_CALCULATED,
                    nodecollection =
                        RatkoNodes(
                            type = RatkoNodesType.START_AND_END,
                            nodes =
                                listOf(
                                    RatkoNode(
                                        nodeType = RatkoNodeType.START_POINT,
                                        point =
                                            RatkoPoint(
                                                kmM = RatkoTrackMeter(startTrackMeter),
                                                locationtrack = RatkoOid(requireNotNull(locationTrackExternalId.oid)),
                                                routenumber = RatkoOid(trackNumberOid),
                                                geometry = RatkoGeometry(segmentMetadata.startPoint),
                                                state = RatkoPointState(RatkoPointStates.VALID),
                                                rowMetadata = null,
                                            ),
                                    ),
                                    RatkoNode(
                                        nodeType = RatkoNodeType.END_POINT,
                                        point =
                                            RatkoPoint(
                                                kmM = RatkoTrackMeter(endTrackMeter),
                                                locationtrack = RatkoOid(locationTrackExternalId.oid),
                                                routenumber = RatkoOid(trackNumberOid),
                                                geometry = RatkoGeometry(segmentMetadata.endPoint),
                                                state = RatkoPointState(RatkoPointStates.VALID),
                                                rowMetadata = null,
                                            ),
                                    ),
                                ),
                        ),
                )
            ),
        isPlanContext = locationTrackExternalId is DesignRatkoExternalId,
        planItemIds = asPlanItemIdsList(locationTrackExternalId),
    )

fun convertToRatkoSwitch(
    layoutSwitch: LayoutSwitch,
    layoutSwitchExternalId: FullRatkoExternalId<LayoutSwitch>?,
    switchStructure: SwitchStructure,
    switchOwner: SwitchOwner?,
    existingRatkoSwitch: RatkoSwitchAsset? = null,
) =
    RatkoSwitchAsset(
        id = layoutSwitchExternalId?.oid?.toString(),
        state = mapToRatkoSwitchState(layoutSwitch.stateCategory, existingRatkoSwitch?.state),
        properties =
            listOf(
                RatkoAssetProperty(name = "name", stringValue = layoutSwitch.name.toString()),
                RatkoAssetProperty(name = "turnout_id", stringValue = layoutSwitch.name.toString()),
                RatkoAssetProperty(name = "turnout_type", enumValue = asSwitchTypeString(switchStructure.type)),
                RatkoAssetProperty(
                    name = "safety_turnout",
                    enumValue = layoutSwitch.trapPoint?.let { if (it) "Kyllä" else "Ei" } ?: "Ei tiedossa",
                ),
                RatkoAssetProperty(name = "owner", enumValue = switchOwner?.name?.toString() ?: "Ei tiedossa"),
                RatkoAssetProperty(
                    name = "handedness",
                    enumValue =
                        switchStructure.hand.let { hand ->
                            when (hand) {
                                SwitchHand.LEFT -> "Vasenkätinen"
                                SwitchHand.RIGHT -> "Oikeakätinen"
                                else -> "Ei kätisyyttä"
                            }
                        },
                ),
            ),
        locations = null,
        assetGeoms = null,
        isPlanContext = layoutSwitchExternalId is DesignRatkoExternalId,
        planItemIds = asPlanItemIdsList(layoutSwitchExternalId),
    )

fun convertToRatkoAssetLocations(
    jointChanges: List<SwitchJointChange>,
    switchType: SwitchBaseType,
): List<RatkoAssetLocation> {
    return jointChanges
        .groupBy { it.locationTrackId }
        .map { (_, jointChange) ->
            jointChange.mapNotNull { change ->
                val nodeType = mapGeometryTypeToNodeType(mapJointNumberToGeometryType(change.number, switchType))

                nodeType?.let {
                    checkNotNull(change.locationTrackExternalId) {
                        "Cannot push switch changes with missing location track oid $change"
                    }

                    checkNotNull(change.trackNumberExternalId) {
                        "Cannot push switch changes with missing route number oid $change"
                    }

                    RatkoNode(
                        nodeType = nodeType,
                        point =
                            RatkoPoint(
                                state = RatkoPointState(RatkoPointStates.VALID),
                                locationtrack = RatkoOid(change.locationTrackExternalId),
                                routenumber = RatkoOid(change.trackNumberExternalId),
                                kmM = RatkoTrackMeter(change.address),
                                geometry = RatkoGeometry(change.point),
                            ),
                    )
                }
            }
        }
        .filter { it.isNotEmpty() }
        .mapIndexed { index, locationNodes ->
            RatkoAssetLocation(
                nodecollection = RatkoNodes(type = RatkoNodesType.JOINTS, nodes = locationNodes),
                priority = index + 1,
                accuracyType = RatkoAccuracyType.GEOMETRY_CALCULATED,
            )
        }
}

fun asPlanItemIdsList(extId: FullRatkoExternalId<*>?): List<Int>? =
    (extId as? DesignRatkoExternalId)?.let { listOf(it.planItemId.id) }

fun newRatkoPlan(design: LayoutDesign): RatkoPlan =
    RatkoPlan(
        id = null,
        name = design.name.toString(),
        estimatedCompletion =
            design.estimatedCompletion.atTime(12, 0, 0).atOffset(ZoneOffset.of("Z")).format(ratkoDateFormatter),
        phase = RatkoPlanPhase.RAILWAY_PLAN,
        state =
            when (design.designState) {
                DesignState.ACTIVE -> RatkoPlanState.OPEN
                DesignState.DELETED -> RatkoPlanState.CANCELLED
                DesignState.COMPLETED -> RatkoPlanState.COMPLETED
            },
    )

fun existingRatkoPlan(design: LayoutDesign, id: RatkoPlanId): RatkoPlan = newRatkoPlan(design).copy(id = id.id)
