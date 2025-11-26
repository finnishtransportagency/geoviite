package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.AddressFilter
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.IAlignment
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
        oid: ExtOidV1<LayoutTrackNumber>,
        layoutVersion: ExtLayoutVersionV1?,
        extResolution: ExtResolutionV1?,
        extCoordinateSystem: ExtSridV1?,
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1?,
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1?,
    ): ExtTrackNumberGeometryResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        val coordinateSystem = coordinateSystem(extCoordinateSystem)
        val resolution = extResolution?.toResolution() ?: Resolution.ONE_METER
        val addressFilter = createAddressFilter(addressFilterStart, addressFilterEnd)
        return createGeometryResponse(oid.value, publication, resolution, coordinateSystem, addressFilter)
    }

    fun getExtTrackNumberGeometryModifications(
        oid: ExtOidV1<LayoutTrackNumber>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extResolution: ExtResolutionV1?,
        extCoordinateSystem: ExtSridV1?,
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1?,
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1?,
    ): ExtTrackNumberModifiedGeometryResponseV1? {
        val publications = publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value)
        // Lookup before change check to produce consistent error if oid is not found
        val id = idLookup(trackNumberDao, oid.value)
        val coordinateSystem = coordinateSystem(extCoordinateSystem)
        val resolution = extResolution?.toResolution() ?: Resolution.ONE_METER
        val addressFilter = createAddressFilter(addressFilterStart, addressFilterEnd)
        return if (publications.areDifferent()) {
            createGeometryModificationResponse(oid.value, id, publications, resolution, coordinateSystem, addressFilter)
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
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
            ?.map(trackNumberDao::fetch)
            ?.takeIf { (oldTn, newTn) -> oldTn?.exists == true || newTn.exists }
            ?.let { (oldTn, newTn) ->
                val (oldGeometry, oldPoints) =
                    getAlignmentAndAddressPoints(branch, startMoment, oldTn, resolution, addressFilter)
                val (newGeometry, newPoints) =
                    getAlignmentAndAddressPoints(branch, endMoment, newTn, resolution, addressFilter)
                ExtTrackNumberModifiedGeometryResponseV1(
                    trackLayoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    trackLayoutVersionTo = ExtLayoutVersionV1(publications.to),
                    trackNumberOid = ExtOidV1(oid),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackIntervals =
                        createModifiedCenterLineIntervals(oldPoints, newPoints, coordinateSystem) { start, end ->
                            isGeometryChanged(start, end, oldGeometry, newGeometry)
                        },
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
                    trackLayoutVersion = ExtLayoutVersionV1(publication),
                    trackNumberOid = ExtOidV1(oid),
                    coordinateSystem = ExtSridV1(coordinateSystem),
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

    private fun getAlignmentAndAddressPoints(
        branch: LayoutBranch,
        moment: Instant,
        trackNumber: LayoutTrackNumber?,
        resolution: Resolution,
        addressFilter: AddressFilter,
    ): Pair<IAlignment<ReferenceLineM>?, AlignmentAddresses<ReferenceLineM>?> =
        trackNumber
            ?.takeIf { it.exists }
            ?.let { tn -> tn.id as IntId }
            ?.let { id ->
                geocodingService.getGeocodingContextAtMoment(branch, id, moment)?.let { ctx ->
                    ctx.referenceLineGeometry to ctx.getReferenceLineAddressesWithResolution(resolution, addressFilter)
                }
            } ?: (null to null)
}
