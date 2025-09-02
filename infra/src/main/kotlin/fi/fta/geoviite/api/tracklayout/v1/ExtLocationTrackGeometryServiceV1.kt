package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtLocationTrackGeometryServiceV1
@Autowired
constructor(
    private val geocodingService: GeocodingService,
    private val geocodingDao: GeocodingDao,
    private val locationTrackDao: LocationTrackDao,
) {
    fun createGeometryResponse(
        oid: Oid<LocationTrack>,
        publication: Publication,
        resolution: Resolution,
        coordinateSystem: Srid,
        trackIntervalFilter: ExtTrackKilometerIntervalV1,
    ): ExtLocationTrackGeometryResponseV1? {
        val locationTrackId =
            locationTrackDao.lookupByExternalId(oid.toString())?.id
                ?: throw ExtOidNotFoundExceptionV1("location track lookup failed for oid=$oid")

        return locationTrackDao
            .fetchOfficialVersionAtMoment(publication.layoutBranch.branch, locationTrackId, publication.publicationTime)
            ?.let(locationTrackDao::fetch)
            ?.let { locationTrack ->
                val geocodingContextCacheKey =
                    geocodingDao.getLayoutGeocodingContextCacheKey(
                        publication.layoutBranch.branch,
                        locationTrack.trackNumberId,
                        publication.publicationTime,
                    ) ?: throw ExtGeocodingFailedV1("could not get geocoding context cache key")

                val alignmentAddresses =
                    geocodingService.getAddressPoints(
                        geocodingContextCacheKey,
                        locationTrack.getVersionOrThrow(),
                        resolution,
                    ) ?: throw ExtGeocodingFailedV1("could not get address points")

                ExtLocationTrackGeometryResponseV1(
                    trackLayoutVersion = publication.uuid,
                    locationTrackOid = oid,
                    trackIntervals =
                        filteredCenterLineTrackIntervals(alignmentAddresses, trackIntervalFilter, coordinateSystem),
                )
            }
    }
}
