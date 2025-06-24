package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.CoordinateTransformationException
import fi.fta.geoviite.infra.geography.isGkFinSrid
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix
import fi.fta.geoviite.infra.tracklayout.LocationTrackNameSpecifier
import fi.fta.geoviite.infra.tracklayout.LocationTrackNamingScheme
import fi.fta.geoviite.infra.tracklayout.LocationTrackOwner
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType
import fi.fta.geoviite.infra.tracklayout.TrackDescriptionStructure
import fi.fta.geoviite.infra.tracklayout.TrackNameStructure

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
    val namingScheme: LocationTrackNamingScheme,
    val nameFreeText: AlignmentName?,
    val nameSpecifier: LocationTrackNameSpecifier?,
    val descriptionBase: LocationTrackDescriptionBase,
    val descriptionSuffix: LocationTrackDescriptionSuffix,
    val type: LocationTrackType,
    val state: LocationTrackState,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val duplicateOf: IntId<LocationTrack>?,
    val topologicalConnectivity: TopologicalConnectivityType,
    val ownerId: IntId<LocationTrackOwner>,
) {
    // Initialize these at construction time to ensure they are always valid
    val nameStructure = TrackNameStructure.of(namingScheme, nameFreeText, nameSpecifier)
    val descriptionStructure = TrackDescriptionStructure(descriptionBase, descriptionSuffix)
}

enum class TrackEnd {
    START,
    END,
}

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

data class LayoutKmPostSaveRequest(
    val kmNumber: KmNumber,
    val state: LayoutState,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val gkLocation: LayoutKmPostGkLocation?,
    val sourceId: IntId<GeometryKmPost>?,
) {
    init {
        gkLocation?.let { (location) ->
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
    val layoutKmPostId: IntId<LayoutKmPost>,
)

enum class TrackSwitchRelinkingResultType {
    RELINKED,
    NOT_AUTOMATICALLY_LINKABLE,
}

data class TrackSwitchRelinkingResult(val id: IntId<LayoutSwitch>, val outcome: TrackSwitchRelinkingResultType)
