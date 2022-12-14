package fi.fta.geoviite.infra.linking

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.*
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText

enum class LocationTrackPointUpdateType {
    START_POINT,
    END_POINT
}

data class IntervalLayoutPoint(
    val segmentId: IndexedId<LayoutSegment>,
    val endPointType: EndPointType?,
    val point: Point,
)

data class IntervalGeometryPoint(
    val segmentId: DomainId<LayoutSegment>,
    val point: Point,
)

data class LayoutInterval<T>(
    val alignmentId: IntId<T>,
    val start: IntervalLayoutPoint,
    val end: IntervalLayoutPoint,
)

data class GeometryInterval(
    val alignmentId: IntId<GeometryAlignment>,
    val start: IntervalGeometryPoint,
    val end: IntervalGeometryPoint,
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

data class LocationTrackEndPointUpdateRequest(
    val updateType: LocationTrackPointUpdateType,
)

data class LocationTrackEndPointConnectedUpdateRequest(
    val connectedLocationTrackId: IntId<LocationTrack>,
    val updateType: LocationTrackPointUpdateType,
)

data class LocationTrackSaveRequest(
    val name: AlignmentName,
    val description: FreeText,
    val type: LocationTrackType,
    val state: LayoutState,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val duplicateOf: IntId<LocationTrack>?,
    val topologicalConnectivity: TopologicalConnectivityType
) {
    init {
        require(description.length in 4..256) {
            "LocationTrack description length ${description.length} not in range 4-256"
        }
    }
}

enum class SuggestedSwitchJointMatchType {
    START,
    END,
    LINE,
}

data class SuggestedSwitchJointMatch(
    val locationTrackId: DomainId<LocationTrack>,
    val segmentIndex: Int,
    val layoutSwitchId: DomainId<TrackLayoutSwitch>?,
    val segmentM: Double,
    @JsonIgnore val switchJoint: SwitchJoint,
    @JsonIgnore val matchType: SuggestedSwitchJointMatchType,
    @JsonIgnore val distance: Double,
    @JsonIgnore val alignmentId: IntId<LayoutAlignment>?,
) {
    fun segmentId(): IndexedId<LayoutSegment>? = alignmentId?.let { aid -> IndexedId(aid.intValue, segmentIndex) }
}

data class SuggestedSwitchJoint(
    override val number: JointNumber,
    override val location: Point,
    val matches: List<SuggestedSwitchJointMatch>,
    val locationAccuracy: LocationAccuracy?
) : ISwitchJoint

data class SuggestedSwitch(
    val name: SwitchName,
    val switchStructure: SwitchStructure,
    val joints: List<SuggestedSwitchJoint>,
    val alignmentEndPoint: LocationTrackEndpoint?,
    val geometrySwitchId: IntId<GeometrySwitch>? = null,
    val geometryPlanId: IntId<GeometryPlan>? = null,
)

data class SwitchLinkingSegment(
    val locationTrackId: IntId<LocationTrack>,
    val segmentIndex: Int,
    val segmentM: Double,
)

data class SwitchLinkingJoint(
    val jointNumber: JointNumber,
    val location: Point,
    val locationAccuracy: LocationAccuracy?,
    val segments: List<SwitchLinkingSegment>,
)

data class SwitchLinkingParameters(
    val layoutSwitchId: IntId<TrackLayoutSwitch>,
    val switchStructureId: IntId<SwitchStructure>,
    val joints: List<SwitchLinkingJoint>,
    val geometrySwitchId: IntId<GeometrySwitch>?,
)

data class TrackLayoutSwitchSaveRequest(
    val name: SwitchName,
    val switchStructureId: IntId<SwitchStructure>,
    val stateCategory: LayoutStateCategory,
    val ownerId: IntId<SwitchOwner>,
    val trapPoint: Boolean?
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
    val updateType: LocationTrackPointUpdateType
)

data class SuggestedSwitchCreateParamsAlignmentMapping(
    val switchAlignmentId: StringId<SwitchAlignment>,
    val locationTrackId: IntId<LocationTrack>,
    val ascending: Boolean? = null
)

data class SuggestedSwitchCreateParams(
    val locationTrackEndpoint: LocationTrackEndpoint,
    val switchStructureId: IntId<SwitchStructure>?,
    val alignmentMappings: List<SuggestedSwitchCreateParamsAlignmentMapping>,
)

data class KmPostLinkingParameters(
    val geometryKmPostId: IntId<GeometryKmPost>,
    val layoutKmPostId: IntId<TrackLayoutKmPost>,
)
