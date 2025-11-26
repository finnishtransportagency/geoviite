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
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@GeoviiteService
class ExtTrackNumberServiceV1
@Autowired
constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val publicationDao: PublicationDao,
    private val publicationService: PublicationService,
    private val geocodingService: GeocodingService,
    private val referenceLineDao: ReferenceLineDao,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtTrackNumberCollection(
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtTrackNumberCollectionResponseV1 {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        return createTrackNumberCollectionResponse(publication, coordinateSystem(extCoordinateSystem))
    }

    fun getExtTrackNumberCollectionModifications(
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedTrackNumberCollectionResponseV1? {
        val publications = publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value)
        return if (publications.areDifferent()) {
            createTrackNumberCollectionModificationResponse(publications, coordinateSystem(extCoordinateSystem))
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    fun getExtTrackNumber(
        oid: ExtOidV1<LayoutTrackNumber>,
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtTrackNumberResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        val id = idLookup(trackNumberDao, oid.value)
        return createTrackNumberResponse(oid.value, id, publication, coordinateSystem(extCoordinateSystem))
    }

    fun getExtTrackNumberModifications(
        oid: ExtOidV1<LayoutTrackNumber>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedTrackNumberResponseV1? {
        val publications = publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value)
        // Lookup before change check to produce consistent error if oid is not found
        val id = idLookup(trackNumberDao, oid.value)
        return if (publications.areDifferent()) {
            createTrackNumberModificationResponse(oid.value, id, publications, coordinateSystem(extCoordinateSystem))
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    private fun createTrackNumberResponse(
        oid: Oid<LayoutTrackNumber>,
        id: IntId<LayoutTrackNumber>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtTrackNumberResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        return trackNumberDao.getOfficialAtMoment(branch, id, moment)?.let { trackNumber ->
            val data = getTrackNumberData(branch, moment, oid, trackNumber)
            ExtTrackNumberResponseV1(
                layoutVersion = ExtLayoutVersionV1(publication),
                coordinateSystem = ExtSridV1(coordinateSystem),
                trackNumber = createExtTrackNumber(data, coordinateSystem),
            )
        }
    }

    private fun createTrackNumberModificationResponse(
        oid: Oid<LayoutTrackNumber>,
        id: IntId<LayoutTrackNumber>,
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedTrackNumberResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedTrackNumberBetween(id, startMoment, endMoment)
            ?.let(trackNumberDao::fetch)
            ?.let { trackNumber ->
                val data = getTrackNumberData(branch, endMoment, oid, trackNumber)
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
        coordinateSystem: Srid,
    ): ExtTrackNumberCollectionResponseV1 {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val trackNumbers = trackNumberDao.listOfficialAtMoment(branch, moment).filter { it.exists }
        return ExtTrackNumberCollectionResponseV1(
            layoutVersion = ExtLayoutVersionV1(publication.uuid),
            coordinateSystem = ExtSridV1(coordinateSystem),
            trackNumberCollection = createExtTrackNumbers(branch, moment, coordinateSystem, trackNumbers),
        )
    }

    private fun createTrackNumberCollectionModificationResponse(
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedTrackNumberCollectionResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedTrackNumbersBetween(startMoment, endMoment)
            .takeIf { versions -> versions.isNotEmpty() }
            ?.let(trackNumberDao::fetchMany)
            ?.let { trackNumbers ->
                ExtModifiedTrackNumberCollectionResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackNumberCollection = createExtTrackNumbers(branch, endMoment, coordinateSystem, trackNumbers),
                )
            } ?: layoutAssetCollectionWasUnmodified<LayoutTrackNumber>(publications)
    }

    private fun createExtTrackNumbers(
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
        trackNumbers: List<LayoutTrackNumber>,
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
            trackNumber = data.trackNumber.number,
            trackNumberDescription = data.trackNumber.description,
            trackNumberState = data.trackNumber.state.let(ExtTrackNumberStateV1::of),
            startLocation = data.geometry?.start?.let(toEndPoint),
            endLocation = data.geometry?.end?.let(toEndPoint),
        )
    }

    data class TrackNumberData(
        val oid: Oid<LayoutTrackNumber>,
        val trackNumber: LayoutTrackNumber,
        // Note: the geocoding context has the same geometry, but this one can exist for deteled
        // TrackNumbers as well, unlike the geocoding context
        val geometry: LayoutAlignment?,
        val geocodingContext: GeocodingContext<ReferenceLineM>?,
    )

    private fun getTrackNumberData(
        branch: LayoutBranch,
        moment: Instant,
        oid: Oid<LayoutTrackNumber>,
        trackNumber: LayoutTrackNumber,
    ): TrackNumberData {
        val id = trackNumber.id as IntId
        val referenceLineGeometry =
            trackNumber.referenceLineId
                ?.let { rlId -> referenceLineDao.fetchOfficialVersionAtMoment(branch, rlId, moment) }
                ?.let(referenceLineService::getWithAlignment)
                ?.second
        val geocodingContext = geocodingService.getGeocodingContextAtMoment(branch, id, moment)
        return TrackNumberData(oid, trackNumber, referenceLineGeometry, geocodingContext)
    }

    private fun getTrackNumberData(
        branch: LayoutBranch,
        moment: Instant,
        trackNumbers: List<LayoutTrackNumber>,
    ): List<TrackNumberData> {
        val getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment)
        val extIds = trackNumberDao.fetchExternalIds(branch, trackNumbers.map { it.id as IntId })
        val referenceLines =
            referenceLineDao
                .fetchManyOfficialVersionsAtMoment(branch, trackNumbers.mapNotNull { it.referenceLineId }, moment)
                .let { versions -> referenceLineService.getManyWithAlignments(versions) }
                .associate { it.first.id as IntId to it.second }
        return trackNumbers.map { trackNumber ->
            val id = trackNumber.id as IntId
            val oid = extIds[id]?.oid ?: throwOidNotFound(branch, id)
            val referenceLineGeometry = trackNumber.referenceLineId?.let(referenceLines::get)
            TrackNumberData(oid, trackNumber, referenceLineGeometry, getGeocodingContext(id))
        }
    }
}
