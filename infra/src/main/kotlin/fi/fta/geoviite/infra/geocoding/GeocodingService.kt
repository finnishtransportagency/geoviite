package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class GeocodingService(
    private val addressPointsCache: AddressPointsCache,
    private val geocodingDao: GeocodingDao,
    private val geocodingCacheService: GeocodingCacheService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getAddressPoints(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
    ): AlignmentAddresses? {
        logger.serviceCall(
            "getAddressPoints",
            "layoutContext" to layoutContext,
            "locationTrackId" to locationTrackId,
        )
        return addressPointsCache
            .getAddressPointCacheKey(layoutContext, locationTrackId)
            ?.let(addressPointsCache::getAddressPoints)
    }

    fun getAddressPoints(
        contextKey: GeocodingContextCacheKey,
        alignmentVersion: RowVersion<LayoutAlignment>,
    ): AlignmentAddresses? {
        logger.serviceCall(
            "getAddressPoints",
            "alignmentVersion" to alignmentVersion,
            "contextKey" to contextKey,
        )
        return addressPointsCache.getAddressPoints(AddressPointCacheKey(alignmentVersion, contextKey))
    }

    fun getAddress(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        location: IPoint,
    ): Pair<TrackMeter, IntersectType>? {
        logger.serviceCall(
            "getAddress",
            "context" to layoutContext,
            "trackNumberId" to trackNumberId,
            "location" to location,
        )
        return getGeocodingContext(layoutContext, trackNumberId)?.getAddress(location)
    }

    fun getAddressIfWithin(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        location: IPoint,
    ): TrackMeter? {
        logger.serviceCall(
            "getAddressIfWithin",
            "context" to layoutContext,
            "trackNumberId" to trackNumberId,
            "location" to location,
        )
        return getGeocodingContext(layoutContext, trackNumberId)
            ?.getAddress(location)
            ?.let { (address, intersect) -> if (intersect != WITHIN) null else address }
    }

    fun getLocationTrackStartAndEnd(
        layoutContext: LayoutContext,
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
    ): AlignmentStartAndEnd? {
        logger.serviceCall(
            "getLocationTrackStartAndEnd",
            "context" to layoutContext,
            "locationTrack" to locationTrack,
            "alignment" to alignment
        )
        return getGeocodingContext(layoutContext, locationTrack.trackNumberId)?.getStartAndEnd(alignment)
    }

    fun getReferenceLineStartAndEnd(
        layoutContext: LayoutContext,
        referenceLine: ReferenceLine,
        alignment: LayoutAlignment,
    ): AlignmentStartAndEnd? {
        logger.serviceCall(
            "getReferenceLineStartAndEnd",
            "context" to layoutContext,
            "referenceLine" to referenceLine,
            "alignment" to alignment
        )
        return getGeocodingContext(layoutContext, referenceLine.trackNumberId)?.getStartAndEnd(alignment)
    }

    fun getTrackLocation(
        layoutContext: LayoutContext,
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
        address: TrackMeter,
    ): AddressPoint? {
        logger.serviceCall(
            "getTrackLocation",
            "layoutContext" to layoutContext,
            "locationTrack" to locationTrack,
            "address" to address,
        )
        return getGeocodingContext(layoutContext, locationTrack.trackNumberId)?.getTrackLocation(alignment, address)
    }

    @Transactional(readOnly = true)
    fun getGeocodingContexts(layoutContext: LayoutContext): Map<IntId<TrackLayoutTrackNumber>, GeocodingContext?> =
        geocodingDao
            .listLayoutGeocodingContextCacheKeys(layoutContext)
            .associate { key -> key.trackNumberVersion.id to geocodingCacheService.getGeocodingContext(key) }

    fun getGeocodingContext(
        layoutContext: LayoutContext,
        trackNumberId: DomainId<TrackLayoutTrackNumber>?,
    ): GeocodingContext? = if (trackNumberId is IntId) getGeocodingContext(layoutContext, trackNumberId) else null

    fun getGeocodingContext(geocodingContextCacheKey: GeocodingContextCacheKey) =
        geocodingCacheService.getGeocodingContext(geocodingContextCacheKey)

    fun getGeocodingContext(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): GeocodingContext? = getGeocodingContextCreateResult(layoutContext, trackNumberId)?.geocodingContext

    fun getGeocodingContextCreateResult(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): GeocodingContextCreateResult? =
        geocodingCacheService.getGeocodingContextCreateResult(layoutContext, trackNumberId)

    fun getGeocodingContextAtMoment(trackNumberId: IntId<TrackLayoutTrackNumber>, moment: Instant): GeocodingContext? =
        geocodingCacheService.getGeocodingContextAtMoment(trackNumberId, moment)

    fun getGeocodingContext(
        trackNumber: TrackNumber,
        plan: RowVersion<GeometryPlan>,
    ): GeocodingContext? = geocodingCacheService.getGeocodingContext(trackNumber, plan)

    fun getGeocodingContextCacheKey(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): LayoutGeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(layoutContext, trackNumberId)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ): GeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(trackNumberId, versions)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ): GeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, trackNumberId, moment)
    // TODO: GVT-2616
}
