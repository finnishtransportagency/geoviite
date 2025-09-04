package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTrackNumberGeometryServiceV1
@Autowired
constructor(
    private val geocodingService: GeocodingService,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
) {
    fun createGeometryResponse(
        oid: Oid<LayoutTrackNumber>,
        publication: Publication,
        resolution: Resolution,
        coordinateSystem: Srid,
        trackIntervalFilter: ExtTrackKilometerIntervalFilterV1,
    ): ExtTrackNumberGeometryResponseV1? {
        val trackNumberId =
            layoutTrackNumberDao.lookupByExternalId(oid.toString())?.id
                ?: throw ExtOidNotFoundExceptionV1("track number lookup failed for oid=$oid")

        return layoutTrackNumberDao
            .fetchOfficialVersionAtMoment(publication.layoutBranch.branch, trackNumberId, publication.publicationTime)
            ?.let(layoutTrackNumberDao::fetch)
            ?.let { trackNumber ->
                val alignmentAddresses =
                    geocodingService
                        .getGeocodingContextAtMoment(
                            publication.layoutBranch.branch,
                            trackNumber.id as IntId,
                            publication.publicationTime,
                        )
                        ?.getReferenceLineAddressesWithResolution(resolution)
                        ?: throw ExtGeocodingFailedV1("could not get reference line address points")

                ExtTrackNumberGeometryResponseV1(
                    trackLayoutVersion = publication.uuid,
                    trackNumberOid = oid,
                    trackIntervals =
                        filteredCenterLineTrackIntervals(alignmentAddresses, trackIntervalFilter, coordinateSystem),
                )
            }
    }
}
