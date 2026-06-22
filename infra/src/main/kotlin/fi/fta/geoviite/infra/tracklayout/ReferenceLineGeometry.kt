package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.round
import kotlin.math.abs

data class DbReferenceLineGeometry(
    override val segments: List<LayoutSegment>,
    @get:JsonIgnore val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
) : ReferenceLineGeometry() {
    override val trackNumberId: IntId<LayoutTrackNumber>
        get() = trackNumberVersion.id

    init {
        validateSegments()
    }
}

data class TmpReferenceLineGeometry(
    override val segments: List<LayoutSegment>,
    override val trackNumberId: IntId<LayoutTrackNumber>?,
) : ReferenceLineGeometry() {

    init {
        validateSegments()
    }

    companion object {
        val empty = TmpReferenceLineGeometry(emptyList(), null)
    }
}

sealed class ReferenceLineGeometry : IAlignment<ReferenceLineM> {
    abstract val trackNumberId: IntId<LayoutTrackNumber>?
    abstract override val segments: List<LayoutSegment>

    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(segments.map { s -> s.boundingBox }) }
    override val segmentMValues: List<Range<LineM<ReferenceLineM>>> by lazy { calculateSegmentMValues(segments) }
    @get:JsonIgnore
    override val segmentsWithM: List<Pair<LayoutSegment, Range<LineM<ReferenceLineM>>>>
        get() = segments.zip(segmentMValues)

    private val spatialIndex: SegmentSpatialIndex by lazy { SegmentSpatialIndex(segments) }

    override fun approximateClosestSegmentIndex(target: IPoint): Int? = spatialIndex.findClosest(target)

    protected fun validateSegments() {
        segments.forEachIndexed { index, segment ->
            val m = segmentMValues[index]
            require(abs(segment.length - (m.max.distance - m.min.distance)) < LAYOUT_M_DELTA)

            if (index == 0) {
                require(m.min.distance == 0.0) {
                    "First segment should start at 0.0: trackNumberId=$trackNumberId firstStart=${m.min.distance}"
                }
            } else {
                val previous = segments[index - 1]
                val previousM = segmentMValues[index - 1]
                require(previous.segmentEnd.isSame(segment.segmentStart, LAYOUT_COORDINATE_DELTA)) {
                    "Alignment segment doesn't start where the previous one ended: " +
                        "trackNumberId=$trackNumberId segment=$index length=${segment.length} prevLength=${previous.length} " +
                        "diff=${lineLength(previous.segmentEnd, segment.segmentStart)}"
                }
                require(isSame(previousM.max.distance, m.min.distance, LAYOUT_M_DELTA)) {
                    "Alignment segment m-calculation should be continuous: " +
                        "trackNumberId=$trackNumberId segment=$index prev=$previousM next=$m"
                }
            }
        }
    }

    fun withSegments(newSegments: List<LayoutSegment>) = TmpReferenceLineGeometry(segments = newSegments, trackNumberId)

    override fun toLog(): String =
        logFormat("trackNumberId" to trackNumberId, "segments" to segments.size, "length" to round(length, 3))
}
