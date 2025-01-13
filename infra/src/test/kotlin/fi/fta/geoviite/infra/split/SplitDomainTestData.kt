package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix

fun splitRequest(trackId: IntId<LocationTrack>, vararg targets: SplitRequestTarget): SplitRequest =
    SplitRequest(trackId, targets.toList())

fun targetRequest(
    startAtSwitchId: IntId<LayoutSwitch>?,
    name: String,
    descriptionBase: String = "Split desc $name",
    descriptionSuffix: LocationTrackDescriptionSuffix = LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH,
    duplicateTrackId: IntId<LocationTrack>? = null,
    operation: SplitTargetDuplicateOperation = SplitTargetDuplicateOperation.OVERWRITE,
): SplitRequestTarget =
    SplitRequestTarget(
        duplicateTrack = duplicateTrackId?.let { id -> SplitRequestTargetDuplicate(id, operation) },
        startAtSwitchId = startAtSwitchId,
        name = AlignmentName(name),
        descriptionBase = LocationTrackDescriptionBase(descriptionBase),
        descriptionSuffix = descriptionSuffix,
    )

fun targetParams(
    switchId: IntId<LayoutSwitch>?,
    switchJoint: JointNumber?,
    name: String,
    descriptionBase: String = "split desc $name $switchId $switchJoint",
    descriptionSuffixType: LocationTrackDescriptionSuffix = LocationTrackDescriptionSuffix.NONE,
    duplicate: Pair<LocationTrack, LayoutAlignment>? = null,
): SplitTargetParams {
    return SplitTargetParams(
        startSwitch =
            if (switchId != null && switchJoint != null) {
                SplitPointSwitch(switchId, switchJoint, SwitchName("S${switchId.intValue}"))
            } else {
                null
            },
        request =
            SplitRequestTarget(
                duplicateTrack =
                    (duplicate?.first?.id as? IntId)?.let { id ->
                        SplitRequestTargetDuplicate(id, SplitTargetDuplicateOperation.OVERWRITE)
                    },
                startAtSwitchId = switchId,
                name = AlignmentName(name),
                descriptionBase = LocationTrackDescriptionBase(descriptionBase),
                descriptionSuffix = descriptionSuffixType,
            ),
        duplicate =
            duplicate?.let { (track, alignment) ->
                SplitTargetDuplicate(SplitTargetDuplicateOperation.OVERWRITE, track, alignment)
            },
    )
}
