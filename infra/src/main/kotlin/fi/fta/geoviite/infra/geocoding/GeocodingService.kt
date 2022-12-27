package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.linking.PublicationVersions
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class GeocodingService(
    private val addressPointsCache: AddressPointsCache,
    private val geocodingDao: GeocodingDao,
    private val locationTrackService: LocationTrackService,
    private val referenceLineService: ReferenceLineService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getAddressPoints(locationTrackId: IntId<LocationTrack>, publicationState: PublishType): AlignmentAddresses? {
        logger.serviceCall(
            "getAddressPoints",
            "locationTrackId" to locationTrackId, "publicationState" to publicationState
        )
        return addressPointsCache.getAddressPointCacheKey(publicationState, locationTrackId)
            ?.let(addressPointsCache::getAddressPoints)
    }

    fun getAddressPoints(
        contextKey: GeocodingContextCacheKey,
        alignmentVersion: RowVersion<LayoutAlignment>,
    ): AlignmentAddresses? {
        logger.serviceCall(
            "getAddressPointsForPublication",
            "alignmentVersion" to alignmentVersion, "contextKey" to contextKey
        )
        return addressPointsCache.getAddressPoints(AddressPointCacheKey(alignmentVersion, contextKey))
    }

    fun getReferenceLineAddressPoints(contextKey: GeocodingContextCacheKey): AlignmentAddresses? {
        logger.serviceCall("getReferenceLineAddressPoints", "contextKey" to contextKey)
        return geocodingDao.getGeocodingContext(contextKey)?.referenceLineAddresses
    }

    fun getAddress(
        publicationState: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        location: IPoint,
    ): Pair<TrackMeter, IntersectType>? {
        logger.serviceCall(
            "getAddress",
            "trackNumberId" to trackNumberId, "location" to location, "publicationState" to publicationState
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
            "trackNumberId" to trackNumberId, "location" to location, "publicationState" to publicationState
        )
        return getGeocodingContext(publicationState, trackNumberId)?.getAddress(location)
            ?.let { (address, intersect) -> if (intersect != WITHIN) null else address }
    }

    fun getLocationTrackStartAndEnd(
        publicationState: PublishType,
        locationTrackId: IntId<LocationTrack>,
    ): AlignmentStartAndEnd? {
        logger.serviceCall(
            "getLocationTrackStartAndEnd",
            "publicationState" to publicationState, "locationTrackId" to locationTrackId)
        return locationTrackService.getWithAlignment(publicationState, locationTrackId)?.let { (track, alignment) ->
            val geocodingContext = getGeocodingContext(publicationState, track.trackNumberId)
            geocodingContext?.getStartAndEnd(alignment)
        }
    }

    fun getReferenceLineStartAndEnd(
        publicationState: PublishType,
        referenceLineId: IntId<ReferenceLine>,
    ): AlignmentStartAndEnd? {
        logger.serviceCall("getReferenceLineStartAndEnd",
            "publicationState" to publicationState, "referenceLineId" to referenceLineId)
        return referenceLineService.getWithAlignment(publicationState, referenceLineId)?.let { (line, alignment) ->
            val geocodingContext = getGeocodingContext(publicationState, line.trackNumberId)
            geocodingContext?.getStartAndEnd(alignment)
        }
    }

    fun getTrackLocation(
        locationTrackId: IntId<LocationTrack>,
        address: TrackMeter,
        publicationState: PublishType,
    ): AddressPoint? {
        logger.serviceCall("getTrackLocation",
            "locationTrackId" to locationTrackId, "address" to address, "publicationState" to publicationState)
        return locationTrackService.getWithAlignment(publicationState, locationTrackId)?.let { (track, alignment) ->
            getGeocodingContext(publicationState, track.trackNumberId)?.getTrackLocation(alignment, address)
        }
    }

    fun getGeocodingContext(
        publicationState: PublishType,
        trackNumberId: DomainId<TrackLayoutTrackNumber>?,
    ) = if (trackNumberId is IntId) getGeocodingContext(publicationState, trackNumberId) else null

    fun getGeocodingContext(
        publicationState: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): GeocodingContext? =
        geocodingDao.getGeocodingContextCacheKey(publicationState, trackNumberId)?.let(::getGeocodingContext)

    fun getGeocodingContextAtMoment(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ): GeocodingContext? =
        geocodingDao.getGeocodingContextCacheKey(trackNumberId, moment)?.let(::getGeocodingContext)

    fun getGeocodingContext(cacheKey: GeocodingContextCacheKey) = geocodingDao.getGeocodingContext(cacheKey)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationState: PublishType,
    ) = geocodingDao.getGeocodingContextCacheKey(publicationState, trackNumberId)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationVersions: PublicationVersions,
    ) = geocodingDao.getGeocodingContextCacheKey(trackNumberId, publicationVersions)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ) = geocodingDao.getGeocodingContextCacheKey(trackNumberId, moment)
}
