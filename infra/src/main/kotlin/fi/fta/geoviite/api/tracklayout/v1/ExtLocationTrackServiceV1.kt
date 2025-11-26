package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
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
    private val trackNumberDao: LayoutTrackNumberDao,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtLocationTrackCollection(
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtLocationTrackCollectionResponseV1 {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        return createLocationTrackCollectionResponse(publication, coordinateSystem(extCoordinateSystem))
    }

    fun getExtLocationTrackCollectionModifications(
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedLocationTrackCollectionResponseV1? =
        publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value).let { publications
            ->
            if (publications.areDifferent()) {
                createLocationTrackCollectionModificationResponse(publications, coordinateSystem(extCoordinateSystem))
            } else {
                publicationsAreTheSame(layoutVersionFrom.value)
            }
        }

    fun getExtLocationTrack(
        oid: ExtOidV1<LocationTrack>,
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtLocationTrackResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        val id = idLookup(locationTrackDao, oid.value)
        return createLocationTrackResponse(oid.value, id, publication, coordinateSystem(extCoordinateSystem))
    }

    fun getExtLocationTrackModifications(
        oid: ExtOidV1<LocationTrack>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedLocationTrackResponseV1? {
        val publications = publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value)
        // Lookup before change check to produce consistent error if oid is not found
        val id = idLookup(locationTrackDao, oid.value)
        return if (publications.areDifferent()) {
            createLocationTrackModificationResponse(oid.value, id, publications, coordinateSystem(extCoordinateSystem))
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

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
                layoutVersion = ExtLayoutVersionV1(publication),
                coordinateSystem = ExtSridV1(coordinateSystem),
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
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
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
            layoutVersion = ExtLayoutVersionV1(publication),
            coordinateSystem = ExtSridV1(coordinateSystem),
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
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
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
            locationTrackOid = ExtOidV1(data.oid),
            locationTrackName = data.track.name,
            locationTrackType = ExtLocationTrackTypeV1.of(data.track.type),
            locationTrackState = ExtLocationTrackStateV1.of(data.track.state),
            locationTrackDescription = data.track.description,
            locationTrackOwner = locationTrackService.getLocationTrackOwner(data.track.ownerId).name,
            startLocation = data.geometry.start?.let(toEndPoint),
            endLocation = data.geometry.end?.let(toEndPoint),
            trackNumberName = data.trackNumber.number,
            trackNumberOid = ExtOidV1(data.trackNumberOid),
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
            trackNumberOid = oidLookup(trackNumberDao, branch, track.trackNumberId),
            trackNumber =
                trackNumberDao.getOfficialAtMoment(branch, track.trackNumberId, moment)
                    ?: throwTrackNumberNotFound(branch, moment, track.trackNumberId),
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
            trackNumberDao.getManyOfficialAtMoment(branch, distinctTrackNumberIds, moment).associateBy { trackNumber ->
                trackNumber.id
            }

        val locationTrackExtIds = locationTrackDao.fetchExternalIds(branch, locationTrackIds)
        val trackNumberExtIds = trackNumberDao.fetchExternalIds(branch, distinctTrackNumberIds)
        return tracksAndGeoms.map { (track, geom) ->
            LocationTrackData(
                oid = locationTrackExtIds[track.id]?.oid ?: throwOidNotFound(branch, track.id),
                track = track,
                geometry = geom,
                trackNumberOid =
                    trackNumberExtIds[track.trackNumberId]?.oid ?: throwOidNotFound(branch, track.trackNumberId),
                trackNumber =
                    trackNumbers[track.trackNumberId] ?: throwTrackNumberNotFound(branch, moment, track.trackNumberId),
                geocodingContext = produceIf(track.exists) { getGeocodingContext(track.trackNumberId) },
            )
        }
    }
}
