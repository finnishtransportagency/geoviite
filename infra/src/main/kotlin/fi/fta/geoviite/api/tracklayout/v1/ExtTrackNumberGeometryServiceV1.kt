package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.AddressFilter
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTrackNumberGeometryServiceV1
@Autowired
constructor(private val geocodingService: GeocodingService, private val layoutTrackNumberDao: LayoutTrackNumberDao) {
    fun createGeometryResponse(
        oid: Oid<LayoutTrackNumber>,
        publication: Publication,
        resolution: Resolution,
        coordinateSystem: Srid,
        addressFilter: AddressFilter,
    ): ExtTrackNumberGeometryResponseV1? {
        val trackNumberId =
            layoutTrackNumberDao.lookupByExternalId(oid.toString())?.id
                ?: throw ExtOidNotFoundExceptionV1("track number lookup failed for oid=$oid")

        return layoutTrackNumberDao
            .fetchOfficialVersionAtMoment(publication.layoutBranch.branch, trackNumberId, publication.publicationTime)
            ?.let(layoutTrackNumberDao::fetch)
            ?.let { trackNumber ->
                val filteredAddressPoints =
                    geocodingService
                        .getGeocodingContextAtMoment(
                            publication.layoutBranch.branch,
                            trackNumber.id as IntId,
                            publication.publicationTime,
                        )
                        ?.getReferenceLineAddressesWithResolution(resolution, addressFilter)

                ExtTrackNumberGeometryResponseV1(
                    trackLayoutVersion = publication.uuid,
                    trackNumberOid = oid,
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
