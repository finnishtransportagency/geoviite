package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.math.BoundingBox
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LayoutAlignmentService(
    private val dao: LayoutAlignmentDao,
) {
    fun update(alignment: LayoutAlignment) = dao.update(alignment)

    fun saveAsNew(alignment: LayoutAlignment): RowVersion<LayoutAlignment> = save(asNew(alignment))

    @Transactional
    fun duplicateOrNew(alignmentVersion: RowVersion<LayoutAlignment>?): RowVersion<LayoutAlignment> =
        alignmentVersion?.let(::duplicate) ?: newEmpty().second

    @Transactional
    fun duplicate(alignmentVersion: RowVersion<LayoutAlignment>): RowVersion<LayoutAlignment> =
        save(asNew(dao.fetch(alignmentVersion)))

    fun save(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        return if (alignment.dataType == DataType.STORED) dao.update(alignment)
        else dao.insert(alignment)
    }

    fun newEmpty(): Pair<LayoutAlignment, RowVersion<LayoutAlignment>> {
        val alignment = emptyAlignment()
        return alignment to dao.insert(alignment)
    }

    fun getGeometrySectionsByPlan(
        publishType: PublishType,
        id: IntId<LayoutAlignment>,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        boundingBox: BoundingBox?
    ): List<SegmentGeometryAndMetadata> {
        val segmentGeometriesAndMetadatas = dao.fetchSegmentGeometriesAndPlanMetadata(id)

        val filtered = if (boundingBox != null) filterAndClipSegmentsToBoundingBox(
            segmentGeometriesAndMetadatas,
            boundingBox
        ) else segmentGeometriesAndMetadatas
        return foldSegmentsByPlan(filtered)
    }
}

private fun foldSegmentsByPlan(segments: List<SegmentGeometryAndMetadata>) =
    segments.fold(mutableListOf<SegmentGeometryAndMetadata>()) { acc, element ->
        val last = acc.lastOrNull()
        if (last == null || last.planId != element.planId || last.source != element.source) acc.add(
            element
        )
        else acc.set(acc.lastIndex, last.copy(points = last.points + element.points))
        acc
    }

private fun clipSegmentPointsToBoundingBox(segment: SegmentGeometryAndMetadata, boundingBox: BoundingBox) =
    segment.copy(points = segment.points.filter { boundingBox.contains(it) })

private fun filterAndClipSegmentsToBoundingBox(
    segments: List<SegmentGeometryAndMetadata>,
    boundingBox: BoundingBox
): List<SegmentGeometryAndMetadata> {
    val firstIndex =
        segments.indexOfFirst { segment -> segment.points.any{ boundingBox.contains(it) } }
    val lastIndex =
        segments.indexOfLast { segment -> segment.points.any{ boundingBox.contains(it) } }

    if (firstIndex < 0 && lastIndex < 0) return emptyList()
    if (firstIndex == lastIndex) return listOf(clipSegmentPointsToBoundingBox(segments[firstIndex], boundingBox))

    val firstSegmentClipped = clipSegmentPointsToBoundingBox(segments[firstIndex], boundingBox)
    val lastSegmentClipped = clipSegmentPointsToBoundingBox(segments[lastIndex], boundingBox)
    return listOf(firstSegmentClipped) + segments.subList(firstIndex + 1, lastIndex) + listOf(lastSegmentClipped)
}

private fun asNew(alignment: LayoutAlignment) =
    if (alignment.dataType == TEMP) alignment
    else alignment.copy(id = StringId(), dataType = TEMP)
