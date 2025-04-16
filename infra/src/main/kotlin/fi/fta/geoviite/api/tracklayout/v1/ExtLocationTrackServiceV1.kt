package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.util.FreeText
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Schema(name = "Vastaus: Sijaintiraide")
data class ExtLocationTrackResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty("sijaintiraide") val locationTrack: ExtLocationTrackV1,
)

@Schema(name = "Vastaus: Muutettu sijaintiraide")
data class ExtModifiedLocationTrackResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty("sijaintiraide") val locationTrack: ExtLocationTrackV1,
)

@Schema(name = "Sijaintiraide")
data class ExtLocationTrackV1(
    @JsonProperty("ratanumero") val trackNumberName: TrackNumber,
    @JsonProperty("ratanumero_oid") val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty("oid") val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty("sijaintiraidetunnus") val locationTrackName: AlignmentName,
    @JsonProperty("tyyppi") val locationTrackType: ExtLocationTrackTypeV1,
    @JsonProperty("tila") val locationTrackState: ExtLocationTrackStateV1,
    @JsonProperty("kuvaus") val locationTrackDescription: FreeText,
    @JsonProperty("omistaja") val locationTrackOwner: MetaDataName,
    @JsonProperty("alkusijainti") val startLocation: ExtCenterLineGeometryPointV1,
    @JsonProperty("loppusijainti") val endLocation: ExtCenterLineGeometryPointV1,
    @JsonProperty("koordinaatisto") val coordinateSystem: Srid,
)

@GeoviiteService
class ExtLocationTrackServiceV1
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationDao: PublicationDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createLocationTrackResponse(
        oid: Oid<LocationTrack>,
        trackNetworkVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtLocationTrackResponseV1 {
        val layoutContext = MainLayoutContext.official

        val publication =
            trackNetworkVersion?.let { uuid -> publicationDao.fetchPublicationByUuid(uuid).let(::requireNotNull) }
                ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        val locationTrack =
            getLocationTrackByOidAtMoment(oid, layoutContext, publication.publicationTime)
                ?: throw ExtOidNotFoundExceptionV1(
                    "location track lookup failed for " +
                        "oid=$oid, layoutContext=$layoutContext, publicationId=${publication.id}"
                )

        return ExtLocationTrackResponseV1(
            trackNetworkVersion = publication.uuid,
            locationTrack =
                getExtLocationTrack(oid, locationTrack, layoutContext, publication.publicationTime, coordinateSystem),
        )
    }

    fun createLocationTrackModificationResponse(
        oid: Oid<LocationTrack>,
        modificationsFromVersion: Uuid<Publication>,
        trackNetworkVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtModifiedLocationTrackResponseV1? {
        val layoutContext = MainLayoutContext.official

        val earlierPublication = publicationDao.fetchPublicationByUuid(modificationsFromVersion).let(::requireNotNull)
        val laterPublication =
            trackNetworkVersion?.let { uuid -> publicationDao.fetchPublicationByUuid(uuid).let(::requireNotNull) }
                ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        return if (earlierPublication == laterPublication) {
            logger.info(
                "there cannot be any differences if the requested publication ids are the same " +
                    "publication=${earlierPublication.id}"
            )
            null
        } else {
            val locationTrackId =
                locationTrackDao.lookupByExternalId(oid)?.id
                    ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

            val earlierLocationTrackVersion =
                locationTrackDao.fetchOfficialVersionAtMomentOrThrow(
                    layoutContext.branch,
                    locationTrackId,
                    earlierPublication.publicationTime,
                )

            val laterLocationTrackVersion =
                locationTrackDao.fetchOfficialVersionAtMomentOrThrow(
                    layoutContext.branch,
                    locationTrackId,
                    laterPublication.publicationTime,
                )

            if (earlierLocationTrackVersion == laterLocationTrackVersion) {
                logger.info(
                    "location track version was the same for " +
                        "earlierPublication=${earlierPublication.id}, laterPublication=${laterPublication.id}"
                )
                return null
            } else {
                return ExtModifiedLocationTrackResponseV1(
                    modificationsFromVersion = modificationsFromVersion,
                    trackNetworkVersion = laterPublication.uuid,
                    locationTrack =
                        getExtLocationTrack(
                            oid,
                            locationTrackDao.fetch(laterLocationTrackVersion),
                            MainLayoutContext.official,
                            laterPublication.publicationTime,
                            coordinateSystem,
                        ),
                )
            }
        }
    }

    fun getExtLocationTrack(
        oid: Oid<LocationTrack>,
        locationTrack: LocationTrack,
        layoutContext: LayoutContext,
        moment: Instant,
        coordinateSystem: Srid,
    ): ExtLocationTrackV1 {
        val alignmentAddresses =
            geocodingService.getAddressPoints(layoutContext, locationTrack.id as IntId)
                ?: throw ExtGeocodingFailedV1("address points not found, locationTrackId=${locationTrack.id}")

        val trackNumberName =
            layoutTrackNumberDao
                .fetchOfficialVersionAtMoment(layoutContext.branch, locationTrack.trackNumberId, moment)
                ?.let(layoutTrackNumberDao::fetch)
                ?.number
                ?: throw ExtTrackNumberNotFoundV1(
                    "track number was not found for " +
                        "branch=${layoutContext.branch}, trackNumberId=${locationTrack.trackNumberId}, moment=$moment"
                )

        val trackNumberOid =
            layoutTrackNumberDao.fetchExternalId(layoutContext.branch, locationTrack.trackNumberId)?.oid
                ?: throw ExtOidNotFoundExceptionV1(
                    "track number oid was not found for " +
                        "branch=${layoutContext.branch}, trackNumberId=${locationTrack.trackNumberId}"
                )

        val (startLocation, endLocation) =
            when (coordinateSystem) {
                LAYOUT_SRID -> alignmentAddresses.startPoint to alignmentAddresses.endPoint
                else -> {
                    val start = layoutAddressPointToCoordinateSystem(alignmentAddresses.startPoint, coordinateSystem)
                    val end = layoutAddressPointToCoordinateSystem(alignmentAddresses.endPoint, coordinateSystem)

                    start to end
                }
            }

        val locationTrackDescription =
            locationTrackService
                .getFullDescriptions(layoutContext, listOf(locationTrack), LocalizationLanguage.FI)
                .first()

        return ExtLocationTrackV1(
            locationTrackOid = oid,
            locationTrackName = locationTrack.name,
            locationTrackType = ExtLocationTrackTypeV1(locationTrack.type),
            locationTrackState = ExtLocationTrackStateV1(locationTrack.state),
            locationTrackDescription = locationTrackDescription,
            locationTrackOwner = locationTrackService.getLocationTrackOwner(locationTrack.ownerId).name,
            coordinateSystem = coordinateSystem,
            startLocation = ExtCenterLineGeometryPointV1.of(startLocation),
            endLocation = ExtCenterLineGeometryPointV1.of(endLocation),
            trackNumberName = trackNumberName,
            trackNumberOid = trackNumberOid,
        )
    }

    fun getLocationTrackByOidAtMoment(
        oid: Oid<LocationTrack>,
        layoutContext: LayoutContext,
        moment: Instant,
    ): LocationTrack? {
        return locationTrackDao
            .lookupByExternalId(oid)
            ?.let { layoutRowId ->
                locationTrackDao.fetchOfficialVersionAtMoment(layoutContext.branch, layoutRowId.id, moment)
            }
            ?.let(locationTrackDao::fetch)
    }
}
