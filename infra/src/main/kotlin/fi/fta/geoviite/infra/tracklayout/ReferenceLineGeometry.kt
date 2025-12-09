package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.round
import kotlin.math.abs

data class ReferenceLineGeometry(
    override val segments: List<LayoutSegment>,
    val id: DomainId<ReferenceLineGeometry> = StringId(),
    val dataType: DataType = DataType.TEMP,
) : IAlignment<ReferenceLineM> {
    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(segments.map { s -> s.boundingBox }) }
    override val segmentMValues: List<Range<LineM<ReferenceLineM>>> = calculateSegmentMValues(segments)
    @get:JsonIgnore
    override val segmentsWithM: List<Pair<LayoutSegment, Range<LineM<ReferenceLineM>>>>
        get() = segments.zip(segmentMValues)

    private val spatialIndex: SegmentSpatialIndex by lazy { SegmentSpatialIndex(segments) }

    override fun approximateClosestSegmentIndex(target: IPoint): Int? = spatialIndex.findClosest(target)

    init {
        segments.forEachIndexed { index, segment ->
            val m = segmentMValues[index]
            require(abs(segment.length - (m.max.distance - m.min.distance)) < LAYOUT_M_DELTA)

            if (index == 0) {
                require(m.min.distance == 0.0) {
                    "First segment should start at 0.0: alignment=$id firstStart=${m.min.distance}"
                }
            } else {
                val previous = segments[index - 1]
                val previousM = segmentMValues[index - 1]
                require(previous.segmentEnd.isSame(segment.segmentStart, LAYOUT_COORDINATE_DELTA)) {
                    "Alignment segment doesn't start where the previous one ended: " +
                        "alignment=$id segment=$index length=${segment.length} prevLength=${previous.length} " +
                        "diff=${lineLength(previous.segmentEnd, segment.segmentStart)}"
                }
                require(isSame(previousM.max.distance, m.min.distance, LAYOUT_M_DELTA)) {
                    "Alignment segment m-calculation should be continuous: " +
                        "alignment=$id segment=$index prev=$previousM next=$m"
                }
            }
        }
    }

    fun withSegments(newSegments: List<LayoutSegment>) = copy(segments = newSegments)

    override fun toLog(): String = logFormat("id" to id, "segments" to segments.size, "length" to round(length, 3))
}
