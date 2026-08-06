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
import fi.fta.geoviite.infra.tracklayout.DbReferenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTrackNumberServiceV1
@Autowired
constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val publicationDao: PublicationDao,
    private val publicationService: PublicationService,
    private val geocodingService: GeocodingService,
    private val layoutDesignService: LayoutDesignService,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtTrackNumberCollection(
        designOid: ExtOidV1<LayoutDesign>?,
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
        trackNumberFilter: String? = null,
    ): ExtTrackNumberCollectionResponseV1 {
        val branch = branchByDesignOid(layoutDesignService, designOid)
        val publication = publicationService.getPublicationByUuidOrLatest(branch, layoutVersion?.value)
        return createTrackNumberCollectionResponse(
            publication,
            branch,
            coordinateSystem(extCoordinateSystem),
            trackNumberFilter,
        )
    }

    fun getExtTrackNumberCollectionModifications(
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        designOid: ExtOidV1<LayoutDesign>?,
        extCoordinateSystem: ExtSridV1?,
        trackNumberFilter: String? = null,
    ): ExtModifiedTrackNumberCollectionResponseV1? {
        val branch = branchByDesignOid(layoutDesignService, designOid)
        val publications =
            publicationService.getPublicationsToCompare(
                layoutVersionFrom.value,
                layoutVersionTo?.value,
                branch = branch,
            )
        return if (publications.areDifferent()) {
            createTrackNumberCollectionModificationResponse(
                publications,
                branch,
                coordinateSystem(extCoordinateSystem),
                trackNumberFilter,
            )
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    fun getExtTrackNumber(
        oid: ExtOidV1<LayoutTrackNumber>,
        layoutVersion: ExtLayoutVersionV1?,
        designOid: ExtOidV1<LayoutDesign>?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtTrackNumberResponseV1? {
        val branch = branchByDesignOid(layoutDesignService, designOid)
        val publication = publicationService.getPublicationByUuidOrLatest(branch, layoutVersion?.value)
        val id = idLookup(trackNumberDao, oid.value)
        val oids = branchOids(trackNumberDao, branch, oid.value, id)
        return createTrackNumberResponse(oids, id, publication, branch, coordinateSystem(extCoordinateSystem))
    }

    fun getExtTrackNumberModifications(
        oid: ExtOidV1<LayoutTrackNumber>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        designOid: ExtOidV1<LayoutDesign>?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedTrackNumberResponseV1? {
        val branch = branchByDesignOid(layoutDesignService, designOid)
        val publications =
            publicationService.getPublicationsToCompare(
                layoutVersionFrom.value,
                layoutVersionTo?.value,
                branch = branch,
            )
        // Lookup before change check to produce consistent error if oid is not found
        val id = idLookup(trackNumberDao, oid.value)
        val oids = branchOids(trackNumberDao, branch, oid.value, id)
        return if (publications.areDifferent()) {
            createTrackNumberModificationResponse(oids, id, publications, branch, coordinateSystem(extCoordinateSystem))
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    private fun createTrackNumberResponse(
        oids: BranchOidsV1<LayoutTrackNumber>,
        id: IntId<LayoutTrackNumber>,
        publication: Publication,
        branch: LayoutBranch,
        coordinateSystem: Srid,
    ): ExtTrackNumberResponseV1? {
        val moment = publication.publicationTime
        return trackNumberService.getOfficialWithGeometryAtMoment(branch, id, moment)?.let { (trackNumber, geometry) ->
            val data = getTrackNumberData(branch, moment, oids, trackNumber, geometry)
            ExtTrackNumberResponseV1(
                layoutVersion = ExtLayoutVersionV1(publication),
                coordinateSystem = ExtSridV1(coordinateSystem),
                trackNumber = createExtTrackNumber(data, coordinateSystem),
            )
        }
    }

    private fun createTrackNumberModificationResponse(
        oids: BranchOidsV1<LayoutTrackNumber>,
        id: IntId<LayoutTrackNumber>,
        publications: PublicationComparison,
        branch: LayoutBranch,
        coordinateSystem: Srid,
    ): ExtModifiedTrackNumberResponseV1? {
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedTrackNumberBetween(id, startMoment, endMoment, branch)
            ?.let(trackNumberService::getWithGeometry)
            ?.let { (trackNumber, referenceLineGeometry) ->
                val data = getTrackNumberData(branch, endMoment, oids, trackNumber, referenceLineGeometry)
                ExtModifiedTrackNumberResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackNumber = createExtTrackNumber(data, coordinateSystem),
                )
            } ?: layoutAssetVersionsAreTheSame(id, publications)
    }

    private fun createTrackNumberCollectionResponse(
        publication: Publication,
        branch: LayoutBranch,
        coordinateSystem: Srid,
        tnFilter: String?,
    ): ExtTrackNumberCollectionResponseV1 {
        val moment = publication.publicationTime
        val trackNumbers =
            trackNumberService.listOfficialWithGeometryAtMoment(branch, moment).filter { (tn, _) ->
                tn.exists && (tnFilter == null || tn.number.contains(tnFilter, ignoreCase = true))
            }
        val filteredTrackNumbers = filterToDesignBranchTrackNumbers(branch, trackNumbers)
        return ExtTrackNumberCollectionResponseV1(
            layoutVersion = ExtLayoutVersionV1(publication.uuid),
            coordinateSystem = ExtSridV1(coordinateSystem),
            trackNumberCollection = createExtTrackNumbers(branch, moment, coordinateSystem, filteredTrackNumbers),
        )
    }

    private fun createTrackNumberCollectionModificationResponse(
        publications: PublicationComparison,
        branch: LayoutBranch,
        coordinateSystem: Srid,
        tnFilter: String?,
    ): ExtModifiedTrackNumberCollectionResponseV1? {
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedTrackNumbersBetween(startMoment, endMoment, branch)
            .takeIf { versions -> versions.isNotEmpty() }
            ?.let(trackNumberService::getManyWithGeometries)
            ?.let { all ->
                tnFilter?.let { all.filter { (tn, _) -> tn.number.contains(it, ignoreCase = true) } } ?: all
            }
            ?.let { all -> filterToDesignBranchTrackNumbers(branch, all) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { trackNumbers ->
                ExtModifiedTrackNumberCollectionResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackNumberCollection = createExtTrackNumbers(branch, endMoment, coordinateSystem, trackNumbers),
                )
            } ?: layoutAssetCollectionWasUnmodified<LayoutTrackNumber>(publications)
    }

    /**
     * A track number with no OID in a design branch is not part of the design's externally published state and is not
     * listed by the design's collection routes.
     */
    private fun filterToDesignBranchTrackNumbers(
        branch: LayoutBranch,
        trackNumbers: List<Pair<LayoutTrackNumber, DbReferenceLineGeometry>>,
    ): List<Pair<LayoutTrackNumber, DbReferenceLineGeometry>> =
        if (branch == LayoutBranch.main) trackNumbers
        else {
            val branchTrackNumberIds =
                trackNumberDao.fetchExternalIds(branch, trackNumbers.map { (tn, _) -> tn.id as IntId }).keys
            trackNumbers.filter { (tn, _) -> tn.id in branchTrackNumberIds }
        }

    private fun createExtTrackNumbers(
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
        trackNumbers: List<Pair<LayoutTrackNumber, DbReferenceLineGeometry>>,
    ): List<ExtTrackNumberV1> {
        return getTrackNumberData(branch, moment, trackNumbers)
            .parallelStream()
            .map { data -> createExtTrackNumber(data, coordinateSystem) }
            .toList()
    }

    private fun createExtTrackNumber(data: TrackNumberData, coordinateSystem: Srid): ExtTrackNumberV1 {
        val toEndPoint = { p: IPoint -> toExtAddressPoint(p, data.geocodingContext, coordinateSystem) }
        return ExtTrackNumberV1(
            trackNumberOid = ExtOidV1(data.oid),
            officialTrackNumberOid = data.officialOid?.let(::ExtOidV1),
            trackNumber = data.trackNumber.number,
            trackNumberDescription = data.trackNumber.description,
            trackNumberState = data.trackNumber.state.let(ExtTrackNumberStateV1::of),
            startLocation = data.geometry?.start?.let(toEndPoint),
            endLocation = data.geometry?.end?.let(toEndPoint),
        )
    }

    data class TrackNumberData(
        val oid: Oid<LayoutTrackNumber>,
        val officialOid: Oid<LayoutTrackNumber>?,
        val trackNumber: LayoutTrackNumber,
        // Note: the geocoding context has the same geometry, but this one can exist for deteled
        // TrackNumbers as well, unlike the geocoding context
        val geometry: ReferenceLineGeometry?,
        val geocodingContext: GeocodingContext<ReferenceLineM>?,
    )

    private fun getTrackNumberData(
        branch: LayoutBranch,
        moment: Instant,
        oids: BranchOidsV1<LayoutTrackNumber>,
        trackNumber: LayoutTrackNumber,
        referenceLineGeometry: DbReferenceLineGeometry,
    ): TrackNumberData {
        val id = trackNumber.id as IntId
        val geocodingContext = geocodingService.getGeocodingContextAtMoment(branch, id, moment)
        return TrackNumberData(oids.oid, oids.officialOid, trackNumber, referenceLineGeometry, geocodingContext)
    }

    private fun getTrackNumberData(
        branch: LayoutBranch,
        moment: Instant,
        trackNumbers: List<Pair<LayoutTrackNumber, DbReferenceLineGeometry>>,
    ): List<TrackNumberData> {
        val getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment)
        val trackNumberIds = trackNumbers.map { (tn, _) -> tn.id as IntId }
        val extIds = trackNumberDao.fetchExternalIds(branch, trackNumberIds)
        val officialExtIdsIfBranch =
            if (branch == LayoutBranch.main) mapOf()
            else trackNumberDao.fetchExternalIds(LayoutBranch.main, trackNumberIds)
        return trackNumbers.map { (trackNumber, referenceLineGeometry) ->
            val id = trackNumber.id as IntId
            val oid = extIds[id]?.oid ?: throwOidNotFound(branch, id)
            TrackNumberData(
                oid,
                officialExtIdsIfBranch[id]?.oid,
                trackNumber,
                referenceLineGeometry,
                getGeocodingContext(id),
            )
        }
    }
}
