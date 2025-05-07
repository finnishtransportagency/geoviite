package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentEndPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
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
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty("osoitevalit") val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Vastaus: Muutettu sijaintiraidegeometria")
data class ExtLocationTrackModifiedGeometryResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty(LOCATION_TRACK_OID_PARAM) val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty("osoitevalit") val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Osoitepiste")
data class ExtAddressPointV1(val x: Double, val y: Double, @JsonProperty("rataosoite") val trackAddress: String?) {
    companion object {
        fun of(addressPoint: AddressPoint): ExtAddressPointV1 {
            return ExtAddressPointV1(
                addressPoint.point.x,
                addressPoint.point.y,
                addressPoint.address.formatFixedDecimals(3),
            )
        }

        fun of(alignmentEndPoint: AlignmentEndPoint): ExtAddressPointV1 {
            return ExtAddressPointV1(
                alignmentEndPoint.point.x,
                alignmentEndPoint.point.y,
                alignmentEndPoint.address?.formatFixedDecimals(3),
            )
        }
    }
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
            locationTrackService.getLocationTrackByOidAtMoment(oid, layoutContext, publication.publicationTime)
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
            coordinateSystem = coordinateSystem,
            trackIntervals =
                getExtLocationTrackGeometry(
                    locationTrack.getAlignmentVersionOrThrow(),
                    geocodingContextCacheKey,
                    trackIntervalFilter,
                    resolution,
                    coordinateSystem,
                ),
        )
    }

    private fun getExtLocationTrackGeometry(
        locationTrackAlignmentVersion: RowVersion<LayoutAlignment>,
        geocodingContextCacheKey: GeocodingContextCacheKey,
        trackIntervalFilter: ExtTrackKilometerIntervalV1,
        resolution: Resolution,
        coordinateSystem: Srid,
    ): List<ExtCenterLineTrackIntervalV1> {
        val alignmentAddresses =
            geocodingService.getAddressPoints(geocodingContextCacheKey, locationTrackAlignmentVersion, resolution)
                ?: throw ExtGeocodingFailedV1("could not get address points")

        val filteredPoints =
            alignmentAddresses.allPoints.filter { trackIntervalFilter.containsKmEndInclusive(it.address.kmNumber) }

        // TODO Check that there is a start & end point defined? (otherwise the first/last calls will throw). This
        // should probably always be the case for a track interval?

        return listOf(
            ExtCenterLineTrackIntervalV1(
                startAddress = filteredPoints.first().address.formatFixedDecimals(3),
                endAddress = filteredPoints.last().address.formatFixedDecimals(3),
                addressPoints =
                    filteredPoints
                        .map { addressPoint -> layoutAddressPointToCoordinateSystem(addressPoint, coordinateSystem) }
                        .map(ExtAddressPointV1::of),
            )
        )
    }
}
