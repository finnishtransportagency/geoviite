package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.AlignmentEndPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Schema(name = "Vastaus: Sijaintiraidegeometria")
data class ExtLocationTrackGeometryResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty(LOCATION_TRACK_OID_PARAM) val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty("osoitevalit") val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Vastaus: Muutettu sijaintiraidegeometria")
data class ExtLocationTrackModifiedGeometryResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty(LOCATION_TRACK_OID_PARAM) val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty("osoitevalit") val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Osoitepiste")
data class ExtAddressPointV1(val x: Double, val y: Double, @JsonProperty("rataosoite") val trackAddress: String?) {

    constructor(x: Double, y: Double, address: TrackMeter?) : this(x, y, address?.formatFixedDecimals(3))

    constructor(point: AlignmentEndPoint) : this(point.point.x, point.point.y, point.address)
}

@Schema(name = "Osoiteväli")
data class ExtCenterLineTrackIntervalV1(
    @JsonProperty("alku") val startAddress: String,
    @JsonProperty("loppu") val endAddress: String,
    @JsonProperty("pisteet") val addressPoints: List<ExtAddressPointV1>,
)

data class ExtTrackKilometerIntervalV1(val start: KmNumber?, val inclusiveEnd: KmNumber?) {
    fun containsKmEndInclusive(kmNumber: KmNumber): Boolean {
        val startsAfterStartKmFilter = start == null || kmNumber >= start
        val endsBeforeEndKmFilter = inclusiveEnd == null || kmNumber <= inclusiveEnd

        return startsAfterStartKmFilter && endsBeforeEndKmFilter
    }
}

@GeoviiteService
class ExtLocationTrackGeometryServiceV1
@Autowired
constructor(
    private val geocodingService: GeocodingService,
    private val geocodingDao: GeocodingDao,
    private val locationTrackService: LocationTrackService,
    private val publicationDao: PublicationDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createGeometryResponse(
        oid: Oid<LocationTrack>,
        trackNetworkVersion: Uuid<Publication>?,
        resolution: Resolution,
        coordinateSystem: Srid,
        trackIntervalFilter: ExtTrackKilometerIntervalV1,
    ): ExtLocationTrackGeometryResponseV1 {
        val layoutContext = MainLayoutContext.official

        val publication =
            trackNetworkVersion?.let { uuid ->
                publicationDao.fetchPublicationByUuid(uuid)
                    ?: throw ExtTrackNetworkVersionNotFound("fetch failed, uuid=$uuid")
            } ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        val locationTrack =
            locationTrackService.getAugLocationTrackByOidAtMoment(oid, layoutContext, publication.publicationTime)
                ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

        val geocodingContextCacheKey =
            geocodingDao.getLayoutGeocodingContextCacheKey(
                layoutContext.branch,
                locationTrack.trackNumberId,
                publication.publicationTime,
            ) ?: throw ExtGeocodingFailedV1("could not get geocoding context cache key")

        return ExtLocationTrackGeometryResponseV1(
            trackNetworkVersion = publication.uuid,
            locationTrackOid = oid,
            trackIntervals =
                getExtLocationTrackGeometry(
                    locationTrack.versionOrThrow,
                    geocodingContextCacheKey,
                    trackIntervalFilter,
                    resolution,
                    coordinateSystem,
                ),
        )
    }

    private fun getExtLocationTrackGeometry(
        locationTrackVersion: LayoutRowVersion<LocationTrack>,
        geocodingContextCacheKey: GeocodingContextCacheKey,
        trackIntervalFilter: ExtTrackKilometerIntervalV1,
        resolution: Resolution,
        coordinateSystem: Srid,
    ): List<ExtCenterLineTrackIntervalV1> {
        val alignmentAddresses =
            geocodingService.getAddressPoints(geocodingContextCacheKey, locationTrackVersion, resolution)
                ?: throw ExtGeocodingFailedV1("could not get address points")

        val extAddressPoints =
            alignmentAddresses.allPoints.mapNotNull { point ->
                point
                    .takeIf { p -> trackIntervalFilter.containsKmEndInclusive(p.address.kmNumber) }
                    ?.let { toExtAddressPoint(point, coordinateSystem) }
            }

        return listOf(
            ExtCenterLineTrackIntervalV1(
                startAddress = alignmentAddresses.startPoint.address.toString(),
                endAddress = alignmentAddresses.endPoint.address.toString(),
                addressPoints = extAddressPoints,
            )
        )
    }
}
