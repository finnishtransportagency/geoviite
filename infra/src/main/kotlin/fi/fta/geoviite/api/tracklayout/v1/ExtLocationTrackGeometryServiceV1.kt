package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.AddressFilter
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtLocationTrackGeometryServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val geocodingDao: GeocodingDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
) {
    fun getExtLocationTrackGeometry(
        oid: ExtOidV1<LocationTrack>,
        layoutVersion: ExtLayoutVersionV1?,
        extResolution: ExtResolutionV1?,
        extCoordinateSystem: ExtSridV1?,
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1?,
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1?,
    ): ExtLocationTrackGeometryResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        val resolution = resolution(extResolution)
        val coordinateSystem = coordinateSystem(extCoordinateSystem)
        val addressFilter = createAddressFilter(addressFilterStart, addressFilterEnd)
        return createGeometryResponse(oid.value, publication, resolution, coordinateSystem, addressFilter)
    }

    fun getExtLocationTrackGeometryModifications(
        oid: ExtOidV1<LocationTrack>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extResolution: ExtResolutionV1?,
        extCoordinateSystem: ExtSridV1?,
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1?,
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1?,
    ): ExtLocationTrackModifiedGeometryResponseV1? {
        val publications = publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value)
        // Lookup before change check to produce consistent error if oid is not found
        val id = idLookup(locationTrackDao, oid.value)
        val resolution = resolution(extResolution)
        val coordinateSystem = coordinateSystem(extCoordinateSystem)
        val addressFilter = createAddressFilter(addressFilterStart, addressFilterEnd)
        return if (publications.areDifferent()) {
            createGeometryModificationResponse(oid.value, id, publications, resolution, coordinateSystem, addressFilter)
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    private fun createGeometryModificationResponse(
        oid: Oid<LocationTrack>,
        id: IntId<LocationTrack>,
        publications: PublicationComparison,
        resolution: Resolution,
        coordinateSystem: Srid,
        addressFilter: AddressFilter,
    ): ExtLocationTrackModifiedGeometryResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedLocationTrackGeomsBetween(id, startMoment, endMoment)
            ?.map(locationTrackDao::fetch)
            ?.takeIf { (oldTrack, newTrack) -> oldTrack?.exists == true || newTrack.exists }
            ?.let { (oldTrack, newTrack) ->
                val oldPoints =
                    oldTrack
                        ?.takeIf { it.exists }
                        ?.let { track -> getAddressPoints(branch, startMoment, track, resolution, addressFilter) }
                val newPoints =
                    newTrack
                        .takeIf { it.exists }
                        ?.let { track -> getAddressPoints(branch, endMoment, track, resolution, addressFilter) }
                val oldGeometry = oldTrack?.version?.let { alignmentDao.fetch(it) }
                val newGeometry = newTrack.getVersionOrThrow().let { alignmentDao.fetch(it) }
                println(
                    "oldState=${oldTrack?.state} geocoded=${oldPoints != null} oldGeom=${oldGeometry?.segments?.map { it.geometry.id }}"
                )
                println(
                    "newState=${newTrack.state} geocoded=${newPoints != null} newGeom=${newGeometry.segments.map { it.geometry.id }}"
                )
                ExtLocationTrackModifiedGeometryResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    locationTrackOid = ExtOidV1(oid),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackIntervals =
                        createModifiedCenterLineIntervals(oldPoints, newPoints, coordinateSystem) { start, end ->
                            isGeometryChanged(start, end, oldGeometry, newGeometry).also {
                                println("interaval changed: result=$it start=$start end=$end")
                            }
                        },
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
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val id = idLookup(locationTrackDao, oid)
        return locationTrackDao
            .fetchOfficialVersionAtMoment(branch, id, moment)
            ?.let(locationTrackDao::fetch)
            // Deleted tracks have no geometry in API since there's no guarantee of geocodable addressing
            ?.takeIf { it.exists }
            ?.let { locationTrack ->
                val filteredAddressPoints = getAddressPoints(branch, moment, locationTrack, resolution, addressFilter)
                ExtLocationTrackGeometryResponseV1(
                    layoutVersion = ExtLayoutVersionV1(publication),
                    locationTrackOid = ExtOidV1(oid),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackInterval =
                        // Address points are null for example in case when the user provided
                        // address filter is outside the track boundaries.
                        filteredAddressPoints?.let { addressPoints -> toExtInterval(addressPoints, coordinateSystem) },
                )
            }
    }

    private fun getAddressPoints(
        branch: LayoutBranch,
        moment: Instant,
        track: LocationTrack,
        resolution: Resolution,
        addressFilter: AddressFilter,
    ): AlignmentAddresses<LocationTrackM>? =
        if (!track.exists) {
            // Deleted tracks have no geometry in API since there's no guarantee of geocodable addressing
            null
        } else if (addressFilter.start == null && addressFilter.end == null) {
            // Prefer using cached (full) address list when address filter is unassigned
            val geocodingContextCacheKey =
                geocodingDao.getLayoutGeocodingContextCacheKey(branch, track.trackNumberId, moment)
                    ?: throw ExtGeocodingFailedV1("could not get geocoding context cache key")

            geocodingService.getAddressPoints(geocodingContextCacheKey, track.getVersionOrThrow(), resolution)
        } else {
            // When filter is assigned, compute the desired interval on the fly
            val geometry = alignmentDao.fetch(requireNotNull(track.version))
            val context =
                geocodingService.getGeocodingContextAtMoment(branch, track.trackNumberId, moment)
                    ?: throw ExtGeocodingFailedV1("could not calculate address points")
            context.getAddressPoints(geometry, resolution, addressFilter)
        }
}
