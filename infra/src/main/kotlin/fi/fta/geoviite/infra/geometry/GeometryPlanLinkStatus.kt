package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack

data class GeometryPlanLinkStatus(
    val id: IntId<GeometryPlan>,
    val alignments: List<GeometryAlignmentLinkStatus>,
    val switches: List<GeometrySwitchLinkStatus>,
    val kmPosts: List<GeometryKmPostLinkStatus>,
)

data class GeometryAlignmentLinkStatus(
    val id: IntId<GeometryAlignment>,
    val elements: List<GeometryElementLinkStatus>,
) {
    val isLinked = elements.any(GeometryElementLinkStatus::isLinked)
    val linkedLocationTrackIds = elements.flatMap(GeometryElementLinkStatus::linkedLocationTrackIds).distinct()
    val linkedReferenceLineIds = elements.flatMap(GeometryElementLinkStatus::linkedReferenceLineIds).distinct()
}

data class GeometryElementLinkStatus(
    val id: IndexedId<GeometryElement>,
    val isLinked: Boolean,
    val linkedLocationTrackIds: List<IntId<LocationTrack>>,
    // TODO: GVT-3637 clean up naming
    val linkedReferenceLineIds: List<IntId<LayoutTrackNumber>>,
)

data class GeometrySwitchLinkStatus(val id: IntId<GeometrySwitch>, val isLinked: Boolean)

data class GeometryKmPostLinkStatus(val id: IntId<GeometryKmPost>, val linkedKmPosts: List<IntId<LayoutKmPost>>)
