package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class AlignmentMetadataService(
    private val alignmentDao: LayoutAlignmentDao,
    private val geocodingService: GeocodingService,
    private val locationTrackDao: LocationTrackDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
) {

    @Transactional(readOnly = true)
    fun getLocationTrackMetadataSections(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection<LocationTrackM>> {
        return locationTrackDao.get(layoutContext, locationTrackId)?.let { locationTrack ->
            geocodingService.getGeocodingContext(layoutContext, locationTrack.trackNumberId)?.let { geocodingContext ->
                getGeometryMetadataSections(
                    locationTrack.getVersionOrThrow(),
                    locationTrackDao.fetchExternalId(layoutContext.branch, locationTrackId)?.oid,
                    boundingBox,
                    geocodingContext,
                )
            }
        } ?: listOf()
    }

    @Transactional(readOnly = true)
    fun getReferenceLineMetadataSections(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection<ReferenceLineM>> {
        return referenceLineDao.getByTrackNumber(layoutContext, trackNumberId)?.let { referenceLine ->
            geocodingService.getGeocodingContext(layoutContext, trackNumberId)?.let { geocodingContext ->
                getGeometryMetadataSections(
                    referenceLine.getGeometryVersionOrThrow(),
                    trackNumberDao.fetchExternalId(layoutContext.branch, trackNumberId)?.oid,
                    boundingBox,
                    geocodingContext,
                )
            }
        } ?: listOf()
    }

    private fun getGeometryMetadataSections(
        geometryVersion: RowVersion<ReferenceLineGeometry>,
        externalId: Oid<*>?,
        boundingBox: BoundingBox?,
        context: GeocodingContext<ReferenceLineM>,
    ): List<AlignmentPlanSection<ReferenceLineM>> {
        val sections = alignmentDao.fetchSegmentGeometriesAndPlanMetadata(geometryVersion, externalId, boundingBox)
        val geometry = alignmentDao.fetch(geometryVersion)
        return toPlanSections(sections, geometry, context)
    }

    private fun getGeometryMetadataSections(
        trackVersion: LayoutRowVersion<LocationTrack>,
        externalId: Oid<*>?,
        boundingBox: BoundingBox?,
        context: GeocodingContext<ReferenceLineM>,
    ): List<AlignmentPlanSection<LocationTrackM>> {
        val sections = alignmentDao.fetchSegmentGeometriesAndPlanMetadata(trackVersion, externalId, boundingBox)
        val geometry = alignmentDao.fetch(trackVersion)
        return toPlanSections(sections, geometry, context)
    }
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
