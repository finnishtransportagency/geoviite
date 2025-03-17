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

const val ALIGNMENT_POLYGON_BUFFER = 10.0
const val ALIGNMENT_POLYGON_SIMPLIFICATION_RESOLUTION = 100

@GeoviiteService
class LayoutAlignmentService(
    private val layoutAlignmentDao: LayoutAlignmentDao,
    private val geometryDao: GeometryDao,
    private val geocodingService: GeocodingService,
) {

    fun update(alignment: LayoutAlignment) = layoutAlignmentDao.update(alignment)

    fun saveAsNew(alignment: LayoutAlignment): RowVersion<LayoutAlignment> = save(asNew(alignment))

    @Transactional
    fun duplicateOrNew(alignmentVersion: RowVersion<LayoutAlignment>?): RowVersion<LayoutAlignment> =
        alignmentVersion?.let(::duplicate) ?: newEmpty().second

    @Transactional
    fun duplicate(alignmentVersion: RowVersion<LayoutAlignment>): RowVersion<LayoutAlignment> =
        save(asNew(layoutAlignmentDao.fetch(alignmentVersion)))

    fun save(alignment: LayoutAlignment): RowVersion<LayoutAlignment> =
        if (alignment.dataType == DataType.STORED) layoutAlignmentDao.update(alignment)
        else layoutAlignmentDao.insert(alignment)

    fun newEmpty(): Pair<LayoutAlignment, RowVersion<LayoutAlignment>> {
        val alignment = emptyAlignment()
        return alignment to layoutAlignmentDao.insert(alignment)
    }

    fun getGeometryMetadataSections(
        alignmentVersion: RowVersion<LayoutAlignment>,
        externalId: Oid<*>?,
        boundingBox: BoundingBox?,
        context: GeocodingContext,
    ): List<AlignmentPlanSection> {
        val sections =
            layoutAlignmentDao.fetchSegmentGeometriesAndPlanMetadata(alignmentVersion, externalId, boundingBox)
        val alignment = layoutAlignmentDao.fetch(alignmentVersion)
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
        val croppedAlignment =
            if (startKmNumber == null && endKmNumber == null) layoutAlignmentDao.fetch(alignmentVersion)
            else cropAlignment(alignmentVersion, geocodingContextCacheKey, startKmNumber, endKmNumber)
        val simplified =
            simplify(
                croppedAlignment,
                ALIGNMENT_POLYGON_SIMPLIFICATION_RESOLUTION,
                bbox = null,
                includeSegmentEndPoints = true,
            )

        val polygon = bufferedPolygonForLineStringPoints(simplified, ALIGNMENT_POLYGON_BUFFER, LAYOUT_SRID)
        val plans = geometryDao.fetchIntersectingPlans(polygon, LAYOUT_SRID)
        return geometryDao.getPlanHeaders(plans)
    }

    private fun cropAlignment(
        alignmentVersion: RowVersion<LayoutAlignment>,
        geocodingContextCacheKey: GeocodingContextCacheKey,
        startKmNumber: KmNumber?,
        endKmNumber: KmNumber?,
    ): IAlignment {
        val alignment = layoutAlignmentDao.fetch(alignmentVersion)

        val geocodingContext = geocodingService.getGeocodingContext(geocodingContextCacheKey)
        val startM =
            requireNotNull(
                startKmNumber?.let {
                    geocodingContext?.referencePoints?.find { it.kmNumber == startKmNumber }?.distance
                } ?: alignment.start?.m
            )
        val endM =
            requireNotNull(
                endKmNumber?.let { geocodingContext?.referencePoints?.findLast { it.kmNumber > endKmNumber }?.distance }
                    ?: alignment.end?.m
            )
        val mRange = Range(startM, endM)

        val segments = alignment.segments.filter { s -> mRange.contains(s.startM) || mRange.contains(s.endM) }
        val startSegment = segments.firstOrNull()
        val endSegment = segments.lastOrNull()
        val midSegments = segments.drop(1).dropLast(1)

        val croppedSegments =
            if (startSegment == endSegment) {
                listOfNotNull(startSegment?.slice(Range(startM, endM)))
            } else {
                val croppedStartSegment = startSegment?.slice(Range(startM, startSegment.endM))
                val croppedEndSegment = endSegment?.slice(Range(endSegment.startM, endM))
                listOfNotNull(croppedStartSegment) + midSegments + listOfNotNull(croppedEndSegment)
            }

        return CroppedAlignment(0, croppedSegments, alignment.id)
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
