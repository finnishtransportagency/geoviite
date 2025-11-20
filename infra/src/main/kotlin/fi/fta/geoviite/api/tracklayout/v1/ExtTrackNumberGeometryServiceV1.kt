package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.AddressFilter
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTrackNumberGeometryServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val trackNumberDao: LayoutTrackNumberDao,
) {
    fun getExtTrackNumberGeometry(
        oid: Oid<LayoutTrackNumber>,
        trackLayoutVersion: Uuid<Publication>?,
        extResolution: ExtResolutionV1?,
        coordinateSystem: Srid?,
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1?,
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1?,
    ): ExtTrackNumberGeometryResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        val resolution = extResolution?.toResolution() ?: Resolution.ONE_METER
        val coordinateSystem = coordinateSystem ?: LAYOUT_SRID
        val addressFilter = createAddressFilter(addressFilterStart, addressFilterEnd)
        return createGeometryResponse(oid, publication, resolution, coordinateSystem, addressFilter)
    }

    fun getExtTrackNumberGeometryModifications(
        oid: Oid<LayoutTrackNumber>,
        trackLayoutVersionFrom: Uuid<Publication>,
        trackLayoutVersionTo: Uuid<Publication>?,
        extResolution: ExtResolutionV1? = null,
        coordinateSystem: Srid? = null,
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1? = null,
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1? = null,
    ): ExtTrackNumberModifiedGeometryResponseV1? {
        val publications = publicationService.getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo)
        // Lookup before change check to produce consistent error if oid is not found
        val id = idLookup(trackNumberDao, oid)
        val resolution = extResolution?.toResolution() ?: Resolution.ONE_METER
        val coordinateSystem = coordinateSystem ?: LAYOUT_SRID
        val addressFilter = createAddressFilter(addressFilterStart, addressFilterEnd)
        return if (publications.areDifferent()) {
            createGeometryModificationResponse(oid, id, publications, resolution, coordinateSystem, addressFilter)
        } else {
            publicationsAreTheSame(trackLayoutVersionFrom)
        }
    }

    private fun createGeometryModificationResponse(
        oid: Oid<LayoutTrackNumber>,
        id: IntId<LayoutTrackNumber>,
        publications: PublicationComparison,
        resolution: Resolution,
        coordinateSystem: Srid,
        addressFilter: AddressFilter,
    ): ExtTrackNumberModifiedGeometryResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedTrackNumberGeomsBetween(id, startMoment, endMoment)
            ?.let { (old, new) -> old?.let(trackNumberDao::fetch) to trackNumberDao.fetch(new) }
            ?.takeIf { (old, new) -> old?.exists == true || new.exists }
            ?.let { (oldTrackNumber, newTrackNumber) ->
                val oldPoints =
                    oldTrackNumber
                        ?.takeIf { it.exists }
                        ?.let { _ -> getAddressPoints(branch, startMoment, id, resolution, addressFilter) }
                val newPoints =
                    newTrackNumber
                        .takeIf { it.exists }
                        ?.let { _ -> getAddressPoints(branch, endMoment, id, resolution, addressFilter) }
                ExtTrackNumberModifiedGeometryResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    trackNumberOid = oid,
                    coordinateSystem = coordinateSystem,
                    trackIntervals = createModifiedCenterLineIntervals(oldPoints, newPoints, coordinateSystem),
                )
            }
    }

    private fun createGeometryResponse(
        oid: Oid<LayoutTrackNumber>,
        publication: Publication,
        resolution: Resolution,
        coordinateSystem: Srid,
        addressFilter: AddressFilter,
    ): ExtTrackNumberGeometryResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val trackNumberId = idLookup(trackNumberDao, oid)
        return trackNumberDao
            .fetchOfficialVersionAtMoment(branch, trackNumberId, moment)
            ?.let(trackNumberDao::fetch)
            // Deleted track numbers have no geometry in API since there's no guarantee of geocodable addressing
            ?.takeIf { it.exists }
            ?.let { _ ->
                val filteredAddressPoints = getAddressPoints(branch, moment, trackNumberId, resolution, addressFilter)
                ExtTrackNumberGeometryResponseV1(
                    trackLayoutVersion = publication.uuid,
                    trackNumberOid = oid,
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

    private fun getAddressPoints(
        branch: LayoutBranch,
        moment: Instant,
        trackNumberId: IntId<LayoutTrackNumber>,
        resolution: Resolution,
        addressFilter: AddressFilter,
    ): AlignmentAddresses<ReferenceLineM>? =
        geocodingService
            .getGeocodingContextAtMoment(branch, trackNumberId, moment)
            ?.getReferenceLineAddressesWithResolution(resolution, addressFilter)
}
