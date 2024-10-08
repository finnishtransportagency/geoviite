package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.CoordinateTransformationException
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.geography.isGkFinSrid
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.publication.LayoutValidationIssue
import fi.fta.geoviite.infra.switchLibrary.ISwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.DescriptionSuffixType
import fi.fta.geoviite.infra.tracklayout.KmPostGkLocationSource
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackOwner
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber

enum class LocationTrackPointUpdateType {
    START_POINT,
    END_POINT,
}

data class LayoutInterval<T>(val alignmentId: IntId<T>, val mRange: Range<Double>)

data class GeometryInterval(val alignmentId: IntId<GeometryAlignment>, val mRange: Range<Double>)

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
    val descriptionBase: LocationTrackDescriptionBase,
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
    START,
    END,
    LINE,
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

data class TopologyLinkFindingSwitch(val joints: List<ISwitchJoint>, val id: IntId<TrackLayoutSwitch>)

data class FittedSwitch(val switchStructure: SwitchStructure, val joints: List<FittedSwitchJoint>)

data class SuggestedSwitch(
    val switchStructureId: IntId<SwitchStructure>,
    val joints: List<TrackLayoutSwitchJoint>,
    val trackLinks: Map<IntId<LocationTrack>, SwitchLinkingTrackLinks>,
    val geometrySwitchId: IntId<GeometrySwitch>? = null,
    val name: SwitchName,
)

enum class TrackEnd {
    START,
    END,
}

data class SwitchLinkingTopologicalTrackLink(val number: JointNumber, val trackEnd: TrackEnd)

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

data class TrackLayoutSwitchSaveRequest(
    val name: SwitchName,
    val switchStructureId: IntId<SwitchStructure>,
    val stateCategory: LayoutStateCategory,
    val ownerId: IntId<SwitchOwner>,
    val trapPoint: Boolean?,
)

data class TrackNumberSaveRequest(
    val number: TrackNumber,
    val description: TrackNumberDescription,
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
    val gkLocationConfirmed: Boolean,
    val gkLocationSource: KmPostGkLocationSource?,
    val gkLocation: GeometryPoint?,
    val sourceId: IntId<GeometryKmPost>?,
) {
    init {
        gkLocation?.let { location ->
            try {
                if (!isGkFinSrid(location.srid)) {
                    throw LinkingFailureException(
                        message = "Given GK location SRID is not a GK coordinate system",
                        localizedMessageKey = "invalid-gk-srid",
                        localizedMessageParams = localizationParams("srid" to "${location.srid.code}"),
                    )
                }
                // We don't use the value here, but transform it as a form of validation
                transformNonKKJCoordinate(location.srid, LAYOUT_SRID, location)
            } catch (e: CoordinateTransformationException) {
                throw LinkingFailureException(
                    message = "Invalid GK location given for km-post",
                    localizedMessageKey = "invalid-gk-location",
                    localizedMessageParams =
                        localizationParams(
                            "x" to "${location.x}",
                            "y" to "${location.y}",
                            "srid" to "${location.srid.code}",
                        ),
                    cause = e,
                )
            }
        }
    }
}

data class KmPostLinkingParameters(
    val geometryPlanId: IntId<GeometryPlan>,
    val geometryKmPostId: IntId<GeometryKmPost>,
    val layoutKmPostId: IntId<TrackLayoutKmPost>,
)

data class SwitchRelinkingValidationResult(
    val id: IntId<TrackLayoutSwitch>,
    val successfulSuggestion: SwitchRelinkingSuggestion?,
    val validationIssues: List<LayoutValidationIssue>,
)

data class SwitchRelinkingSuggestion(val location: Point, val address: TrackMeter)

enum class TrackSwitchRelinkingResultType {
    RELINKED,
    NOT_AUTOMATICALLY_LINKABLE,
}

data class TrackSwitchRelinkingResult(val id: IntId<TrackLayoutSwitch>, val outcome: TrackSwitchRelinkingResultType)

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
