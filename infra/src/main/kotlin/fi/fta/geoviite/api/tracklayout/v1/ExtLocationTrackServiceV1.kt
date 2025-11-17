package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.util.produceIf
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
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtLocationTrackCollection(
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtLocationTrackCollectionResponseV1 {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        return createLocationTrackCollectionResponse(publication, coordinateSystem ?: LAYOUT_SRID)
    }

    fun getExtLocationTrackCollectionModifications(
        trackLayoutVersionFrom: Uuid<Publication>,
        trackLayoutVersionTo: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ) =
        publicationService.getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo).let { publications ->
            if (publications.areDifferent()) {
                createLocationTrackCollectionModificationResponse(
                    publications,
                    coordinateSystem = coordinateSystem ?: LAYOUT_SRID,
                )
            } else {
                publicationsAreTheSame(trackLayoutVersionFrom)
            }
        }

    fun getExtLocationTrack(
        oid: Oid<LocationTrack>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtLocationTrackResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        return createLocationTrackResponse(oid, idLookup(oid), publication, coordinateSystem ?: LAYOUT_SRID)
    }

    fun getExtLocationTrackModifications(
        oid: Oid<LocationTrack>,
        trackLayoutVersionFrom: Uuid<Publication>,
        trackLayoutVersionTo: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtModifiedLocationTrackResponseV1? {
        val publications = publicationService.getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo)
        val id = idLookup(oid) // Lookup before change check to produce consistent error if oid is not found
        return if (publications.areDifferent()) {
            createLocationTrackModificationResponse(oid, id, publications, coordinateSystem ?: LAYOUT_SRID)
        } else {
            publicationsAreTheSame(trackLayoutVersionFrom)
        }
    }

    private fun idLookup(oid: Oid<LocationTrack>): IntId<LocationTrack> =
        locationTrackDao.lookupByExternalId(oid)?.id
            ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

    private fun createLocationTrackResponse(
        oid: Oid<LocationTrack>,
        id: IntId<LocationTrack>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtLocationTrackResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        return locationTrackService.getOfficialWithGeometryAtMoment(branch, id, moment)?.let { (track, geometry) ->
            val data = getLocationTrackData(branch, moment, oid, track, geometry)
            ExtLocationTrackResponseV1(
                trackLayoutVersion = publication.uuid,
                coordinateSystem = coordinateSystem,
                locationTrack = createExtLocationTrack(data, coordinateSystem),
            )
        }
    }

    private fun createLocationTrackModificationResponse(
        oid: Oid<LocationTrack>,
        id: IntId<LocationTrack>,
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedLocationTrackResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedLocationTrackBetween(id, startMoment, endMoment)
            ?.let(locationTrackService::getWithGeometry)
            ?.let { (track, geometry) ->
                val data = getLocationTrackData(branch, endMoment, oid, track, geometry)
                ExtModifiedLocationTrackResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    locationTrack = createExtLocationTrack(data, coordinateSystem),
                )
            } ?: layoutAssetVersionsAreTheSame(id, publications)
    }

    private fun createLocationTrackCollectionResponse(
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtLocationTrackCollectionResponseV1 {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val tracksAndGeoms = locationTrackService.listOfficialWithGeometryAtMoment(branch, moment, false)
        return ExtLocationTrackCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = coordinateSystem,
            locationTrackCollection = createExtLocationTracks(branch, moment, coordinateSystem, tracksAndGeoms),
        )
    }

    private fun createLocationTrackCollectionModificationResponse(
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedLocationTrackCollectionResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedLocationTracksBetween(startMoment, endMoment)
            .takeIf { versions -> versions.isNotEmpty() }
            ?.let(locationTrackService::getManyWithGeometries)
            ?.let { tracksAndGeoms ->
                ExtModifiedLocationTrackCollectionResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    locationTrackCollection =
                        createExtLocationTracks(branch, endMoment, coordinateSystem, tracksAndGeoms),
                )
            } ?: layoutAssetCollectionWasUnmodified<LocationTrack>(publications)
    }

    private fun createExtLocationTracks(
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
        tracksAndGeoms: List<Pair<LocationTrack, LocationTrackGeometry>>,
    ): List<ExtLocationTrackV1> {
        return getLocationTrackData(branch, moment, tracksAndGeoms)
            .parallelStream()
            .map { data -> createExtLocationTrack(data, coordinateSystem) }
            .toList()
    }

    private fun createExtLocationTrack(data: LocationTrackData, coordinateSystem: Srid): ExtLocationTrackV1 {
        val toEndPoint = { p: IPoint -> toExtAddressPoint(p, data.geocodingContext, coordinateSystem) }
        return ExtLocationTrackV1(
            locationTrackOid = data.oid,
            locationTrackName = data.track.name,
            locationTrackType = ExtLocationTrackTypeV1.of(data.track.type),
            locationTrackState = ExtLocationTrackStateV1.of(data.track.state),
            locationTrackDescription = data.track.description,
            locationTrackOwner = locationTrackService.getLocationTrackOwner(data.track.ownerId).name,
            startLocation = data.geometry.start?.let(toEndPoint),
            endLocation = data.geometry.end?.let(toEndPoint),
            trackNumberName = data.trackNumber.number,
            trackNumberOid = data.trackNumberOid,
        )
    }

    private data class LocationTrackData(
        val oid: Oid<LocationTrack>,
        val track: LocationTrack,
        val geometry: LocationTrackGeometry,
        val trackNumberOid: Oid<LayoutTrackNumber>,
        val trackNumber: LayoutTrackNumber,
        val geocodingContext: GeocodingContext<ReferenceLineM>?,
    )

    private fun getLocationTrackData(
        branch: LayoutBranch,
        moment: Instant,
        oid: Oid<LocationTrack>,
        track: LocationTrack,
        geometry: LocationTrackGeometry,
    ): LocationTrackData =
        LocationTrackData(
            oid = oid,
            track = track,
            geometry = geometry,
            trackNumberOid =
                layoutTrackNumberDao.fetchExternalId(branch, track.trackNumberId)?.oid
                    ?: throw ExtOidNotFoundExceptionV1(
                        "track number oid was not found: branch=$branch trackNumberId=${track.trackNumberId}"
                    ),
            trackNumber =
                layoutTrackNumberDao.getOfficialAtMoment(branch, track.trackNumberId, moment)
                    ?: throw ExtTrackNumberNotFoundV1(
                        "track number was not found: branch=$branch trackNumberId=${track.trackNumberId} moment=$moment"
                    ),
            geocodingContext =
                produceIf(track.exists) {
                    geocodingService.getGeocodingContextAtMoment(branch, track.trackNumberId, moment)
                },
        )

    private fun getLocationTrackData(
        branch: LayoutBranch,
        moment: Instant,
        tracksAndGeoms: List<Pair<LocationTrack, LocationTrackGeometry>>,
    ): List<LocationTrackData> {
        val locationTrackIds = tracksAndGeoms.map { (track, _) -> track.id as IntId }
        val distinctTrackNumberIds = tracksAndGeoms.map { (track, _) -> track.trackNumberId }.distinct()

        val getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment)
        val trackNumbers =
            layoutTrackNumberDao.getManyOfficialAtMoment(branch, distinctTrackNumberIds, moment).associateBy {
                trackNumber ->
                trackNumber.id
            }

        val externalLocationTrackIds = locationTrackDao.fetchExternalIds(branch, locationTrackIds)
        val externalTrackNumberIds = layoutTrackNumberDao.fetchExternalIds(branch, distinctTrackNumberIds)
        return tracksAndGeoms.map { (track, geom) ->
            LocationTrackData(
                oid =
                    externalLocationTrackIds[track.id]?.oid
                        ?: throw ExtOidNotFoundExceptionV1("location track oid not found: locationTrackId=${track.id}"),
                track = track,
                geometry = geom,
                trackNumberOid =
                    externalTrackNumberIds[track.trackNumberId]?.oid
                        ?: throw ExtOidNotFoundExceptionV1(
                            "track number oid was not found: branch=$branch trackNumberId=${track.trackNumberId}"
                        ),
                trackNumber =
                    trackNumbers[track.trackNumberId]
                        ?: throw ExtTrackNumberNotFoundV1(
                            "track number was not found: branch=$branch trackNumberId=${track.trackNumberId} moment=$moment"
                        ),
                geocodingContext = produceIf(track.exists) { getGeocodingContext(track.trackNumberId) },
            )
        }
    }
}
