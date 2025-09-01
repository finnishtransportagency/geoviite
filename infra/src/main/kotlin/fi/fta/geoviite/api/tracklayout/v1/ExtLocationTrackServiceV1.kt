package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.error.TrackLayoutVersionNotFound
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@GeoviiteService
class ExtLocationTrackServiceV1
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createLocationTrackResponse(
        oid: Oid<LocationTrack>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtLocationTrackResponseV1? {
        val layoutContext = MainLayoutContext.official

        val publication =
            trackLayoutVersion?.let { uuid ->
                publicationDao.fetchPublicationByUuid(uuid)
                    ?: throw TrackLayoutVersionNotFound("trackLayoutVersion=${trackLayoutVersion}")
            } ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        val locationTrackId =
            locationTrackDao.lookupByExternalId(oid.toString())?.id
                ?: throw ExtOidNotFoundExceptionV1("location track lookup failed for oid=$oid")

        return locationTrackDao
            .fetchOfficialVersionAtMoment(layoutContext.branch, locationTrackId, publication.publicationTime)
            ?.let(locationTrackDao::fetch)
            ?.let { locationTrack ->
                ExtLocationTrackResponseV1(
                    trackLayoutVersion = publication.uuid,
                    coordinateSystem = coordinateSystem,
                    locationTrack =
                        getExtLocationTrack(
                            oid,
                            locationTrack,
                            layoutContext,
                            publication.publicationTime,
                            coordinateSystem,
                        ),
                )
            }
    }

    fun createLocationTrackModificationResponse(
        oid: Oid<LocationTrack>,
        modificationsFromVersion: Uuid<Publication>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtModifiedLocationTrackResponseV1? {
        val layoutContext = MainLayoutContext.official

        val (fromPublication, toPublication) =
            publicationService.getPublicationsToCompare(modificationsFromVersion, trackLayoutVersion)

        return if (fromPublication == toPublication) {
            logger.info(
                "there cannot be any differences if the requested publication ids are the same publication=${fromPublication.id}"
            )
            null
        } else {
            val locationTrackId =
                locationTrackDao.lookupByExternalId(oid.toString())?.id
                    ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

            val fromLocationTrackVersion =
                locationTrackDao.fetchOfficialVersionAtMoment(
                    layoutContext.branch,
                    locationTrackId,
                    fromPublication.publicationTime,
                )

            val toLocationTrackVersion =
                locationTrackDao.fetchOfficialVersionAtMoment(
                    layoutContext.branch,
                    locationTrackId,
                    toPublication.publicationTime,
                )

            return if (fromLocationTrackVersion == toLocationTrackVersion) {
                logger.info(
                    "location track version was the same for locationTrackId=$locationTrackId, earlierPublication=${fromPublication.id}, laterPublication=${toPublication.id}"
                )
                null
            } else {
                checkNotNull(toLocationTrackVersion) {
                    "It should not be possible for the fromLocationTrackVersion to be non-null, while the toLocationTrackVersion is null."
                }

                ExtModifiedLocationTrackResponseV1(
                    modificationsFromVersion = modificationsFromVersion,
                    trackLayoutVersion = toPublication.uuid,
                    coordinateSystem = coordinateSystem,
                    locationTrack =
                        getExtLocationTrack(
                            oid,
                            locationTrackDao.fetch(toLocationTrackVersion),
                            MainLayoutContext.official,
                            toPublication.publicationTime,
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
        val trackNumberName =
            layoutTrackNumberDao
                .fetchOfficialVersionAtMoment(layoutContext.branch, locationTrack.trackNumberId, moment)
                ?.let(layoutTrackNumberDao::fetch)
                ?.number
                ?: throw ExtTrackNumberNotFoundV1(
                    "track number was not found for branch=${layoutContext.branch}, trackNumberId=${locationTrack.trackNumberId}, moment=$moment"
                )

        val trackNumberOid =
            layoutTrackNumberDao.fetchExternalId(layoutContext.branch, locationTrack.trackNumberId)?.oid
                ?: throw ExtOidNotFoundExceptionV1(
                    "track number oid was not found, branch=${layoutContext.branch}, trackNumberId=${locationTrack.trackNumberId}"
                )

        val (startLocation, endLocation) =
            locationTrackService
                .getStartAndEndAtMoment(layoutContext, listOf(locationTrack.id as IntId), moment)
                .first()
                .let { startAndEnd -> layoutAlignmentStartAndEndToCoordinateSystem(coordinateSystem, startAndEnd) }
                .let { startAndEnd -> startAndEnd.start to startAndEnd.end }

        return ExtLocationTrackV1(
            locationTrackOid = oid,
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
