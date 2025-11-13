package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.AddressFilter
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
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
    private val alignmentDao: LayoutAlignmentDao,
    private val publicationService: PublicationService,
) {
    fun getExtLocationTrackGeometry(
        oid: Oid<LocationTrack>,
        trackLayoutVersion: Uuid<Publication>? = null,
        extResolution: ExtResolutionV1? = null,
        coordinateSystem: Srid? = null,
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1? = null,
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1? = null,
    ): ExtLocationTrackGeometryResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        val resolution = extResolution?.toResolution() ?: Resolution.ONE_METER
        val coordinateSystem = coordinateSystem ?: LAYOUT_SRID
        val addressFilter = createAddressFilter(addressFilterStart, addressFilterEnd)
        return createGeometryResponse(oid, idLookup(oid), publication, resolution, coordinateSystem, addressFilter)
    }

    private fun idLookup(oid: Oid<LocationTrack>): IntId<LocationTrack> =
        locationTrackDao.lookupByExternalId(oid)?.id
            ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

    private fun createGeometryResponse(
        oid: Oid<LocationTrack>,
        id: IntId<LocationTrack>,
        publication: Publication,
        resolution: Resolution,
        coordinateSystem: Srid,
        addressFilter: AddressFilter,
    ): ExtLocationTrackGeometryResponseV1? {
        return locationTrackDao
            .fetchOfficialVersionAtMoment(publication.layoutBranch.branch, id, publication.publicationTime)
            ?.let(locationTrackDao::fetch)
            // Deleted tracks have no geometry in API since there's no guarantee of geocodable addressing
            ?.takeIf { it.exists }
            ?.let { locationTrack ->
                val filteredAddressPoints =
                    if (addressFilter.start == null && addressFilter.end == null) {
                        // Prefer using cache when address filter is unassigned
                        val geocodingContextCacheKey =
                            geocodingDao.getLayoutGeocodingContextCacheKey(
                                publication.layoutBranch.branch,
                                locationTrack.trackNumberId,
                                publication.publicationTime,
                            ) ?: throw ExtGeocodingFailedV1("could not get geocoding context cache key")

                        geocodingService.getAddressPoints(
                            geocodingContextCacheKey,
                            locationTrack.getVersionOrThrow(),
                            resolution,
                        )
                    } else {
                        geocodingService
                            .getGeocodingContextAtMoment(
                                publication.layoutBranch.branch,
                                locationTrack.trackNumberId,
                                publication.publicationTime,
                            )
                            ?.getAddressPoints(
                                alignmentDao.fetch(requireNotNull(locationTrack.version)),
                                resolution,
                                addressFilter,
                            )
                    }

                ExtLocationTrackGeometryResponseV1(
                    trackLayoutVersion = publication.uuid,
                    locationTrackOid = oid,
                    coordinateSystem = coordinateSystem,
                    trackInterval =
                        // Address points are null for example in case when the user provided
                        // address filter is outside the track boundaries.
                        filteredAddressPoints?.let { addressPoints ->
                            ExtCenterLineTrackIntervalV1(
                                startAddress = addressPoints.startPoint.address.toString(),
                                endAddress = addressPoints.endPoint.address.toString(),
                                addressPoints =
                                    filteredAddressPoints.allPoints.map { point ->
                                        toExtAddressPoint(point, coordinateSystem)
                                    },
                            )
                        },
                )
            }
    }
}
