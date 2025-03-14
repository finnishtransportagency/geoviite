package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.bufferedPolygonForLineStringPoints
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlanHeader
import fi.fta.geoviite.infra.linking.switches.CroppedAlignment
import fi.fta.geoviite.infra.map.simplify
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class LayoutAlignmentService(
    private val dao: LayoutAlignmentDao,
    private val geometryDao: GeometryDao,
    private val geocodingService: GeocodingService,
) {

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
        context: GeocodingContext,
    ): List<AlignmentPlanSection> {
        val sections = dao.fetchSegmentGeometriesAndPlanMetadata(alignmentVersion, externalId, boundingBox)
        val alignment = dao.fetch(alignmentVersion)
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

    fun getOverlappingPlanHeaders(
        alignmentVersion: RowVersion<LayoutAlignment>,
        geocodingContextCacheKey: GeocodingContextCacheKey,
        startKmNumber: KmNumber?,
        endKmNumber: KmNumber?,
    ): List<GeometryPlanHeader> {
        val croppedAlignment = cropAlignment(alignmentVersion, geocodingContextCacheKey, startKmNumber, endKmNumber)
        val simplified = simplify(croppedAlignment, 100, null, true)

        val polygon = bufferedPolygonForLineStringPoints(simplified, 10.0, LAYOUT_SRID)
        val plans = geometryDao.fetchIntersectingPlans(polygon)
        return geometryDao.getPlanHeaders(plans)
    }

    private fun cropAlignment(
        alignmentVersion: RowVersion<LayoutAlignment>,
        geocodingContextCacheKey: GeocodingContextCacheKey,
        startKmNumber: KmNumber?,
        endKmNumber: KmNumber?,
    ): IAlignment {
        val alignment = dao.fetch(alignmentVersion)
        if (startKmNumber == null && endKmNumber == null) return alignment
        val geocodingContext = geocodingService.getGeocodingContext(geocodingContextCacheKey)
        val startM =
            requireNotNull(
                startKmNumber?.let {
                    geocodingContext?.referencePoints?.find { it.kmNumber == startKmNumber }?.distance
                } ?: alignment.start?.m
            )
        val endM =
            requireNotNull(
                endKmNumber?.let { geocodingContext?.referencePoints?.find { it.kmNumber >= endKmNumber }?.distance }
                    ?: alignment.end?.m
            )

        val startSegment =
            alignment.segments
                .find { s -> startM in s.startM..s.endM }
                ?.let { s -> if (startM < s.endM) s.slice(Range(startM, s.endM)) else null }
        val endSegment =
            alignment.segments
                .find { s -> endM in s.startM..s.endM }
                ?.let { s -> if (s.startM < endM) s.slice(Range(s.startM, endM)) else null }
        val midSegments = alignment.segments.filter { s -> s.startM > startM && s.endM < endM }

        val segments = listOfNotNull(startSegment) + midSegments + listOfNotNull(endSegment)
        val croppedAlignment = CroppedAlignment(0, segments, alignment.id)
        return croppedAlignment
    }
}

private fun toPlanSectionPoint(point: IPoint, alignment: LayoutAlignment, context: GeocodingContext) =
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
