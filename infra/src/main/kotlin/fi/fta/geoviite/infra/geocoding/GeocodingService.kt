package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class GeocodingService(
    private val addressPointsCache: AddressPointsCache,
    private val geocodingDao: GeocodingDao,
    private val geocodingCacheService: GeocodingCacheService,
    private val trackNumberDao: LayoutTrackNumberDao,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getAddressPoints(locationTrackId: IntId<LocationTrack>, publicationState: PublishType): AlignmentAddresses? {
        logger.serviceCall(
            "getAddressPoints", "locationTrackId" to locationTrackId, "publicationState" to publicationState
        )
        return addressPointsCache.getAddressPointCacheKey(publicationState, locationTrackId)
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
        return getGeocodingContext(publicationState, trackNumberId)?.getAddress(location)
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
        return getGeocodingContext(
            publicationState, referenceLine.trackNumberId
        )?.getStartAndEnd(alignment)
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

    fun getGeocodingContext(
        publicationState: PublishType,
        trackNumberId: DomainId<TrackLayoutTrackNumber>?,
    ) = if (trackNumberId is IntId) getGeocodingContext(publicationState, trackNumberId) else null

    fun getGeocodingContext(geocodingContextCacheKey: GeocodingContextCacheKey) =
        geocodingCacheService.getGeocodingContext(geocodingContextCacheKey)

    fun getGeocodingContext(
        publicationState: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): GeocodingContext? = geocodingDao.getLayoutGeocodingContextCacheKey(publicationState, trackNumberId)
        ?.let(geocodingCacheService::getGeocodingContext)

    fun getGeocodingContextAtMoment(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ): GeocodingContext? = geocodingDao.getLayoutGeocodingContextCacheKey(trackNumberId, moment)
        ?.let(geocodingCacheService::getGeocodingContext)

    fun getGeocodingContext(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        plan: RowVersion<GeometryPlan>,
    ): GeocodingContext? = trackNumberDao.fetchVersion(trackNumberId, PublishType.OFFICIAL)?.let { trackNumberVersion ->
        getGeocodingContext(GeometryGeocodingContextCacheKey(trackNumberVersion, plan))
    }

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationState: PublishType,
    ) = geocodingDao.getLayoutGeocodingContextCacheKey(publicationState, trackNumberId)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ) = geocodingDao.getLayoutGeocodingContextCacheKey(trackNumberId, versions)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ) = geocodingDao.getLayoutGeocodingContextCacheKey(trackNumberId, moment)


}
