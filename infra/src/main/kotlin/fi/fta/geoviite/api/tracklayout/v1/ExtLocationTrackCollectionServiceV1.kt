package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@GeoviiteService
class ExtLocationTrackCollectionServiceV1
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationDao: PublicationDao,
) {

    fun createLocationTrackCollectionResponse(
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtLocationTrackCollectionResponseV1 {
        return ExtLocationTrackCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = coordinateSystem,
            locationTrackCollection =
                extGetLocationTrackCollection(
                    LayoutContext.of(publication.layoutBranch.branch, PublicationState.OFFICIAL),
                    locationTrackDao
                        .listOfficialAtMoment(publication.layoutBranch.branch, publication.publicationTime)
                        .filter { track -> track.state != LocationTrackState.DELETED },
                    coordinateSystem,
                    publication.publicationTime,
                ),
        )
    }

    fun createLocationTrackCollectionModificationResponse(
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedLocationTrackCollectionResponseV1? {
        return publicationDao
            .fetchPublishedLocationTracksAfterMoment(
                publications.from.publicationTime,
                publications.to.publicationTime,
            )
            .let { changedIds ->
                locationTrackDao.getManyOfficialAtMoment(
                    LayoutBranch.main,
                    changedIds,
                    publications.to.publicationTime,
                )
            }
            .takeIf { modifiedLocationTracks -> modifiedLocationTracks.isNotEmpty() }
            ?.let { modifiedLocationTracks ->
                ExtModifiedLocationTrackCollectionResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    locationTrackCollection =
                        extGetLocationTrackCollection(
                            LayoutContext.of(publications.to.layoutBranch.branch, PublicationState.OFFICIAL),
                            modifiedLocationTracks,
                            coordinateSystem,
                            publications.to.publicationTime,
                        ),
                )
            } ?: layoutAssetCollectionWasUnmodified<LocationTrack>(publications)
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
