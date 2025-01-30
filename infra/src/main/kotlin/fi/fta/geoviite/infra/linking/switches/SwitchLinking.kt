package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.linking.TrackEnd
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.LayoutValidationIssue
import fi.fta.geoviite.infra.switchLibrary.ISwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LocationTrack

enum class SuggestedSwitchJointMatchType {
    START,
    END,
    LINE,
}

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
    val m: Double,
    val switchJoint: SwitchStructureJoint,
    val matchType: SuggestedSwitchJointMatchType,
    val distance: Double,
    val distanceToAlignment: Double,
)

data class FittedSwitchJoint(
    override val number: JointNumber,
    override val location: Point,
    val locationAccuracy: LocationAccuracy?,
    val matches: List<FittedSwitchJointMatch>,
) : ISwitchJoint

data class TopologyLinkFindingSwitch(val joints: List<ISwitchJoint>, val id: IntId<LayoutSwitch>)

data class FittedSwitch(val switchStructure: SwitchStructure, val joints: List<FittedSwitchJoint>)

data class SuggestedSwitch(
    val switchStructureId: IntId<SwitchStructure>,
    val joints: List<LayoutSwitchJoint>,
    val trackLinks: Map<IntId<LocationTrack>, SwitchLinkingTrackLinks>,
    val geometrySwitchId: IntId<GeometrySwitch>? = null,
    val name: SwitchName,
)

data class SwitchLinkingTopologicalTrackLink(val number: JointNumber, val trackEnd: TrackEnd)

data class SwitchPlacingRequest(val points: SamplingGridPoints, val layoutSwitchId: IntId<LayoutSwitch>)

data class SwitchLinkingTrackLinks(
    val segmentJoints: List<SwitchLinkingJoint>,
    val topologyJoint: SwitchLinkingTopologicalTrackLink?,
) {
    init {
        // linking to neither is OK; that just communicates cleaning up all links
        check(topologyJoint == null || segmentJoints.isEmpty()) {
            "Switch linking track link links both to segment and topology"
        }
        check(segmentJoints.zipWithNext { a, b -> a.m < b.m }.all { it }) {
            "Switch linking track link segment joints should be m-ordered"
        }
    }

    fun isLinked(): Boolean = segmentJoints.isNotEmpty() || topologyJoint != null
}

data class SwitchLinkingJoint(val number: JointNumber, val segmentIndex: Int, val m: Double, val location: Point)

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
