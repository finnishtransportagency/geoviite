package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LayoutAlignmentService(
    private val dao: LayoutAlignmentDao,
) {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun update(alignment: LayoutAlignment) = dao.update(alignment)

    fun saveAsNew(alignment: LayoutAlignment): RowVersion<LayoutAlignment> = save(asNew(alignment))

    @Transactional
    fun duplicateOrNew(alignmentVersion: RowVersion<LayoutAlignment>?): RowVersion<LayoutAlignment> =
        alignmentVersion?.let(::duplicate) ?: newEmpty().second

    @Transactional
    fun duplicate(alignmentVersion: RowVersion<LayoutAlignment>): RowVersion<LayoutAlignment> =
        save(asNew(dao.fetch(alignmentVersion)))

    fun save(alignment: LayoutAlignment): RowVersion<LayoutAlignment> =
        if (alignment.dataType == DataType.STORED) dao.update(alignment)
        else dao.insert(alignment)

    fun newEmpty(): Pair<LayoutAlignment, RowVersion<LayoutAlignment>> {
        val alignment = emptyAlignment()
        return alignment to dao.insert(alignment)
    }

    fun getGeometryMetadataSections(
        alignmentVersion: RowVersion<LayoutAlignment>,
        externalId: Oid<*>?,
        boundingBox: BoundingBox?,
        context: GeocodingContext,
    ): List<AlignmentPlanSection> {
        logger.serviceCall(
            "getGeometryMetadataSections",
            "alignmentVersion" to alignmentVersion,
            "externalId" to externalId,
            "boundingBox" to boundingBox
        )
        val sections = dao.fetchSegmentGeometriesAndPlanMetadata(alignmentVersion, externalId, boundingBox)
        return sections.mapNotNull { section ->
            val startAddress = if (section.startPoint != null) context.getAddress(section.startPoint)?.first else null
            val endAddress = if (section.endPoint != null) context.getAddress(section.endPoint)?.first else null

            if (startAddress != null && endAddress != null) AlignmentPlanSection(
                planId = section.planId,
                planName = section.fileName,
                startAddress = startAddress,
                endAddress = endAddress,
                id = section.id,
            ) else null
        }
    }
}

fun foldSegmentsByPlan(segments: List<SegmentGeometryAndMetadata>) =
    segments.fold(mutableListOf<SegmentGeometryAndMetadata>()) { acc, element ->
        val last = acc.lastOrNull()
        if (last == null || last.planId != element.planId || last.fileName != element.fileName || last.source != element.source) acc.add(
            element
        )
        else acc.set(acc.lastIndex, last.copy(endPoint = element.endPoint))
        acc
    }

private fun asNew(alignment: LayoutAlignment) =
    if (alignment.dataType == TEMP) alignment
    else alignment.copy(id = StringId(), dataType = TEMP)
