package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class LayoutAlignmentService(private val dao: LayoutAlignmentDao) {

    fun update(alignment: LayoutAlignment) = dao.update(alignment)

    fun saveAsNew(alignment: LayoutAlignment): RowVersion<LayoutAlignment> = save(asNew(alignment))

    @Transactional
    fun duplicateOrNew(alignmentVersion: RowVersion<LayoutAlignment>?): RowVersion<LayoutAlignment> =
        alignmentVersion?.let(::duplicate) ?: newEmpty().second

    @Transactional
    fun duplicate(alignmentVersion: RowVersion<LayoutAlignment>): RowVersion<LayoutAlignment> =
        save(asNew(dao.fetch(alignmentVersion)))

    fun save(alignment: LayoutAlignment): RowVersion<LayoutAlignment> =
        if (alignment.dataType == DataType.STORED) dao.update(alignment) else dao.insert(alignment)

    fun newEmpty(): Pair<LayoutAlignment, RowVersion<LayoutAlignment>> {
        val alignment = emptyAlignment()
        return alignment to dao.insert(alignment)
    }

    fun getGeometryMetadataSections(
        alignmentVersion: RowVersion<LayoutAlignment>,
        externalId: Oid<*>?,
        boundingBox: BoundingBox?,
        context: GeocodingContext<ReferenceLineM>,
    ): List<AlignmentPlanSection<ReferenceLineM>> {
        val sections = dao.fetchSegmentGeometriesAndPlanMetadata(alignmentVersion, externalId, boundingBox)
        val alignment = dao.fetch(alignmentVersion)
        return toPlanSections(sections, alignment, context)
    }

    fun getGeometryMetadataSections(
        trackVersion: LayoutRowVersion<LocationTrack>,
        externalId: Oid<*>?,
        boundingBox: BoundingBox?,
        context: GeocodingContext<ReferenceLineM>,
    ): List<AlignmentPlanSection<LocationTrackM>> {
        val sections = dao.fetchSegmentGeometriesAndPlanMetadata(trackVersion, externalId, boundingBox)
        val alignment = dao.fetch(trackVersion)
        return toPlanSections(sections, alignment, context)
    }

    private fun <M : AlignmentM<M>> toPlanSections(
        sections: List<SegmentGeometryAndMetadata<M>>,
        alignment: IAlignment<M>,
        context: GeocodingContext<ReferenceLineM>,
    ): List<AlignmentPlanSection<M>> {
        return sections.mapNotNull { section ->
            val start = section.startPoint?.let { p -> toPlanSectionPoint(p, alignment, context) }
            val end = section.endPoint?.let { p -> toPlanSectionPoint(p, alignment, context) }

            if (start != null && end != null) {
                AlignmentPlanSection(
                    planId = section.planId,
                    planName = section.fileName,
                    alignmentId = section.alignmentId,
                    alignmentName = section.alignmentName,
                    start = start,
                    end = end,
                    isLinked = section.isLinked,
                    id = section.id,
                )
            } else {
                null
            }
        }
    }
}

private fun <M : AlignmentM<M>> toPlanSectionPoint(
    point: IPoint,
    alignment: IAlignment<M>,
    context: GeocodingContext<ReferenceLineM>,
) =
    context.getAddress(point)?.let { (address, _) ->
        PlanSectionPoint(
            address = address,
            location = point,
            m =
                alignment.getClosestPointM(point)?.first
                    ?: throw IllegalArgumentException("Could not find closest point for $point"),
        )
    }

private fun asNew(alignment: LayoutAlignment): LayoutAlignment =
    if (alignment.dataType == TEMP) alignment else alignment.copy(id = StringId(), dataType = TEMP)

fun cropIsWithinReferenceLine(
    startKmNumber: KmNumber?,
    endKmNumber: KmNumber?,
    geocodingContext: GeocodingContext<ReferenceLineM>,
): Boolean =
    geocodingContext.kmRange?.let { contextRange ->
        when {
            startKmNumber == null && endKmNumber == null -> true
            startKmNumber == null -> requireNotNull(endKmNumber) >= contextRange.min
            endKmNumber == null -> startKmNumber <= contextRange.max
            startKmNumber > endKmNumber -> false
            else -> Range(startKmNumber, endKmNumber).overlaps(contextRange)
        }
    } ?: false
