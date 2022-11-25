package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GeocodingService(
    private val addressPointsCache: AddressPointsCache,
    private val geocodingDao: GeocodingDao,
    private val locationTrackService: LocationTrackService,
    private val referenceLineService: ReferenceLineService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getAddressPoints(locationTrackId: DomainId<LocationTrack>, publishType: PublishType): AlignmentAddresses? {
        check(locationTrackId is IntId) { "Location track must be stored in DB before calculating address points" }
        logger.serviceCall(
            "getAddressPoints",
            "locationTrackId" to locationTrackId, "publishType" to publishType
        )
        return addressPointsCache.getAddressPointCacheKey(publishType, locationTrackId)
            ?.let(addressPointsCache::getAddressPoints)
    }

    fun getAddress(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        location: IPoint,
    ): Pair<TrackMeter, IntersectType>? {
        logger.serviceCall(
            "getAddress",
            "trackNumberId" to trackNumberId, "location" to location, "publishType" to publishType
        )
        return getGeocodingContext(publishType, trackNumberId)?.getAddress(location)
    }

    fun getAddressIfWithin(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        location: IPoint,
    ): TrackMeter? {
        logger.serviceCall(
            "getAddressIfWithin",
            "trackNumberId" to trackNumberId, "location" to location, "publishType" to publishType
        )
        return getGeocodingContext(publishType, trackNumberId)?.getAddress(location)
            ?.let { (address, intersect) -> if (intersect != WITHIN) null else address }
    }

    fun getLocationTrackStartAndEnd(
        publishType: PublishType,
        locationTrackId: IntId<LocationTrack>,
    ): AlignmentStartAndEnd? {
        logger.serviceCall(
            "getLocationTrackStartAndEnd",
            "publishType" to publishType, "locationTrackId" to locationTrackId)
        return locationTrackService.getWithAlignment(publishType, locationTrackId)?.let { (track, alignment) ->
            val geocodingContext = getGeocodingContext(publishType, track.trackNumberId)
            geocodingContext?.getStartAndEnd(alignment)
        }
    }

    fun getReferenceLineStartAndEnd(
        publishType: PublishType,
        referenceLineId: IntId<ReferenceLine>,
    ): AlignmentStartAndEnd? {
        logger.serviceCall("getReferenceLineStartAndEnd",
            "publishType" to publishType, "referenceLineId" to referenceLineId)
        return referenceLineService.getWithAlignment(publishType, referenceLineId)?.let { (line, alignment) ->
            val geocodingContext = getGeocodingContext(publishType, line.trackNumberId)
            geocodingContext?.getStartAndEnd(alignment)
        }
    }

    fun getTrackLocation(
        locationTrackId: IntId<LocationTrack>,
        address: TrackMeter,
        publishType: PublishType,
    ): AddressPoint? {
        logger.serviceCall("getTrackLocation", "locationTrackId" to locationTrackId, "address" to address)
        return locationTrackService.getWithAlignment(publishType, locationTrackId)?.let { (track, alignment) ->
            getGeocodingContext(publishType, track.trackNumberId)?.getTrackLocation(alignment, address)
        }
    }

    fun getGeocodingContext(publishType: PublishType, trackNumberId: DomainId<TrackLayoutTrackNumber>?) =
        if (trackNumberId is IntId) {
            geocodingDao.getGeocodingContextCacheKey(publishType, trackNumberId)
                ?.let(geocodingDao::getGeocodingContext)
        } else {
            logger.warn("Cannot get geocoding context for track number: $trackNumberId")
            null
        }
}
