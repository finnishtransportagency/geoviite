package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.map.AlignmentHeader
import fi.fta.geoviite.infra.map.AlignmentPolyLine
import fi.fta.geoviite.infra.map.toAlignmentPolyLine
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.util.FileName
import java.math.BigDecimal

data class GeometryPlanLayout(
    val fileName: FileName,
    val alignments: List<PlanLayoutAlignment>,
    val switches: List<LayoutSwitch>,
    val kmPosts: List<LayoutKmPost>,
    val id: DomainId<GeometryPlan>,
    val planHidden: Boolean,
    val planDataType: DataType,
    val startAddress: TrackMeter?,
) : Loggable {
    val boundingBox: BoundingBox? = boundingBoxCombining(alignments.mapNotNull { a -> a.boundingBox })

    fun withLayoutGeometry(resolution: Int? = null): GeometryPlanLayout =
        copy(
            alignments =
                alignments.map { alignment ->
                    alignment.copy(
                        polyLine =
                            toAlignmentPolyLine(
                                alignment.id,
                                alignment.header.alignmentType,
                                alignment,
                                resolution,
                                includeSegmentEndPoints = true,
                            )
                    )
                }
        )

    override fun toLog(): String =
        logFormat(
            "plan" to id,
            "alignments" to alignments.map(PlanLayoutAlignment::toLog),
            "switches" to switches.map(LayoutSwitch::toLog),
            "kmPosts" to kmPosts.map(LayoutKmPost::toLog),
        )
}

data class PlanLayoutAlignment(
    val header: AlignmentHeader<GeometryAlignment, LayoutState>,
    @JsonIgnore override val segments: List<PlanLayoutSegment>,
    @JsonIgnore val staStart: Double,
    val polyLine: AlignmentPolyLine<GeometryAlignment>? = null,
) : IAlignment {
    override val segmentMValues: List<Range<Double>> by lazy { calculateSegmentMValues(segments) }

    @get:JsonIgnore
    val id: DomainId<GeometryAlignment>
        get() = header.id

    @get:JsonIgnore
    override val boundingBox: BoundingBox?
        get() = header.boundingBox

    override fun toLog(): String =
        logFormat("id" to id, "name" to header.name, "segments" to segments.size, "points" to polyLine?.points?.size)
}

data class PlanLayoutSegment(
    @JsonIgnore override val geometry: SegmentGeometry,
    val pointCount: Int,
    override val sourceId: DomainId<GeometryElement>?,
    override val sourceStartM: BigDecimal?,
    override val source: GeometrySource,
    val id: DomainId<LayoutSegment>,
) : ISegment, ISegmentGeometry by geometry
