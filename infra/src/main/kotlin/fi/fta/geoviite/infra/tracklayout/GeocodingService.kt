package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GeocodingService(
    private val addressPointService: AddressPointService,
    private val geocodingDao: GeocodingDao,
    private val locationTrackDao: LocationTrackDao,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getTrackAddress(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        location: IPoint,
        publishType: PublishType,
    ): Pair<TrackMeter, IntersectType>? {
        logger.serviceCall(
            "getTrackAddress",
            "trackNumberId" to trackNumberId, "location" to location, "publishType" to publishType
        )
        return getGeocodingContext(publishType, trackNumberId)?.getAddress(location)
    }

    fun getAddressPoints(locationTrackId: DomainId<LocationTrack>, publishType: PublishType): AlignmentAddresses? {
        check(locationTrackId is IntId) { "Id must be IntId when calculating address points" }
        return locationTrackDao.fetchVersion(locationTrackId, publishType)
            ?.let(locationTrackDao::fetch)
            ?.let { locationTrack ->
                addressPointService.getAddressPointCacheKey(publishType, locationTrack)
                    ?.let { context -> addressPointService.getAddressPointsInternal(context) }
            }
    }

    fun getAddressPoints(locationTrack: LocationTrack, publishType: PublishType) =
        addressPointService.getAddressPointCacheKey(publishType, locationTrack)
            ?.let { context -> addressPointService.getAddressPointsInternal(context) }

    fun getTrackLocation(
        alignmentId: IntId<LocationTrack>,
        address: TrackMeter,
        publishType: PublishType,
    ): AddressPoint? {
        logger.serviceCall("getTrackLocation", "alignmentId" to alignmentId, "address" to address)
        return addressPointService.getTrackGeocodingData(publishType, alignmentId)?.let { data ->
            getTrackLocation(data.locationTrack, data.alignment, data.context, address, publishType)
        }
    }

    fun getTrackLocation(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
        context: GeocodingContext,
        address: TrackMeter,
        publishType: PublishType,
    ): AddressPoint? {
        logger.serviceCall(
            "getTrackLocation",
            "locationTrack" to locationTrack.id, "alignment" to alignment.id, "address" to address
        )
        val startAddress = alignment.start?.let(context::getAddress)?.first
        val endAddress = alignment.end?.let(context::getAddress)?.first
        return if (startAddress == null || endAddress == null || address !in startAddress..endAddress) {
            null
        } else context.getProjectionLine(address)?.let { projectionLine ->
            getProjectedAddressPoint(projectionLine, alignment)
        }
    }

    fun getGeocodingContext(publishType: PublishType, trackNumberId: DomainId<TrackLayoutTrackNumber>?) =
        if (trackNumberId is IntId) {
            geocodingDao.getGeocodingContextChangeTime(publishType, trackNumberId)?.let { changeTime ->
                geocodingDao.getGeocodingContext(
                    publishType = publishType,
                    trackNumberId = trackNumberId,
                    changeTime = changeTime,
                )
            }
        } else {
            logger.warn("Cannot get geocoding context for track number: $trackNumberId")
            null
        }
}
