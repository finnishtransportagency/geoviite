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
import fi.fta.geoviite.infra.geocoding.AlignmentEndPoint
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
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
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty(LOCATION_TRACK_PARAM) val locationTrack: ExtLocationTrackV1,
)

@Schema(name = "Vastaus: Muutettu sijaintiraide")
data class ExtModifiedLocationTrackResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty(LOCATION_TRACK_PARAM) val locationTrack: ExtLocationTrackV1,
)

@Schema(name = "Sijaintiraide")
data class ExtLocationTrackV1(
    @JsonProperty("sijaintiraide_oid") val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty("sijaintiraidetunnus") val locationTrackName: AlignmentName,
    @JsonProperty("ratanumero") val trackNumberName: TrackNumber,
    @JsonProperty("ratanumero_oid") val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty("tyyppi") val locationTrackType: ExtLocationTrackTypeV1,
    @JsonProperty("tila") val locationTrackState: ExtLocationTrackStateV1,
    @JsonProperty("kuvaus") val locationTrackDescription: FreeText,
    @JsonProperty("omistaja") val locationTrackOwner: MetaDataName,
    @JsonProperty("alkusijainti") val startLocation: ExtAddressPointV1?,
    @JsonProperty("loppusijainti") val endLocation: ExtAddressPointV1?,
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
        val lang = LocalizationLanguage.FI

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
            coordinateSystem = coordinateSystem,
            locationTrack =
                getExtLocationTrack(
                    oid,
                    locationTrack,
                    layoutContext,
                    publication.publicationTime,
                    coordinateSystem,
                    lang,
                ),
        )
    }

    fun createLocationTrackModificationResponse(
        oid: Oid<LocationTrack>,
        modificationsFromVersion: Uuid<Publication>,
        trackNetworkVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtModifiedLocationTrackResponseV1? {
        val layoutContext = MainLayoutContext.official
        val lang = LocalizationLanguage.FI

        val fromPublication =
            publicationDao
                .fetchPublicationByUuid(modificationsFromVersion)
                .let(::requireNotNull) // TODO Improve error handling
        val toPublication =
            trackNetworkVersion?.let { uuid ->
                publicationDao.fetchPublicationByUuid(uuid).let(::requireNotNull)
            } // TODO Improve error handling
            ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        return if (fromPublication == toPublication) {
            logger.info(
                "there cannot be any differences if the requested publication ids are the same " +
                    "publication=${fromPublication.id}"
            )
            null
        } else {
            val locationTrackId =
                locationTrackDao.lookupByExternalId(oid)?.id
                    ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

            val fromLocationTrackVersion =
                locationTrackDao.fetchOfficialVersionAtMomentOrThrow(
                    layoutContext.branch,
                    locationTrackId,
                    fromPublication.publicationTime,
                ) // TODO Improve error handling

            val toLocationTrackVersion =
                locationTrackDao.fetchOfficialVersionAtMomentOrThrow(
                    layoutContext.branch,
                    locationTrackId,
                    toPublication.publicationTime,
                ) // TODO Improve error handling

            if (fromLocationTrackVersion == toLocationTrackVersion) {
                logger.info(
                    "location track version was the same for locationTrackId=$locationTrackId, " +
                        "earlierPublication=${fromPublication.id}, laterPublication=${toPublication.id}"
                )
                return null
            } else {
                return ExtModifiedLocationTrackResponseV1(
                    modificationsFromVersion = modificationsFromVersion,
                    trackNetworkVersion = toPublication.uuid,
                    coordinateSystem = coordinateSystem,
                    locationTrack =
                        getExtLocationTrack(
                            oid,
                            locationTrackDao.fetch(toLocationTrackVersion),
                            MainLayoutContext.official,
                            toPublication.publicationTime,
                            coordinateSystem,
                            lang,
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
        lang: LocalizationLanguage,
    ): ExtLocationTrackV1 {
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
            locationTrackService
                .getStartAndEndAtMoment(layoutContext, listOf(locationTrack.id as IntId), moment)
                .first()
                .let { startAndEnd -> layoutAlignmentStartAndEndToCoordinateSystem(coordinateSystem, startAndEnd) }
                .let { startAndEnd -> startAndEnd.start to startAndEnd.end }

        val locationTrackDescription =
            locationTrackService.getFullDescriptionsAtMoment(layoutContext, listOf(locationTrack), lang, moment).first()

        return ExtLocationTrackV1(
            locationTrackOid = oid,
            locationTrackName = locationTrack.name,
            locationTrackType = ExtLocationTrackTypeV1(locationTrack.type),
            locationTrackState = ExtLocationTrackStateV1(locationTrack.state),
            locationTrackDescription = locationTrackDescription,
            locationTrackOwner = locationTrackService.getLocationTrackOwner(locationTrack.ownerId).name,
            startLocation = startLocation?.let(ExtAddressPointV1::of),
            endLocation = endLocation?.let(ExtAddressPointV1::of),
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

internal fun <T> layoutAlignmentStartAndEndToCoordinateSystem(
    coordinateSystem: Srid,
    startAndEnd: AlignmentStartAndEnd<T>,
): AlignmentStartAndEnd<T> {
    return when (coordinateSystem) {
        LAYOUT_SRID -> startAndEnd
        else -> {
            val start =
                startAndEnd.start?.let { start ->
                    convertAlignmentEndPointCoordinateSystem(LAYOUT_SRID, coordinateSystem, start)
                }

            val end =
                startAndEnd.end?.let { end ->
                    convertAlignmentEndPointCoordinateSystem(LAYOUT_SRID, coordinateSystem, end)
                }

            startAndEnd.copy(start = start, end = end)
        }
    }
}

private fun convertAlignmentEndPointCoordinateSystem(
    sourceCoordinateSystem: Srid,
    targetCoordinateSystem: Srid,
    alignmentEndPoint: AlignmentEndPoint,
): AlignmentEndPoint {
    val convertedPoint =
        transformNonKKJCoordinate(sourceCoordinateSystem, targetCoordinateSystem, alignmentEndPoint.point)
    return alignmentEndPoint.copy(point = alignmentEndPoint.point.copy(x = convertedPoint.x, y = convertedPoint.y))
}
