package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@Schema(name = "Vastaus: Sijaintiraidekokoelma")
data class ExtLocationTrackCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty(LOCATION_TRACK_COLLECTION) val locationTrackCollection: List<ExtLocationTrackV1>,
)

@Schema(name = "Vastaus: Muutettu sijaintiraidekokoelma")
data class ExtModifiedLocationTrackCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty(LOCATION_TRACK_COLLECTION) val locationTrackCollection: List<ExtLocationTrackV1>,
)

@GeoviiteService
class ExtLocationTrackCollectionServiceV1
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationDao: PublicationDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createLocationTrackCollectionResponse(
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtLocationTrackCollectionResponseV1 {
        val layoutContext = MainLayoutContext.official

        val (publication, locationTracks) =
            when (trackLayoutVersion) {
                null -> {
                    publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single().let {
                        newestPublication ->
                        newestPublication to locationTrackDao.list(layoutContext, includeDeleted = false)
                    }
                }
                else -> {
                    trackLayoutVersion
                        .let { uuid -> publicationDao.fetchPublicationByUuid(uuid) }
                        ?.let { specifiedPublication ->
                            specifiedPublication to
                                locationTrackDao.listPublishedLocationTracksAtMoment(
                                    specifiedPublication.publicationTime
                                )
                        } ?: throw ExtTrackLayoutVersionNotFound("trackLayoutVersion=${trackLayoutVersion}")
                }
            }

        return ExtLocationTrackCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = coordinateSystem,
            locationTrackCollection =
                extGetLocationTrackCollection(
                    layoutContext,
                    locationTracks,
                    coordinateSystem,
                    publication.publicationTime,
                ),
        )
    }

    fun createLocationTrackCollectionModificationResponse(
        modificationsFromVersion: Uuid<Publication>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtModifiedLocationTrackCollectionResponseV1? {
        val layoutContext = MainLayoutContext.official

        val fromPublication =
            publicationDao.fetchPublicationByUuid(modificationsFromVersion)
                ?: throw ExtTrackLayoutVersionNotFound("modificationsFromVersion=${modificationsFromVersion}")

        val toPublication =
            trackLayoutVersion?.let { uuid ->
                publicationDao.fetchPublicationByUuid(uuid)
                    ?: throw ExtTrackLayoutVersionNotFound("trackLayoutVersion=${trackLayoutVersion}")
            } ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        return if (fromPublication == toPublication) {
            logger.info(
                "there cannot be any differences if the requested publication ids are the same, publicationId=${fromPublication.id}"
            )
            null
        } else {
            val changedIds =
                publicationDao.fetchPublishedLocationTracksAfterMoment(
                    fromPublication.publicationTime,
                    toPublication.publicationTime,
                )
            val modifiedLocationTracks =
                locationTrackDao.getManyOfficialAtMoment(
                    layoutContext.branch,
                    changedIds,
                    toPublication.publicationTime,
                )

            if (modifiedLocationTracks.isEmpty()) {
                logger.info(
                    "There were no modified location tracks between publications ${fromPublication.id} -> ${toPublication.id}"
                )
                null
            } else {
                ExtModifiedLocationTrackCollectionResponseV1(
                    modificationsFromVersion = modificationsFromVersion,
                    trackLayoutVersion = toPublication.uuid,
                    coordinateSystem = coordinateSystem,
                    locationTrackCollection =
                        extGetLocationTrackCollection(
                            layoutContext,
                            modifiedLocationTracks,
                            coordinateSystem,
                            toPublication.publicationTime,
                        ),
                )
            }
        }
    }

    fun extGetLocationTrackCollection(
        layoutContext: LayoutContext,
        locationTracks: List<LocationTrack>,
        coordinateSystem: Srid,
        moment: Instant,
    ): List<ExtLocationTrackV1> {
        val locationTrackIds = locationTracks.map { locationTrack -> locationTrack.id as IntId }
        val distinctTrackNumberIds = locationTracks.map { locationTrack -> locationTrack.trackNumberId }.distinct()

        val trackNumbers =
            layoutTrackNumberDao
                .getManyOfficialAtMoment(layoutContext.branch, distinctTrackNumberIds, moment)
                .associateBy { trackNumber -> trackNumber.id }

        val externalLocationTrackIds = locationTrackDao.fetchExternalIds(layoutContext.branch, locationTrackIds)
        val externalTrackNumberIds = layoutTrackNumberDao.fetchExternalIds(layoutContext.branch, distinctTrackNumberIds)

        val locationTrackStartsAndEnds =
            locationTrackService.getStartAndEndAtMoment(layoutContext, locationTrackIds, moment)

        require(locationTracks.size == locationTrackStartsAndEnds.size) {
            "locationTracks.size=${locationTracks.size} != locationTrackStartsAndEnds.size=${locationTrackStartsAndEnds.size}"
        }

        return locationTracks.mapIndexed { index, locationTrack ->
            val locationTrackOid =
                externalLocationTrackIds[locationTrack.id]?.oid
                    ?: throw ExtOidNotFoundExceptionV1(
                        "location track oid not found, locationTrackId=${locationTrack.id}"
                    )

            val (startLocation, endLocation) =
                layoutAlignmentStartAndEndToCoordinateSystem(coordinateSystem, locationTrackStartsAndEnds[index]).let {
                    startAndEnd ->
                    startAndEnd.start to startAndEnd.end
                }

            val trackNumberName =
                trackNumbers[locationTrack.trackNumberId]?.number
                    ?: throw ExtTrackNumberNotFoundV1(
                        "track number was not found for " +
                            "branch=${layoutContext.branch}, trackNumberId=${locationTrack.trackNumberId}, moment=$moment"
                    )

            val trackNumberOid =
                externalTrackNumberIds[locationTrack.trackNumberId]?.oid
                    ?: throw ExtOidNotFoundExceptionV1(
                        "track number oid not found, layoutTrackNumberId=${locationTrack.trackNumberId}"
                    )

            ExtLocationTrackV1(
                locationTrackOid = locationTrackOid,
                locationTrackName = locationTrack.name,
                locationTrackType = ExtLocationTrackTypeV1.of(locationTrack.type),
                locationTrackState = ExtLocationTrackStateV1.of(locationTrack.state),
                locationTrackDescription = locationTrack.description,
                locationTrackOwner = locationTrackService.getLocationTrackOwner(locationTrack.ownerId).name,
                startLocation = startLocation?.let(::ExtAddressPointV1),
                endLocation = endLocation?.let(::ExtAddressPointV1),
                trackNumberName = trackNumberName,
                trackNumberOid = trackNumberOid,
            )
        }
    }
}
