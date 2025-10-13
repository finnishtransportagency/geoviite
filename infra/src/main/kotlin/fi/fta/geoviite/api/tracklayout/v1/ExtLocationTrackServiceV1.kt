package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtLocationTrackServiceV1
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationDao: PublicationDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createLocationTrackResponse(
        oid: Oid<LocationTrack>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtLocationTrackResponseV1? {
        val locationTrackId =
            locationTrackDao.lookupByExternalId(oid.toString())?.id
                ?: throw ExtOidNotFoundExceptionV1("location track lookup failed for oid=$oid")

        return locationTrackDao
            .fetchOfficialVersionAtMoment(publication.layoutBranch.branch, locationTrackId, publication.publicationTime)
            ?.let(locationTrackDao::fetch)
            ?.let { locationTrack ->
                ExtLocationTrackResponseV1(
                    trackLayoutVersion = publication.uuid,
                    coordinateSystem = coordinateSystem,
                    locationTrack =
                        getExtLocationTrack(
                            oid,
                            locationTrack,
                            publication.layoutBranch.branch,
                            publication.publicationTime,
                            coordinateSystem,
                        ),
                )
            }
    }

    fun createLocationTrackModificationResponse(
        oid: Oid<LocationTrack>,
        id: IntId<LocationTrack>,
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedLocationTrackResponseV1? {
        return publicationDao
            .fetchPublishedLocationTrackBetween(id, publications.from.publicationTime, publications.to.publicationTime)
            ?.let(locationTrackDao::fetch)
            ?.let { locationTrack ->
                ExtModifiedLocationTrackResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    locationTrack =
                        getExtLocationTrack(
                            oid,
                            locationTrack,
                            LayoutBranch.main,
                            publications.to.publicationTime,
                            coordinateSystem,
                        ),
                )
            } ?: layoutAssetVersionsAreTheSame(id, publications)
    }

    fun getExtLocationTrack(
        oid: Oid<LocationTrack>,
        locationTrack: LocationTrack,
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
    ): ExtLocationTrackV1 {
        val trackNumberName =
            layoutTrackNumberDao
                .fetchOfficialVersionAtMoment(branch, locationTrack.trackNumberId, moment)
                ?.let(layoutTrackNumberDao::fetch)
                ?.number
                ?: throw ExtTrackNumberNotFoundV1(
                    "track number was not found for branch=$branch, trackNumberId=${locationTrack.trackNumberId}, moment=$moment"
                )

        val trackNumberOid =
            layoutTrackNumberDao.fetchExternalId(branch, locationTrack.trackNumberId)?.oid
                ?: throw ExtOidNotFoundExceptionV1(
                    "track number oid was not found, branch=$branch, trackNumberId=${locationTrack.trackNumberId}"
                )

        val (startLocation, endLocation) =
            locationTrackService
                .getStartAndEndAtMoment(branch, listOf(locationTrack.id as IntId), moment)
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
