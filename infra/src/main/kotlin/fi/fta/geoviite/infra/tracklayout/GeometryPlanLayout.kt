package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.map.AlignmentHeader
import fi.fta.geoviite.infra.map.AlignmentPolyLine
import fi.fta.geoviite.infra.map.getSegmentBorderMValues
import fi.fta.geoviite.infra.map.toAlignmentPolyLine
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.util.FileName

data class GeometryPlanLayout(
    val fileName: FileName,
    val alignments: List<PlanLayoutAlignment>,
    val switches: List<TrackLayoutSwitch>,
    val kmPosts: List<TrackLayoutKmPost>,
    val planId: DomainId<GeometryPlan>,
    val planDataType: DataType,
    val startAddress: TrackMeter?,
) {
    val boundingBox: BoundingBox? = boundingBoxCombining(alignments.mapNotNull { a -> a.boundingBox })

    fun withLayoutGeometry(resolution: Int? = null) = copy(
        alignments = alignments.map { alignment -> alignment.copy(
            polyLine = toAlignmentPolyLine(alignment.id, alignment.header.alignmentType, alignment, resolution),
            segmentMValues = getSegmentBorderMValues(alignment),
        )},
    )
}

data class PlanLayoutAlignment(
    val header: AlignmentHeader<GeometryAlignment>,
    @JsonIgnore
    override val segments: List<PlanLayoutSegment>,
    val polyLine: AlignmentPolyLine<GeometryAlignment>? = null,
    val segmentMValues: List<Double> = listOf(),
): IAlignment {
    @get:JsonIgnore
    override val id: DomainId<GeometryAlignment> get() = header.id
    @get:JsonIgnore
    override val boundingBox: BoundingBox? get() = header.boundingBox
}

data class PlanLayoutSegment(
    @JsonIgnore
    override val geometry: SegmentGeometry,
    val pointCount: Int,
    override val sourceId: DomainId<GeometryElement>?,
    override val sourceStart: Double?,
    override val source: GeometrySource,
    override val id: DomainId<LayoutSegment>,
): ISegment, ISegmentGeometry by geometry

