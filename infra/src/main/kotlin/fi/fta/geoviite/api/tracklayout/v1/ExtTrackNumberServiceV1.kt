package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.error.TrackLayoutVersionNotFound
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@Schema(name = "Vastaus: Ratanumero")
data class ExtTrackNumberResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER_PARAM) val trackNumber: ExtTrackNumberV1,
)

@Schema(name = "Vastaus: Muutettu ratanumero")
data class ExtModifiedTrackNumberResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER_PARAM) val trackNumber: ExtTrackNumberV1,
)

@Schema(name = "Ratanumero")
data class ExtTrackNumberV1(
    @JsonProperty("ratanumero_oid") val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty("ratanumero") val trackNumber: TrackNumber,
    @JsonProperty("kuvaus") val trackNumberDescription: TrackNumberDescription,
    @JsonProperty("tila") val trackNumberState: ExtTrackNumberStateV1,
    @JsonProperty("alkusijainti") val startLocation: ExtAddressPointV1?,
    @JsonProperty("loppusijainti") val endLocation: ExtAddressPointV1?,
)

@GeoviiteService
class ExtTrackNumberServiceV1
@Autowired
constructor(
    private val publicationDao: PublicationDao,
    private val publicationService: PublicationService,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createTrackNumberResponse(
        oid: Oid<LayoutTrackNumber>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtTrackNumberResponseV1? {
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
                ExtTrackNumberResponseV1(
                    trackLayoutVersion = publication.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumber =
                        getExtTrackNumber(
                            oid,
                            trackNumber,
                            layoutContext,
                            publication.publicationTime,
                            coordinateSystem,
                        ),
                )
            }
    }

    fun createTrackNumberModificationResponse(
        oid: Oid<LayoutTrackNumber>,
        modificationsFromVersion: Uuid<Publication>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtModifiedTrackNumberResponseV1? {
        val layoutContext = MainLayoutContext.official

        val (fromPublication, toPublication) =
            publicationService.getPublicationsToCompare(modificationsFromVersion, trackLayoutVersion)

        return if (fromPublication == toPublication) {
            logger.info(
                "there cannot be any differences if the requested publication ids are the same publication=${fromPublication.id}"
            )
            null
        } else {
            val trackNumberId =
                layoutTrackNumberDao.lookupByExternalId(oid.toString())?.id
                    ?: throw ExtOidNotFoundExceptionV1("track number lookup failed, oid=$oid")

            val fromTrackNumberVersion =
                layoutTrackNumberDao.fetchOfficialVersionAtMoment(
                    layoutContext.branch,
                    trackNumberId,
                    fromPublication.publicationTime,
                )

            val toTrackNumberVersion =
                layoutTrackNumberDao.fetchOfficialVersionAtMoment(
                    layoutContext.branch,
                    trackNumberId,
                    toPublication.publicationTime,
                )

            return if (fromTrackNumberVersion == toTrackNumberVersion) {
                logger.info(
                    "track number version was the same for trackNumberId=$trackNumberId, earlierPublication=${fromPublication.id}, laterPublication=${toPublication.id}"
                )
                null
            } else {
                checkNotNull(toTrackNumberVersion) {
                    "It should not be possible for the fromTrackNumberVersion to be non-null, while the toTrackNumberVersion is null."
                }

                ExtModifiedTrackNumberResponseV1(
                    modificationsFromVersion = modificationsFromVersion,
                    trackLayoutVersion = toPublication.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumber =
                        getExtTrackNumber(
                            oid,
                            layoutTrackNumberDao.fetch(toTrackNumberVersion),
                            MainLayoutContext.official,
                            toPublication.publicationTime,
                            coordinateSystem,
                        ),
                )
            }
        }
    }

    fun getExtTrackNumber(
        oid: Oid<LayoutTrackNumber>,
        trackNumber: LayoutTrackNumber,
        layoutContext: LayoutContext,
        moment: Instant,
        coordinateSystem: Srid,
    ): ExtTrackNumberV1 {
        val (startLocation, endLocation) =
            referenceLineService
                .getStartAndEndAtMoment(layoutContext, listOf(trackNumber.referenceLineId as IntId), moment)
                .firstOrNull()
                ?.let { startAndEnd -> layoutAlignmentStartAndEndToCoordinateSystem(coordinateSystem, startAndEnd) }
                ?.let { startAndEnd -> startAndEnd.start to startAndEnd.end } ?: (null to null)

        return ExtTrackNumberV1(
            trackNumberOid = oid,
            trackNumber = trackNumber.number,
            trackNumberDescription = trackNumber.description,
            trackNumberState = trackNumber.state.let(ExtTrackNumberStateV1::of),
            startLocation = startLocation?.let(::ExtAddressPointV1),
            endLocation = endLocation?.let(::ExtAddressPointV1),
        )
    }
}
