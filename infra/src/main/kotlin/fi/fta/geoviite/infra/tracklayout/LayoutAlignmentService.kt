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
import kotlin.math.max
import kotlin.math.min
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
        polygonBufferSize: Double,
        startKmNumber: KmNumber?,
        endKmNumber: KmNumber?,
    ): List<GeometryPlanHeader> {
        val alignment = layoutAlignmentDao.fetch(alignmentVersion)
        val geocodingContext = requireNotNull(geocodingService.getGeocodingContext(geocodingContextCacheKey))
        val (cropStartM, cropEndM) = getCropMValues(geocodingContext, startKmNumber, endKmNumber)

        return if (!cropRangeOverlapsAlignment(alignment, cropStartM, cropEndM)) {
            emptyList()
        } else {
            val simplifiedAlignment =
                simplify(
                    alignment =
                        if (startKmNumber == null && endKmNumber == null) alignment
                        else cropAlignment(alignment, cropStartM, cropEndM),
                    resolution = ALIGNMENT_POLYGON_SIMPLIFICATION_RESOLUTION,
                    bbox = null,
                    includeSegmentEndPoints = true,
                )

            val polygon = bufferedPolygonForLineStringPoints(simplifiedAlignment, polygonBufferSize, LAYOUT_SRID)
            val plans = geometryDao.fetchIntersectingPlans(polygon, LAYOUT_SRID)

            geometryDao.getPlanHeaders(plans)
        }
    }

    private fun cropAlignment(alignment: LayoutAlignment, cropStartM: Double?, cropEndM: Double?): IAlignment {
        val alignmentStartM = requireNotNull(alignment.start?.m)
        val alignmentEndM = requireNotNull(alignment.end?.m)
        val startM = if (cropStartM != null) max(cropStartM, alignmentStartM) else alignmentStartM
        val endM = if (cropEndM != null) min(cropEndM, alignmentEndM) else alignmentEndM
        val mRange = Range(startM, endM)

        val segments =
            alignment.segments.filter { s ->
                val sRange = Range(s.startM, s.endM)
                sRange.overlaps(mRange) || mRange.overlaps(sRange)
            }
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

    private fun getCropMValues(
        geocodingContext: GeocodingContext,
        startKmNumber: KmNumber?,
        endKmNumber: KmNumber?,
    ): Pair<Double?, Double?> {
        val startM =
            startKmNumber?.let { geocodingContext.referencePoints.find { it.kmNumber == startKmNumber }?.distance }
        val endM = endKmNumber?.let { geocodingContext.referencePoints.find { it.kmNumber > endKmNumber }?.distance }
        return startM to endM
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

private fun cropRangeOverlapsAlignment(alignment: LayoutAlignment, cropStartM: Double?, cropEndM: Double?): Boolean {
    val alignmentStartM = requireNotNull(alignment.start?.m)
    val alignmentEndM = requireNotNull(alignment.end?.m)

    return if (cropStartM != null && cropEndM != null) {
        cropStartM < cropEndM && Range(cropStartM, cropEndM).overlaps(Range(alignmentStartM, alignmentEndM))
    } else if (cropStartM != null && cropEndM == null) {
        cropStartM <= alignmentEndM
    } else if (cropStartM == null && cropEndM != null) {
        cropEndM >= alignmentStartM
    } else {
        true
    }
}
