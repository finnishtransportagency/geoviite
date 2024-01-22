import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.split.SplitRequestTarget
import fi.fta.geoviite.infra.split.SplitTargetParams
import fi.fta.geoviite.infra.tracklayout.DescriptionSuffixType
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.util.FreeText

fun targetRequest(
    startAtSwitchId: IntId<TrackLayoutSwitch>?,
    name: String,
    descriptionBase: String = "Split desc $name",
    descriptionSuffix: DescriptionSuffixType = DescriptionSuffixType.SWITCH_TO_SWITCH,
    duplicateTrackId: IntId<LocationTrack>? = null,
): SplitRequestTarget = SplitRequestTarget(
    duplicateTrackId = duplicateTrackId,
    startAtSwitchId = startAtSwitchId,
    name = AlignmentName(name),
    descriptionBase = FreeText(descriptionBase),
    descriptionSuffix = descriptionSuffix,
)

fun targetParams(
    switchId: IntId<TrackLayoutSwitch>?,
    switchJoint: JointNumber?,
    name: String,
    descriptionBase: String = "split desc $name $switchId $switchJoint",
    descriptionSuffixType: DescriptionSuffixType = DescriptionSuffixType.NONE,
): SplitTargetParams {
    return SplitTargetParams(
        startSwitch = if (switchId != null && switchJoint != null) (switchId to switchJoint) else null,
        request = SplitRequestTarget(
            duplicateTrackId = null,
            startAtSwitchId = switchId,
            name = AlignmentName(name),
            descriptionBase = FreeText(descriptionBase),
            descriptionSuffix = descriptionSuffixType,
        ),
        duplicate = null,
    )
}
