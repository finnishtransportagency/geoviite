package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.error.TrackLayoutVersionNotFound
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Schema(name = "Vastaus: Ratanumerogeometria")
data class ExtTrackNumberGeometryResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(TRACK_NUMBER_OID_PARAM) val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty("osoitevalit") val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Vastaus: Muutettu ratanumerogeometrika")
data class ExtTrackNumberkModifiedGeometryResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty(TRACK_NUMBER_OID_PARAM) val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty("osoitevalit") val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@GeoviiteService
class ExtTrackNumberGeometryServiceV1
@Autowired
constructor(
    private val geocodingService: GeocodingService,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val publicationDao: PublicationDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createGeometryResponse(
        oid: Oid<LayoutTrackNumber>,
        trackLayoutVersion: Uuid<Publication>?,
        resolution: Resolution,
        coordinateSystem: Srid,
        trackIntervalFilter: ExtTrackKilometerIntervalV1,
    ): ExtTrackNumberGeometryResponseV1? {
        val layoutContext = MainLayoutContext.official

        val publication =
            trackLayoutVersion?.let { uuid ->
                publicationDao.fetchPublicationByUuid(uuid)
                    ?: throw TrackLayoutVersionNotFound("trackLayoutVersion=${trackLayoutVersion}")
            } ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        val trackNumberId =
            layoutTrackNumberDao.lookupByExternalId(oid.toString())?.id
                ?: throw ExtOidNotFoundExceptionV1("track number lookup failed for oid=$oid")

        return layoutTrackNumberDao
            .fetchOfficialVersionAtMoment(layoutContext.branch, trackNumberId, publication.publicationTime)
            ?.let(layoutTrackNumberDao::fetch)
            ?.let { trackNumber ->
                ExtTrackNumberGeometryResponseV1(
                    trackLayoutVersion = publication.uuid,
                    trackNumberOid = oid,
                    trackIntervals =
                        getExtTrackNumberGeometry(
                            layoutContext.branch,
                            trackNumber,
                            trackIntervalFilter,
                            resolution,
                            coordinateSystem,
                            publication.publicationTime,
                        ),
                )
            }
    }

    private fun getExtTrackNumberGeometry(
        branch: LayoutBranch,
        trackNumber: LayoutTrackNumber,
        trackIntervalFilter: ExtTrackKilometerIntervalV1,
        resolution: Resolution,
        coordinateSystem: Srid,
        moment: Instant,
    ): List<ExtCenterLineTrackIntervalV1> {
        val alignmentAddresses =
            geocodingService
                .getGeocodingContextAtMoment(branch, trackNumber.id as IntId, moment)
                ?.getReferenceLineAddressesWithResolution(resolution)
                ?: throw ExtGeocodingFailedV1("could not get reference line address points")

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
