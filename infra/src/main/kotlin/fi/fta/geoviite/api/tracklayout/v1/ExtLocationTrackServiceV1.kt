package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.AddressFilter
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
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
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val geocodingDao: GeocodingDao,
    private val alignmentDao: LayoutAlignmentDao,
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
        return if (publications.areDifferent()) {
            createLocationTrackModificationResponse(oid, idLookup(oid), publications, coordinateSystem ?: LAYOUT_SRID)
        } else {
            publicationsAreTheSame(trackLayoutVersionFrom)
        }
    }

    fun getExtLocationTrackGeometry(
        oid: Oid<LocationTrack>,
        trackLayoutVersion: Uuid<Publication>? = null,
        extResolution: ExtResolutionV1? = null,
        coordinateSystem: Srid? = null,
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1? = null,
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1? = null,
    ): ExtLocationTrackGeometryResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        return createGeometryResponse(
            oid,
            publication,
            resolution = extResolution?.toResolution() ?: Resolution.ONE_METER,
            coordinateSystem = coordinateSystem ?: LAYOUT_SRID,
            addressFilter = createAddressFilter(addressFilterStart, addressFilterEnd),
        )
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
        return locationTrackDao.getOfficialAtMoment(branch, id, moment)?.let { locationTrack ->
            ExtLocationTrackResponseV1(
                trackLayoutVersion = publication.uuid,
                coordinateSystem = coordinateSystem,
                locationTrack = createExtLocationTrack(oid, locationTrack, branch, moment, coordinateSystem),
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
            ?.let(locationTrackDao::fetch)
            ?.let { track ->
                ExtModifiedLocationTrackResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    // TODO: The create should no longer need the branch: fetch track numbers etc. through a local cache
                    locationTrack = createExtLocationTrack(oid, track, branch, endMoment, coordinateSystem),
                )
            } ?: layoutAssetVersionsAreTheSame(id, publications)
    }

    private fun createLocationTrackCollectionResponse(
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtLocationTrackCollectionResponseV1 {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val locationTracks = locationTrackDao.listOfficialAtMoment(branch, moment).filter { it.exists }
        return ExtLocationTrackCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = coordinateSystem,
            locationTrackCollection = createExtLocationTracks(branch, moment, coordinateSystem, locationTracks),
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
            ?.let(locationTrackDao::fetchMany)
            ?.let { modifiedLocationTracks ->
                ExtModifiedLocationTrackCollectionResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    locationTrackCollection =
                        createExtLocationTracks(branch, endMoment, coordinateSystem, modifiedLocationTracks),
                )
            } ?: layoutAssetCollectionWasUnmodified<LocationTrack>(publications)
    }

    private fun createExtLocationTrack(
        oid: Oid<LocationTrack>,
        track: LocationTrack,
        // TODO: Pass in the required stuff instead of branch/moment -> reusable in collection function as well
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
    ): ExtLocationTrackV1 {
        val trackNumberName =
            layoutTrackNumberDao
                .fetchOfficialVersionAtMoment(branch, track.trackNumberId, moment)
                ?.let(layoutTrackNumberDao::fetch)
                ?.number
                ?: throw ExtTrackNumberNotFoundV1(
                    "track number was not found for branch=$branch, trackNumberId=${track.trackNumberId}, moment=$moment"
                )

        val trackNumberOid =
            layoutTrackNumberDao.fetchExternalId(branch, track.trackNumberId)?.oid
                ?: throw ExtOidNotFoundExceptionV1(
                    "track number oid was not found, branch=$branch, trackNumberId=${track.trackNumberId}"
                )

        val (startLocation, endLocation) =
            locationTrackService
                .getStartAndEndAtMoment(branch, listOf(track.id as IntId), moment)
                .first()
                .let { startAndEnd -> layoutAlignmentStartAndEndToCoordinateSystem(coordinateSystem, startAndEnd) }
                .let { startAndEnd -> startAndEnd.start to startAndEnd.end }

        return ExtLocationTrackV1(
            locationTrackOid = oid,
            locationTrackName = track.name,
            locationTrackType = ExtLocationTrackTypeV1.of(track.type),
            locationTrackState = ExtLocationTrackStateV1.of(track.state),
            locationTrackDescription = track.description,
            locationTrackOwner = locationTrackService.getLocationTrackOwner(track.ownerId).name,
            startLocation = startLocation?.let(::ExtAddressPointV1),
            endLocation = endLocation?.let(::ExtAddressPointV1),
            trackNumberName = trackNumberName,
            trackNumberOid = trackNumberOid,
        )
    }

    private fun createExtLocationTracks(
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
        locationTracks: List<LocationTrack>,
    ): List<ExtLocationTrackV1> {
        val locationTrackIds = locationTracks.map { locationTrack -> locationTrack.id as IntId }
        val distinctTrackNumberIds = locationTracks.map { locationTrack -> locationTrack.trackNumberId }.distinct()

        val trackNumbers =
            layoutTrackNumberDao.getManyOfficialAtMoment(branch, distinctTrackNumberIds, moment).associateBy {
                trackNumber ->
                trackNumber.id
            }

        val externalLocationTrackIds = locationTrackDao.fetchExternalIds(branch, locationTrackIds)
        val externalTrackNumberIds = layoutTrackNumberDao.fetchExternalIds(branch, distinctTrackNumberIds)

        val locationTrackStartsAndEnds = locationTrackService.getStartAndEndAtMoment(branch, locationTrackIds, moment)

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
                            "branch=$branch, trackNumberId=${locationTrack.trackNumberId}, moment=$moment"
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

    private fun createGeometryResponse(
        oid: Oid<LocationTrack>,
        publication: Publication,
        resolution: Resolution,
        coordinateSystem: Srid,
        addressFilter: AddressFilter,
    ): ExtLocationTrackGeometryResponseV1? {
        val locationTrackId =
            locationTrackDao.lookupByExternalId(oid)?.id
                ?: throw ExtOidNotFoundExceptionV1("location track lookup failed for oid=$oid")

        return locationTrackDao
            .fetchOfficialVersionAtMoment(publication.layoutBranch.branch, locationTrackId, publication.publicationTime)
            ?.let(locationTrackDao::fetch)
            // Deleted tracks have no geometry in API since there's no guarantee of geocodable addressing
            ?.takeIf { it.exists }
            ?.let { locationTrack ->
                val filteredAddressPoints =
                    if (addressFilter.start == null && addressFilter.end == null) {
                        // Prefer using cache when address filter is unassigned
                        val geocodingContextCacheKey =
                            geocodingDao.getLayoutGeocodingContextCacheKey(
                                publication.layoutBranch.branch,
                                locationTrack.trackNumberId,
                                publication.publicationTime,
                            ) ?: throw ExtGeocodingFailedV1("could not get geocoding context cache key")

                        geocodingService.getAddressPoints(
                            geocodingContextCacheKey,
                            locationTrack.getVersionOrThrow(),
                            resolution,
                        )
                    } else {
                        geocodingService
                            .getGeocodingContextAtMoment(
                                publication.layoutBranch.branch,
                                locationTrack.trackNumberId,
                                publication.publicationTime,
                            )
                            ?.getAddressPoints(
                                alignmentDao.fetch(requireNotNull(locationTrack.version)),
                                resolution,
                                addressFilter,
                            )
                    }

                ExtLocationTrackGeometryResponseV1(
                    trackLayoutVersion = publication.uuid,
                    locationTrackOid = oid,
                    coordinateSystem = coordinateSystem,
                    trackInterval =
                        // Address points are null for example in case when the user provided
                        // address filter is outside the track boundaries.
                        filteredAddressPoints?.let { addressPoints ->
                            ExtCenterLineTrackIntervalV1(
                                startAddress = addressPoints.startPoint.address.toString(),
                                endAddress = addressPoints.endPoint.address.toString(),
                                addressPoints =
                                    filteredAddressPoints.allPoints.map { point ->
                                        toExtAddressPoint(point, coordinateSystem)
                                    },
                            )
                        },
                )
            }
    }
}
