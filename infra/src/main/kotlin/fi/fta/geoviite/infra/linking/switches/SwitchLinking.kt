package fi.fta.geoviite.infra.linking.switches

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.LayoutValidationIssue
import fi.fta.geoviite.infra.switchLibrary.ISwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.tracklayout.EdgeM
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole

enum class SuggestedSwitchJointMatchType {
    START,
    END,
    LINE,
}

enum class RelativeDirection {
    Along,
    Against,
}

data class EdgeId(val locationTrackId: IntId<LocationTrack>, val edgeIndex: Int)

data class JointOnEdge(
    val jointNumber: JointNumber,
    val jointRole: SwitchJointRole,
    val mOnEdge: LineM<EdgeM>,
    val direction: RelativeDirection,
    val location: Point,
)

data class LayoutSwitchSaveRequest(
    val name: SwitchName,
    val switchStructureId: IntId<SwitchStructure>,
    val stateCategory: LayoutStateCategory,
    val ownerId: IntId<SwitchOwner>,
    val trapPoint: Boolean?,
    val draftOid: Oid<LayoutSwitch>?,
)

data class FittedSwitchJointMatch(
    val locationTrackId: IntId<LocationTrack>,
    val segmentIndex: Int,
    val mOnTrack: LineM<LocationTrackM>,
    val switchJoint: SwitchStructureJoint,
    val matchType: SuggestedSwitchJointMatchType,
    val distance: Double,
    val distanceToAlignment: Double,
    val direction: RelativeDirection,
    val location: Point,
)

data class FittedSwitchJoint(
    override val number: JointNumber,
    override val location: Point,
    val locationAccuracy: LocationAccuracy?,
    val matches: List<FittedSwitchJointMatch>,
) : ISwitchJoint

data class TopologyLinkFindingSwitch(val joints: List<ISwitchJoint>, val id: IntId<LayoutSwitch>)

data class FittedSwitch(val switchStructure: SwitchStructure, val joints: List<FittedSwitchJoint>)

data class SwitchPlacingRequest(val points: SamplingGridPoints, val layoutSwitchId: IntId<LayoutSwitch>)

data class SuggestedSwitch(
    val joints: List<LayoutSwitchJoint>,
    val trackLinks: Map<IntId<LocationTrack>, SwitchLinkingTrackLinks>,
)

data class SwitchLinkingParameters(val suggestedSwitch: SuggestedSwitch, val geometrySwitchId: IntId<GeometrySwitch>?)

data class SwitchLinkingTrackLinks(val locationTrackVersion: Int, val suggestedLinks: SuggestedLinks?) {
    @JsonIgnore fun isLinked(): Boolean = suggestedLinks != null
}

data class SuggestedLinks(val edgeIndex: Int, val joints: List<SwitchLinkingJoint>) {
    init {
        require(edgeIndex >= 0) { "Suggested link must be on a found edge" }
    }
}

data class SwitchLinkingJoint(val mvalueOnEdge: LineM<EdgeM>, val jointNumber: JointNumber, val location: Point)

data class SwitchRelinkingValidationResult(
    val id: IntId<LayoutSwitch>,
    val successfulSuggestion: SwitchRelinkingSuggestion?,
    val validationIssues: List<LayoutValidationIssue>,
)

data class SwitchRelinkingSuggestion(val location: Point, val address: TrackMeter)

sealed class GeometrySwitchSuggestionResult

data class GeometrySwitchSuggestionSuccess(val switch: SuggestedSwitch) : GeometrySwitchSuggestionResult()

data class GeometrySwitchSuggestionFailure(val failure: GeometrySwitchSuggestionFailureReason) :
    GeometrySwitchSuggestionResult()

enum class GeometrySwitchSuggestionFailureReason {
    RELATED_TRACKS_NOT_LINKED,
    NO_SWITCH_STRUCTURE_ID_ON_SWITCH,
    NO_SRID_ON_PLAN,
    INVALID_JOINTS,
    LESS_THAN_TWO_JOINTS,
}

sealed class GeometrySwitchFittingResult

data class GeometrySwitchFittingSuccess(val switch: FittedSwitch) : GeometrySwitchFittingResult()

data class GeometrySwitchFittingFailure(val failure: GeometrySwitchSuggestionFailureReason) :
    GeometrySwitchFittingResult()

data class GeometrySwitchFittingException(val failure: GeometrySwitchSuggestionFailureReason) : RuntimeException()

data class SwitchOidPresence(val existsInRatko: Boolean?, val existsInGeoviiteAs: GeoviiteSwitchOidPresence?)

data class GeoviiteSwitchOidPresence(
    val id: IntId<LayoutSwitch>,
    val stateCategory: LayoutStateCategory,
    val name: SwitchName,
)
