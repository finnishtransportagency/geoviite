package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.util.FileName

data class VIStartOrEnd(
    val address: TrackMeter?,
    val height: Double,
    val angle: Double,
    val station: Double,
)

data class VIListingPoint(
    val address: TrackMeter?,
    val height: Double,
    val station: Double,
)

data class LinearSection(
    val length: Double,
    val linearSection: Double,
)

data class ProfileListing(
    val id: StringId<ElementListing>,
    val planId: DomainId<GeometryPlan>?,
    val planSource: PlanSource?,
    val fileName: FileName?,

    val alignmentId: DomainId<GeometryAlignment>?,
    val alignmentName: AlignmentName?,

    val locationTrackName: AlignmentName?,
    val start: VIStartOrEnd,
    val end: VIStartOrEnd,
    val point: VIListingPoint,
    val radius: Double,
    val tangent: Double,
    val linearSectionForward: LinearSection?,
    val linearSectionBackward: LinearSection?,
)
