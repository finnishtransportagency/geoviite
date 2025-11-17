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
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTrackNumberServiceV1
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val publicationDao: PublicationDao,
    private val publicationService: PublicationService,
    private val geocodingService: GeocodingService,
    private val referenceLineDao: ReferenceLineDao,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtTrackNumberCollection(
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtTrackNumberCollectionResponseV1 {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        return createTrackNumberCollectionResponse(publication, coordinateSystem = coordinateSystem ?: LAYOUT_SRID)
    }

    fun getExtTrackNumberCollectionModifications(
        trackLayoutVersionFrom: Uuid<Publication>,
        trackLayoutVersionTo: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtModifiedTrackNumberCollectionResponseV1? {
        val publications = publicationService.getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo)
        return if (publications.areDifferent()) {
            createTrackNumberCollectionModificationResponse(publications, coordinateSystem ?: LAYOUT_SRID)
        } else {
            publicationsAreTheSame(trackLayoutVersionFrom)
        }
    }

    fun getExtTrackNumber(
        oid: Oid<LayoutTrackNumber>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtTrackNumberResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        return createTrackNumberResponse(oid, idLookup(oid), publication, coordinateSystem ?: LAYOUT_SRID)
    }

    fun getExtTrackNumberModifications(
        oid: Oid<LayoutTrackNumber>,
        trackLayoutVersionFrom: Uuid<Publication>,
        trackLayoutVersionTo: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtModifiedTrackNumberResponseV1? {
        val publications = publicationService.getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo)
        val id = idLookup(oid) // Lookup before change check to produce consistent error if oid is not found
        return if (publications.areDifferent()) {
            createTrackNumberModificationResponse(oid, id, publications, coordinateSystem ?: LAYOUT_SRID)
        } else {
            publicationsAreTheSame(trackLayoutVersionFrom)
        }
    }

    private fun idLookup(oid: Oid<LayoutTrackNumber>): IntId<LayoutTrackNumber> =
        layoutTrackNumberDao.lookupByExternalId(oid)?.id
            ?: throw ExtOidNotFoundExceptionV1("track number lookup failed for oid=$oid")

    private fun createTrackNumberResponse(
        oid: Oid<LayoutTrackNumber>,
        id: IntId<LayoutTrackNumber>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtTrackNumberResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        return layoutTrackNumberDao.getOfficialAtMoment(branch, id, moment)?.let { trackNumber ->
            val data = getTrackNumberData(branch, moment, oid, trackNumber)
            ExtTrackNumberResponseV1(
                trackLayoutVersion = publication.uuid,
                coordinateSystem = coordinateSystem,
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
            ?.let(layoutTrackNumberDao::fetch)
            ?.let { trackNumber ->
                val data = getTrackNumberData(branch, endMoment, oid, trackNumber)
                ExtModifiedTrackNumberResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
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
        val trackNumbers = layoutTrackNumberDao.listOfficialAtMoment(branch, moment).filter { it.exists }
        val trackNumberData = getTrackNumberData(branch, moment, trackNumbers)
        return ExtTrackNumberCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = coordinateSystem,
            trackNumberCollection =
                trackNumberData.parallelStream().map { data -> createExtTrackNumber(data, coordinateSystem) }.toList(),
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
            ?.let(layoutTrackNumberDao::fetchMany)
            ?.let { modifiedTrackNumbers ->
                val trackNumberData = getTrackNumberData(branch, endMoment, modifiedTrackNumbers)
                ExtModifiedTrackNumberCollectionResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumberCollection =
                        trackNumberData
                            .parallelStream()
                            .map { data -> createExtTrackNumber(data, coordinateSystem) }
                            .toList(),
                )
            } ?: layoutAssetCollectionWasUnmodified<LayoutTrackNumber>(publications)
    }

    private fun createExtTrackNumber(data: TrackNumberData, coordinateSystem: Srid): ExtTrackNumberV1 {
        return ExtTrackNumberV1(
            trackNumberOid = data.oid,
            trackNumber = data.trackNumber.number,
            trackNumberDescription = data.trackNumber.description,
            trackNumberState = data.trackNumber.state.let(ExtTrackNumberStateV1::of),
            startLocation = data.geometry?.start?.let { p -> getEndPoint(p, data.geocodingContext, coordinateSystem) },
            endLocation = data.geometry?.end?.let { p -> getEndPoint(p, data.geocodingContext, coordinateSystem) },
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
        val trackNumberIds = trackNumbers.map { it.id as IntId }
        val externalTrackNumberIds = layoutTrackNumberDao.fetchExternalIds(branch, trackNumberIds)
        val referenceLineIds = trackNumbers.mapNotNull { it.referenceLineId }
        val referenceLines =
            referenceLineDao
                .fetchManyOfficialVersionsAtMoment(branch, referenceLineIds, moment)
                .let { versions -> referenceLineService.getManyWithAlignments(versions) }
                .associate { it.first.id as IntId to it.second }
        return trackNumbers.map { trackNumber ->
            val id = trackNumber.id as IntId
            val oid =
                requireNotNull(externalTrackNumberIds[id]?.oid) {
                    "track number oid not found, layoutTrackNumberId=$id"
                }
            val referenceLineGeometry = trackNumber.referenceLineId?.let(referenceLines::get)
            TrackNumberData(oid, trackNumber, referenceLineGeometry, getGeocodingContext(id))
        }
    }
}
