package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignService
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
    private val layoutDesignService: LayoutDesignService,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtLocationTrackCollection(
        designOid: ExtOidV1<LayoutDesign>?,
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
        trackNameFilter: String? = null,
        trackNumberOidFilter: ExtOidV1<LayoutTrackNumber>? = null,
    ): ExtLocationTrackCollectionResponseV1 {
        val branch = branchByDesignOid(designOid)

        val publication = publicationService.getPublicationByUuidOrLatest(branch, layoutVersion?.value)
        return createLocationTrackCollectionResponse(
            publication,
            coordinateSystem(extCoordinateSystem),
            branch,
            trackNameFilter,
            trackNumberOidFilter,
        )
    }

    fun getExtLocationTrackCollectionModifications(
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        designOid: ExtOidV1<LayoutDesign>?,
        extCoordinateSystem: ExtSridV1?,
        trackNameFilter: String? = null,
        trackNumberOidFilter: ExtOidV1<LayoutTrackNumber>? = null,
    ): ExtModifiedLocationTrackCollectionResponseV1? {
        val branch = branchByDesignOid(designOid)

        return publicationService
            .getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value, branch = branch)
            .let { publications ->
                if (publications.areDifferent()) {
                    createLocationTrackCollectionModificationResponse(
                        publications,
                        branch,
                        coordinateSystem(extCoordinateSystem),
                        trackNameFilter,
                        trackNumberOidFilter,
                    )
                } else {
                    publicationsAreTheSame(layoutVersionFrom.value)
                }
            }
    }

    fun getExtLocationTrack(
        oid: ExtOidV1<LocationTrack>,
        layoutVersion: ExtLayoutVersionV1?,
        designOid: ExtOidV1<LayoutDesign>?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtLocationTrackResponseV1? {
        val branch = branchByDesignOid(designOid)
        val publication = publicationService.getPublicationByUuidOrLatest(branch, layoutVersion?.value)
        val id = idLookup(locationTrackDao, oid.value)
        val oids = branchOids(locationTrackDao, branch, oid.value, id)
        return createLocationTrackResponse(oids, id, publication, branch, coordinateSystem(extCoordinateSystem))
    }

    fun getExtLocationTrackModifications(
        oid: ExtOidV1<LocationTrack>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        designOid: ExtOidV1<LayoutDesign>?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedLocationTrackResponseV1? {
        val branch = branchByDesignOid(designOid)
        val publications =
            publicationService.getPublicationsToCompare(
                layoutVersionFrom.value,
                layoutVersionTo?.value,
                branch = branch,
            )
        // Lookup before change check to produce consistent error if oid is not found
        val id = idLookup(locationTrackDao, oid.value)
        val oids = branchOids(locationTrackDao, branch, oid.value, id)
        return if (publications.areDifferent()) {
            createLocationTrackModificationResponse(
                oids,
                id,
                publications,
                branch,
                coordinateSystem(extCoordinateSystem),
            )
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    private fun createLocationTrackResponse(
        oids: BranchOidsV1<LocationTrack>,
        id: IntId<LocationTrack>,
        publication: Publication,
        branch: LayoutBranch,
        coordinateSystem: Srid,
    ): ExtLocationTrackResponseV1? {
        val moment = publication.publicationTime
        return locationTrackService.getOfficialWithGeometryAtMoment(branch, id, moment)?.let { (track, geometry) ->
            val (oid, officialOid) = oids
            val data = getLocationTrackData(branch, moment, oid, officialOid, track, geometry)
            ExtLocationTrackResponseV1(
                layoutVersion = ExtLayoutVersionV1(publication),
                coordinateSystem = ExtSridV1(coordinateSystem),
                locationTrack = createExtLocationTrack(data, coordinateSystem),
            )
        }
    }

    private fun createLocationTrackModificationResponse(
        oids: BranchOidsV1<LocationTrack>,
        id: IntId<LocationTrack>,
        publications: PublicationComparison,
        branch: LayoutBranch,
        coordinateSystem: Srid,
    ): ExtModifiedLocationTrackResponseV1? {
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchLatestPublishedLocationTrackChangeTimeBetween(id, startMoment, endMoment, branch)
            ?.let { changeTime -> locationTrackDao.fetchOfficialVersionAtMoment(branch, id, changeTime) }
            ?.let(locationTrackService::getWithGeometry)
            ?.let { (track, geometry) ->
                val (oid, officialOid) = oids
                val data = getLocationTrackData(branch, endMoment, oid, officialOid, track, geometry)
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
        branch: LayoutBranch,
        nameFilter: String?,
        trackNumberOidFilter: ExtOidV1<LayoutTrackNumber>?,
    ): ExtLocationTrackCollectionResponseV1 {
        val moment = publication.publicationTime
        val tracksAndGeoms = locationTrackService.listOfficialWithGeometryAtMoment(branch, moment, false)
        val branchTrackIds = designBranchTrackIds(branch, tracksAndGeoms)
        val filteredTracksAndGeoms = tracksAndGeoms.filter(filterTracks(nameFilter, branchTrackIds))
        return ExtLocationTrackCollectionResponseV1(
            layoutVersion = ExtLayoutVersionV1(publication),
            coordinateSystem = ExtSridV1(coordinateSystem),
            locationTrackCollection =
                createExtLocationTracks(branch, moment, coordinateSystem, filteredTracksAndGeoms, trackNumberOidFilter),
        )
    }

    private fun createLocationTrackCollectionModificationResponse(
        publications: PublicationComparison,
        branch: LayoutBranch,
        coordinateSystem: Srid,
        nameFilter: String?,
        trackNumberOidFilter: ExtOidV1<LayoutTrackNumber>?,
    ): ExtModifiedLocationTrackCollectionResponseV1? {
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchLatestPublishedLocationTrackChangeTimesBetween(startMoment, endMoment, branch)
            .mapNotNull { (id, changeTime) -> locationTrackDao.fetchOfficialVersionAtMoment(branch, id, changeTime) }
            .takeIf { versions -> versions.isNotEmpty() }
            ?.let(locationTrackService::getManyWithGeometries)
            ?.let { tracksAndGeoms ->
                val branchTrackIds = designBranchTrackIds(branch, tracksAndGeoms)
                tracksAndGeoms.filter(filterTracks(nameFilter, branchTrackIds))
            }
            ?.let { tracksAndGeoms ->
                createExtLocationTracks(branch, endMoment, coordinateSystem, tracksAndGeoms, trackNumberOidFilter)
            }
            ?.takeIf { it.isNotEmpty() }
            ?.let { extTracks ->
                ExtModifiedLocationTrackCollectionResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    locationTrackCollection = extTracks,
                )
            } ?: layoutAssetCollectionWasUnmodified<LocationTrack>(publications)
    }

    private fun createExtLocationTracks(
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
        tracksAndGeoms: List<Pair<LocationTrack, LocationTrackGeometry>>,
        trackNumberOidFilter: ExtOidV1<LayoutTrackNumber>?,
    ): List<ExtLocationTrackV1> {
        return getLocationTrackData(branch, moment, tracksAndGeoms)
            .let { data -> trackNumberOidFilter?.let { data.filter { d -> d.trackNumberOid == it.value } } ?: data }
            .parallelStream()
            .map { data -> createExtLocationTrack(data, coordinateSystem) }
            .toList()
    }

    private fun createExtLocationTrack(data: LocationTrackData, coordinateSystem: Srid): ExtLocationTrackV1 {
        val toEndPoint = { p: IPoint -> toExtAddressPoint(p, data.geocodingContext, coordinateSystem) }
        return ExtLocationTrackV1(
            locationTrackOid = ExtOidV1(data.oid),
            officialLocationTrackOid = data.officialOid?.let(::ExtOidV1),
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
        val officialOid: Oid<LocationTrack>?,
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
        officialOid: Oid<LocationTrack>?,
        track: LocationTrack,
        geometry: LocationTrackGeometry,
    ): LocationTrackData =
        LocationTrackData(
            oid = oid,
            officialOid = officialOid,
            track = track,
            geometry = geometry,
            trackNumberOid = oidLookupWithInheritance(trackNumberDao, branch, track.trackNumberId),
            trackNumber =
                trackNumberDao.getOfficialAtMoment(branch, track.trackNumberId, moment)
                    ?: throwTrackNumberNotFound(branch, moment, track.trackNumberId),
            geocodingContext =
                produceIf(track.exists) {
                    geocodingService.getGeocodingContextAtMoment(branch, track.trackNumberId, moment)
                },
        )

    private inline fun <reified T : LayoutAsset<T>> oidLookupWithInheritance(
        dao: IExternalIdDao<T>,
        branch: LayoutBranch,
        id: IntId<T>,
    ): Oid<T> = dao.fetchExternalIdsWithInheritance(branch, listOf(id))[id]?.oid ?: throwOidNotFound(branch, id)

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
        val officialExtIdsIfBranch =
            if (branch == LayoutBranch.main) mapOf()
            else locationTrackDao.fetchExternalIds(LayoutBranch.main, locationTrackIds)
        val trackNumberExtIds = trackNumberDao.fetchExternalIdsWithInheritance(branch, distinctTrackNumberIds)
        return tracksAndGeoms.map { (track, geom) ->
            LocationTrackData(
                officialOid = officialExtIdsIfBranch[track.id]?.oid,
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

    private fun branchByDesignOid(designOid: ExtOidV1<LayoutDesign>?) =
        branchByDesignOid(layoutDesignService, designOid)

    private fun designBranchTrackIds(
        branch: LayoutBranch,
        tracksAndGeoms: List<Pair<LocationTrack, LocationTrackGeometry>>,
    ): Set<IntId<LocationTrack>>? =
        if (branch == LayoutBranch.main) null
        else locationTrackDao.fetchExternalIds(branch, tracksAndGeoms.map { (track, _) -> track.id as IntId }).keys
}

private fun filterTracks(
    nameFilter: String?,
    designBranchTrackIds: Set<IntId<LocationTrack>>?,
): (trackAndGeometry: Pair<LocationTrack, LocationTrackGeometry>) -> Boolean = { (track) ->
    (nameFilter == null || track.name.contains(nameFilter, ignoreCase = true)) &&
        (designBranchTrackIds == null || track.id in designBranchTrackIds)
}
