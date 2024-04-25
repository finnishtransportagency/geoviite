package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.publication.PublicationValidationError
import fi.fta.geoviite.infra.switchLibrary.*
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText

enum class LocationTrackPointUpdateType {
    START_POINT, END_POINT
}

data class LayoutInterval<T>(
    val alignmentId: IntId<T>,
    val mRange: Range<Double>,
)

data class GeometryInterval(
    val alignmentId: IntId<GeometryAlignment>,
    val mRange: Range<Double>,
)

data class LinkingParameters<T>(
    val geometryPlanId: IntId<GeometryPlan>,
    val geometryInterval: GeometryInterval,
    val layoutInterval: LayoutInterval<T>,
)

data class EmptyAlignmentLinkingParameters<T>(
    val geometryPlanId: IntId<GeometryPlan>,
    val layoutAlignmentId: IntId<T>,
    val geometryInterval: GeometryInterval,
)

data class LocationTrackSaveRequest(
    val name: AlignmentName,
    val descriptionBase: FreeText,
    val descriptionSuffix: DescriptionSuffixType,
    val type: LocationTrackType,
    val state: LocationTrackState,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val duplicateOf: IntId<LocationTrack>?,
    val topologicalConnectivity: TopologicalConnectivityType,
    val ownerId: IntId<LocationTrackOwner>,
) {
    init {
        require(descriptionBase.length in 4..256) {
            "LocationTrack description length ${descriptionBase.length} not in range 4-256"
        }
    }
}

enum class SuggestedSwitchJointMatchType {
    START, END, LINE,
}

data class FittedSwitchJointMatch(
    val locationTrackId: IntId<LocationTrack>,
    val segmentIndex: Int,
    val m: Double,
    val switchJoint: SwitchJoint,
    val matchType: SuggestedSwitchJointMatchType,
    val distance: Double,
    val distanceToAlignment: Double,
    val alignmentId: IntId<LayoutAlignment>?,
)
data class FittedSwitchJoint(
    override val number: JointNumber,
    override val location: Point,
    val locationAccuracy: LocationAccuracy?,
    val matches: List<FittedSwitchJointMatch>,
) : ISwitchJoint

data class TopologyLinkFindingSwitch(
    val joints: List<ISwitchJoint>,
    val id: IntId<TrackLayoutSwitch>,
)

data class FittedSwitch(
    val name: SwitchName,
    val switchStructureId: IntId<SwitchStructure>,
    val joints: List<FittedSwitchJoint>,
    val alignmentEndPoint: LocationTrackEndpoint?,
    val geometrySwitchId: IntId<GeometrySwitch>? = null,
    val geometryPlanId: IntId<GeometryPlan>? = null,
)

data class SuggestedSwitch(
    val switchStructureId: IntId<SwitchStructure>,
    val joints: List<TrackLayoutSwitchJoint>,
    val trackLinks: Map<IntId<LocationTrack>, SwitchLinkingTrackLinks>,
    val geometrySwitchId: IntId<GeometrySwitch>? = null,
    val alignmentEndPoint: LocationTrackEndpoint? = null,
    val name: SwitchName,
)

enum class TrackEnd {
    START, END
}

data class SwitchLinkingTopologicalTrackLink(
    val number: JointNumber,
    val trackEnd: TrackEnd,
)

data class SwitchLinkingTrackLinks(
    val segmentJoints: List<SwitchLinkingJoint>,
    val topologyJoint: SwitchLinkingTopologicalTrackLink?,
) {
    init {
        // linking to neither is OK; that just communicates cleaning up all links
        check(topologyJoint == null || segmentJoints.isEmpty()) { "Switch linking track link links both to segment and topology"}
        check(segmentJoints.zipWithNext { a, b -> a.m < b.m }.all { it }) { "Switch linking track link segment joints should be m-ordered"}
    }
}

data class SwitchLinkingJoint(
    val number: JointNumber,
    val segmentIndex: Int,
    val m: Double,
    val location: Point,
)

data class TrackLayoutSwitchSaveRequest(
    val name: SwitchName,
    val switchStructureId: IntId<SwitchStructure>,
    val stateCategory: LayoutStateCategory,
    val ownerId: IntId<SwitchOwner>,
    val trapPoint: Boolean?,
)

data class TrackNumberSaveRequest(
    val number: TrackNumber,
    val description: FreeText,
    val state: LayoutState,
    val startAddress: TrackMeter,
) {
    init {
        require(description.isNotBlank()) { "TrackNumber should have a non-blank description" }
        require(description.length < 100) { "TrackNumber description too long: ${description.length}>100" }
    }
}

data class TrackLayoutKmPostSaveRequest(
    val kmNumber: KmNumber,
    val state: LayoutState,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
)

data class LocationTrackEndpoint(
    val locationTrackId: IntId<LocationTrack>,
    val location: Point,
    val updateType: LocationTrackPointUpdateType,
)

data class SuggestedSwitchCreateParamsAlignmentMapping(
    val switchAlignmentId: StringId<SwitchAlignment>,
    val locationTrackId: IntId<LocationTrack>,
    val ascending: Boolean? = null,
)

data class SuggestedSwitchCreateParams(
    val locationTrackEndpoint: LocationTrackEndpoint,
    val switchStructureId: IntId<SwitchStructure>?,
    val alignmentMappings: List<SuggestedSwitchCreateParamsAlignmentMapping>,
)

data class KmPostLinkingParameters(
    val geometryPlanId: IntId<GeometryPlan>,
    val geometryKmPostId: IntId<GeometryKmPost>,
    val layoutKmPostId: IntId<TrackLayoutKmPost>,
)

data class SwitchRelinkingValidationResult(
    val id: IntId<TrackLayoutSwitch>,
    val successfulSuggestion: SwitchRelinkingSuggestion?,
    val validationErrors: List<PublicationValidationError>,
)
data class SwitchRelinkingSuggestion(
    val location: Point,
    val address: TrackMeter,
)

enum class TrackSwitchRelinkingResultType { RELINKED, NOT_AUTOMATICALLY_LINKABLE }
data class TrackSwitchRelinkingResult(
    val id: IntId<TrackLayoutSwitch>,
    val outcome: TrackSwitchRelinkingResultType
)
