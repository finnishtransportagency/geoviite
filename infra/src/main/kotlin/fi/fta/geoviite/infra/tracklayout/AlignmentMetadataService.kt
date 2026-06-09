package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutContext
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
) {

    @Transactional(readOnly = true)
    fun getLocationTrackMetadataSections(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection<LocationTrackM>> {
        return locationTrackDao.get(layoutContext, locationTrackId)?.let { locationTrack ->
            geocodingService.getGeocodingContext(layoutContext, locationTrack.trackNumberId)?.let { geocodingContext ->
                val version = locationTrack.getVersionOrThrow()
                val oid = locationTrackDao.fetchExternalId(layoutContext.branch, locationTrackId)?.oid
                val sections = alignmentDao.fetchLocationTrackSegmentMetadata(version, oid, boundingBox)
                val geometry = alignmentDao.fetch(version)
                toPlanSections(sections, geometry, geocodingContext)
            }
        } ?: listOf()
    }

    @Transactional(readOnly = true)
    fun getTrackNumberMetadataSections(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection<ReferenceLineM>> {
        return trackNumberDao.get(layoutContext, trackNumberId)?.let { trackNumber ->
            geocodingService.getGeocodingContext(layoutContext, trackNumberId)?.let { geocodingContext ->
                val version = trackNumber.getVersionOrThrow()
                val oid = trackNumberDao.fetchExternalId(layoutContext.branch, trackNumberId)?.oid
                val sections = alignmentDao.fetchTrackNumberSegmentMetadata(version, oid, boundingBox)
                toPlanSections(sections, alignmentDao.fetch(version), geocodingContext)
            }
        } ?: listOf()
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
