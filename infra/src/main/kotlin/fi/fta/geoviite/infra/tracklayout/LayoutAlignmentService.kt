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
        polygonBufferSize: Double,
        startKmNumber: KmNumber?,
        endKmNumber: KmNumber?,
    ): List<GeometryPlanHeader> {
        val alignment = layoutAlignmentDao.fetch(alignmentVersion)
        val geocodingContext = requireNotNull(geocodingService.getGeocodingContext(geocodingContextCacheKey))
        if (!cropKmsAreWithinGeocodingContext(startKmNumber, endKmNumber, geocodingContext)) {
            return emptyList()
        }

        val (cropStartM, cropEndM) = getMValuesAlongReferenceLineForCrop(geocodingContext, startKmNumber, endKmNumber)
        if (!alignmentOverlapsCropRange(alignment, geocodingContext, cropStartM, cropEndM)) {
            return emptyList()
        } else {
            val (alignmentStartMAlongReferenceLine, alignmentEndMAlongReferenceLine) =
                getAlignmentBoundsInReferenceLineM(alignment, geocodingContext)

            val cropStartMAlongAlignment =
                cropStartM?.coerceIn(alignmentStartMAlongReferenceLine, alignmentEndMAlongReferenceLine)?.let {
                    it - alignmentStartMAlongReferenceLine
                }
            val cropEndMAlongAlignment =
                cropEndM?.coerceIn(alignmentStartMAlongReferenceLine, alignmentEndMAlongReferenceLine)?.let {
                    it - alignmentStartMAlongReferenceLine
                }

            val simplifiedAlignment =
                simplify(
                    alignment =
                        if (startKmNumber == null && endKmNumber == null) alignment
                        else cropAlignment(alignment, cropStartMAlongAlignment, cropEndMAlongAlignment),
                    resolution = ALIGNMENT_POLYGON_SIMPLIFICATION_RESOLUTION,
                    bbox = null,
                    includeSegmentEndPoints = true,
                )

            val polygon = bufferedPolygonForLineStringPoints(simplifiedAlignment, polygonBufferSize, LAYOUT_SRID)
            val plans = geometryDao.fetchIntersectingPlans(polygon, LAYOUT_SRID)

            return geometryDao.getPlanHeaders(plans)
        }
    }

    private fun cropAlignment(
        alignment: LayoutAlignment,
        cropStartMAlongAlignment: Double?,
        cropEndMAlongAlignment: Double?,
    ): IAlignment {
        val alignmentStartM = requireNotNull(alignment.start?.m)
        val alignmentEndM = requireNotNull(alignment.end?.m)
        val startMOnAlignment = cropStartMAlongAlignment ?: alignmentStartM
        val endMOnAlignment = cropEndMAlongAlignment ?: alignmentEndM
        val mRange = Range(startMOnAlignment, endMOnAlignment)

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
                listOfNotNull(startSegment?.slice(Range(startMOnAlignment, endMOnAlignment)))
            } else {
                val croppedStartSegment = startSegment?.slice(Range(startMOnAlignment, startSegment.endM))
                val croppedEndSegment = endSegment?.slice(Range(endSegment.startM, endMOnAlignment))
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

private fun alignmentOverlapsCropRange(
    alignment: LayoutAlignment,
    geocodingContext: GeocodingContext,
    cropStartMAlongReferenceLine: Double?,
    cropEndMAlongReferenceLine: Double?,
): Boolean {
    val alignmentStartM = requireNotNull(geocodingContext.getM(requireNotNull(alignment.start))?.first)
    val alignmentEndM = requireNotNull(geocodingContext.getM(requireNotNull(alignment.end))?.first)

    if (cropStartMAlongReferenceLine != null && cropEndMAlongReferenceLine == null) {
        return cropStartMAlongReferenceLine <= alignmentEndM
    } else if (cropStartMAlongReferenceLine == null && cropEndMAlongReferenceLine != null) {
        return cropEndMAlongReferenceLine >= alignmentStartM
    } else if (cropStartMAlongReferenceLine != null && cropEndMAlongReferenceLine != null) {
        val cropRange = Range(cropStartMAlongReferenceLine, cropEndMAlongReferenceLine)
        val alignmentRange = Range(alignmentStartM, alignmentEndM)

        return cropStartMAlongReferenceLine < cropEndMAlongReferenceLine &&
            ((cropRange.overlaps(alignmentRange) || alignmentRange.overlaps(cropRange)))
    } else {
        return true
    }
}

private fun cropKmsAreWithinGeocodingContext(
    startKmNumber: KmNumber?,
    endKmNumber: KmNumber?,
    geocodingContext: GeocodingContext,
): Boolean {
    if (geocodingContext.referencePoints.isEmpty()) {
        return false
    } else if (startKmNumber != null && endKmNumber == null) {
        return geocodingContext.referencePoints.last().kmNumber >= startKmNumber
    } else if (startKmNumber == null && endKmNumber != null) {
        return geocodingContext.referencePoints.first().kmNumber <= endKmNumber
    } else if (startKmNumber != null && endKmNumber != null && startKmNumber <= endKmNumber) {
        val kmNumberRange = Range(startKmNumber, endKmNumber)
        val geocodingRange =
            Range(geocodingContext.referencePoints.first().kmNumber, geocodingContext.referencePoints.last().kmNumber)
        return kmNumberRange.overlaps(geocodingRange) || geocodingRange.overlaps(kmNumberRange)
    } else {
        return startKmNumber == null && endKmNumber == null
    }
}

private fun getAlignmentBoundsInReferenceLineM(
    alignment: LayoutAlignment,
    geocodingContext: GeocodingContext,
): Pair<Double, Double> {
    val alignmentStartM = requireNotNull(geocodingContext.getM(requireNotNull(alignment.start))?.first)
    val alignmentEndM = requireNotNull(geocodingContext.getM(requireNotNull(alignment.end))?.first)
    return alignmentStartM to alignmentEndM
}

private fun getMValuesAlongReferenceLineForCrop(
    geocodingContext: GeocodingContext,
    startKmNumber: KmNumber?,
    endKmNumber: KmNumber?,
): Pair<Double?, Double?> {
    val startM = startKmNumber?.let { geocodingContext.referencePoints.find { it.kmNumber == startKmNumber }?.distance }
    val endM = endKmNumber?.let { geocodingContext.referencePoints.find { it.kmNumber > endKmNumber }?.distance }
    return startM to endM
}
