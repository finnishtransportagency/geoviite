package fi.fta.geoviite.api.tracklayout.v1

import ExtTrackNumberV1
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Srid
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

@Schema(name = "Vastaus: Ratanumerokokoelma")
data class ExtTrackNumberCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER_COLLECTION) val trackNumberCollection: List<ExtTrackNumberV1>,
)

@Schema(name = "Vastaus: Muutettu sijaintiraidekokoelma")
data class ExtModifiedTrackNumberCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER_COLLECTION) val trackNumberCollection: List<ExtTrackNumberV1>,
)

@GeoviiteService
class ExtTrackNumberCollectionServiceV1
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val publicationDao: PublicationDao,
    private val publicationService: PublicationService,
    private val referenceLineService: ReferenceLineService,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createTrackNumberCollectionResponse(
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtTrackNumberCollectionResponseV1 {
        val layoutContext = MainLayoutContext.official

        val (publication, locationTracks) =
            when (trackLayoutVersion) {
                null -> {
                    publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single().let {
                        newestPublication ->
                        newestPublication to layoutTrackNumberDao.list(layoutContext, includeDeleted = false)
                    }
                }
                else -> {
                    trackLayoutVersion
                        .let { uuid -> publicationDao.fetchPublicationByUuid(uuid) }
                        ?.let { specifiedPublication ->
                            specifiedPublication to
                                layoutTrackNumberDao.listPublishedLayoutTrackNumbersAtMoment(
                                    specifiedPublication.publicationTime
                                )
                        } ?: throw TrackLayoutVersionNotFound("trackLayoutVersion=${trackLayoutVersion}")
                }
            }

        return ExtTrackNumberCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = coordinateSystem,
            trackNumberCollection =
                extGetTrackNumberCollection(
                    layoutContext,
                    locationTracks,
                    coordinateSystem,
                    publication.publicationTime,
                ),
        )
    }

    fun createTrackNumberCollectionModificationResponse(
        modificationsFromVersion: Uuid<Publication>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtModifiedTrackNumberCollectionResponseV1? {
        val layoutContext = MainLayoutContext.official

        val (fromPublication, toPublication) =
            publicationService.getPublicationsToCompare(modificationsFromVersion, trackLayoutVersion)

        return if (fromPublication == toPublication) {
            logger.info(
                "there cannot be any differences if the requested publication ids are the same, publicationId=${fromPublication.id}"
            )
            null
        } else {
            val changedIds =
                publicationDao.fetchPublishedTrackNumbersAfterMoment(
                    fromPublication.publicationTime,
                    toPublication.publicationTime,
                )

            val modifiedTrackNumbers =
                layoutTrackNumberDao.getManyOfficialAtMoment(
                    layoutContext.branch,
                    changedIds,
                    toPublication.publicationTime,
                )

            if (modifiedTrackNumbers.isEmpty()) {
                logger.info(
                    "There were no modified track numbers between publications ${fromPublication.id} -> ${toPublication.id}"
                )
                null
            } else {
                ExtModifiedTrackNumberCollectionResponseV1(
                    modificationsFromVersion = modificationsFromVersion,
                    trackLayoutVersion = toPublication.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumberCollection =
                        extGetTrackNumberCollection(
                            layoutContext,
                            modifiedTrackNumbers,
                            coordinateSystem,
                            toPublication.publicationTime,
                        ),
                )
            }
        }
    }

    fun extGetTrackNumberCollection(
        layoutContext: LayoutContext,
        trackNumbers: List<LayoutTrackNumber>,
        coordinateSystem: Srid,
        moment: Instant,
    ): List<ExtTrackNumberV1> {
        val trackNumberIds = trackNumbers.map { trackNumber -> trackNumber.id as IntId }

        val referenceLineStartsAndEnds =
            referenceLineService
                .getStartAndEndAtMoment(
                    layoutContext,
                    trackNumbers.mapNotNull { trackNumber -> trackNumber.referenceLineId },
                    moment,
                )
                .associateBy { it.id }

        val externalTrackNumberIds = layoutTrackNumberDao.fetchExternalIds(layoutContext.branch, trackNumberIds)

        return trackNumbers.map { trackNumber ->
            val (startLocation, endLocation) =
                referenceLineStartsAndEnds[trackNumber.referenceLineId]?.let { startAndEnd ->
                    layoutAlignmentStartAndEndToCoordinateSystem(coordinateSystem, startAndEnd).let {
                        convertedStartAndEnd ->
                        convertedStartAndEnd.start to convertedStartAndEnd.end
                    }
                } ?: (null to null)

            val trackNumberOid =
                externalTrackNumberIds[trackNumber.id]?.oid
                    ?: throw ExtOidNotFoundExceptionV1(
                        "track number oid not found, layoutTrackNumberId=${trackNumber.id}"
                    )

            ExtTrackNumberV1(
                trackNumberOid = trackNumberOid,
                trackNumber = trackNumber.number,
                trackNumberDescription = trackNumber.description,
                trackNumberState = ExtTrackNumberStateV1.of(trackNumber.state),
                startLocation = startLocation?.let(::ExtAddressPointV1),
                endLocation = endLocation?.let(::ExtAddressPointV1),
            )
        }
    }
}
