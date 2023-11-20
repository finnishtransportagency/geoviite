package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.*
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

    fun getAddressPoints(locationTrackId: IntId<LocationTrack>, publicationState: PublishType): AlignmentAddresses? {
        logger.serviceCall(
            "getAddressPoints", "locationTrackId" to locationTrackId, "publicationState" to publicationState
        )
        return addressPointsCache
            .getAddressPointCacheKey(publicationState, locationTrackId)
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
        publicationState: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        location: IPoint,
    ): Pair<TrackMeter, IntersectType>? {
        logger.serviceCall(
            "getAddress",
            "trackNumberId" to trackNumberId,
            "location" to location,
            "publicationState" to publicationState
        )
        return getGeocodingContext(publicationState, trackNumberId)?.getAddress(location)
    }

    fun getAddressIfWithin(
        publicationState: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        location: IPoint,
    ): TrackMeter? {
        logger.serviceCall(
            "getAddressIfWithin",
            "trackNumberId" to trackNumberId,
            "location" to location,
            "publicationState" to publicationState
        )
        return getGeocodingContext(publicationState, trackNumberId)
            ?.getAddress(location)
            ?.let { (address, intersect) -> if (intersect != WITHIN) null else address }
    }

    fun getLocationTrackStartAndEnd(
        publicationState: PublishType,
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
    ): AlignmentStartAndEnd? {
        logger.serviceCall(
            "getLocationTrackStartAndEnd",
            "publicationState" to publicationState,
            "locationTrack" to locationTrack,
            "alignment" to alignment
        )
        return getGeocodingContext(publicationState, locationTrack.trackNumberId)?.getStartAndEnd(alignment)
    }

    fun getReferenceLineStartAndEnd(
        publicationState: PublishType,
        referenceLine: ReferenceLine,
        alignment: LayoutAlignment,
    ): AlignmentStartAndEnd? {
        logger.serviceCall(
            "getReferenceLineStartAndEnd",
            "publicationState" to publicationState,
            "referenceLine" to referenceLine,
            "alignment" to alignment
        )
        return getGeocodingContext(publicationState, referenceLine.trackNumberId)?.getStartAndEnd(alignment)
    }

    fun getTrackLocation(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
        address: TrackMeter,
        publicationState: PublishType,
    ): AddressPoint? {
        logger.serviceCall(
            "getTrackLocation",
            "locationTrack" to locationTrack,
            "address" to address,
            "publicationState" to publicationState
        )
        return getGeocodingContext(publicationState, locationTrack.trackNumberId)?.getTrackLocation(alignment, address)
    }

    @Transactional(readOnly = true)
    fun getGeocodingContexts(publicationState: PublishType): Map<IntId<TrackLayoutTrackNumber>, GeocodingContext?> =
        geocodingDao
            .listLayoutGeocodingContextCacheKeys(publicationState)
            .associate { key -> key.trackNumberVersion.id to geocodingCacheService.getGeocodingContext(key) }

    fun getGeocodingContext(
        publicationState: PublishType,
        trackNumberId: DomainId<TrackLayoutTrackNumber>?,
    ): GeocodingContext? = if (trackNumberId is IntId) getGeocodingContext(publicationState, trackNumberId) else null

    fun getGeocodingContext(geocodingContextCacheKey: GeocodingContextCacheKey) =
        geocodingCacheService.getGeocodingContext(geocodingContextCacheKey)

    fun getGeocodingContext(
        publicationState: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): GeocodingContext? = getGeocodingContextCreateResult(publicationState, trackNumberId)?.geocodingContext

    fun getGeocodingContextCreateResult(
        publicationState: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): GeocodingContextCreateResult? =
        geocodingCacheService.getGeocodingContextCreateResult(publicationState, trackNumberId)

    fun getGeocodingContextAtMoment(trackNumberId: IntId<TrackLayoutTrackNumber>, moment: Instant): GeocodingContext? =
        geocodingCacheService.getGeocodingContextAtMoment(trackNumberId, moment)

    fun getGeocodingContext(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        plan: RowVersion<GeometryPlan>,
    ): GeocodingContext? = geocodingCacheService.getGeocodingContext(trackNumberId, plan)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationState: PublishType,
    ): LayoutGeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(publicationState, trackNumberId)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ): GeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(trackNumberId, versions)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ): GeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(trackNumberId, moment)
}
